package com.shoefactory.assistant.service.impl;

import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.config.FileStorageProperties;
import com.shoefactory.assistant.service.ExcelPdfService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class ExcelPdfServiceImpl implements ExcelPdfService {

    private final FileStorageProperties properties;

    public ExcelPdfServiceImpl(FileStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public Path convertSheetToPdf(Path sourceExcel, Path targetPdf, String sheetName) {
        Path tempDir = null;
        try {
            Files.createDirectories(targetPdf.getParent());
            tempDir = Files.createTempDirectory("shoe-excel-pdf-");
            Path conversionInput = prepareSingleSheetExcel(sourceExcel, tempDir, sheetName);
            Path generatedPdf = runConverter(conversionInput, tempDir);
            Files.copy(generatedPdf, targetPdf, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return targetPdf;
        } catch (IOException ex) {
            throw new BusinessException("Excel 转 PDF 失败", ex);
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    private Path prepareSingleSheetExcel(Path sourceExcel, Path tempDir, String sheetName) throws IOException {
        String extension = extensionOf(sourceExcel.getFileName().toString());
        Path prepared = tempDir.resolve("prepared." + extension);
        try (Workbook workbook = WorkbookFactory.create(sourceExcel.toFile())) {
            int targetSheetIndex = findSheetIndex(workbook, sheetName)
                    .orElseThrow(() -> new BusinessException("Excel 中未找到 sheet: " + sheetName));

            for (int i = workbook.getNumberOfSheets() - 1; i >= 0; i--) {
                if (i != targetSheetIndex) {
                    workbook.removeSheetAt(i);
                    if (i < targetSheetIndex) {
                        targetSheetIndex--;
                    }
                }
            }

            Sheet sheet = workbook.getSheetAt(0);
            configurePrintSettings(workbook, sheet);
            workbook.setActiveSheet(0);
            workbook.setSelectedTab(0);
            try (java.io.OutputStream outputStream = Files.newOutputStream(prepared)) {
                workbook.write(outputStream);
            }
            return prepared;
        }
    }

    private Optional<Integer> findSheetIndex(Workbook workbook, String sheetName) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String current = workbook.getSheetName(i);
            if (current.equals(sheetName)) {
                return Optional.of(i);
            }
        }
        String normalizedExpected = normalizeSheetName(sheetName);
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String current = workbook.getSheetName(i);
            String normalizedCurrent = normalizeSheetName(current);
            if (normalizedCurrent.contains(normalizedExpected) || normalizedExpected.contains(normalizedCurrent)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private String normalizeSheetName(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace(" ", "")
                .replace("\u00A0", "")
                .replace("\u3000", "")
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", "")
                .trim();
    }

    private void configurePrintSettings(Workbook workbook, Sheet sheet) {
        PrintSetup setup = sheet.getPrintSetup();
        setup.setPaperSize(PrintSetup.A4_PAPERSIZE);
        setup.setLandscape(true);
        setup.setFitWidth((short) 1);
        setup.setFitHeight((short) 0);

        sheet.setFitToPage(true);
        sheet.setAutobreaks(true);
        sheet.setMargin(Sheet.TopMargin, 0.25);
        sheet.setMargin(Sheet.BottomMargin, 0.25);
        sheet.setMargin(Sheet.LeftMargin, 0.2);
        sheet.setMargin(Sheet.RightMargin, 0.2);
        sheet.setHorizontallyCenter(true);

        PrintArea printArea = detectPrintArea(sheet);
        if (printArea != null) {
            workbook.setPrintArea(0, printArea.firstCol(), printArea.lastCol(), printArea.firstRow(), printArea.lastRow());
        }
    }

    private PrintArea detectPrintArea(Sheet sheet) {
        int firstRow = Integer.MAX_VALUE;
        int lastRow = -1;
        int firstCol = Integer.MAX_VALUE;
        int lastCol = -1;

        for (Row row : sheet) {
            boolean rowUsed = false;
            short firstCellNum = row.getFirstCellNum();
            short lastCellNum = row.getLastCellNum();
            if (firstCellNum < 0 || lastCellNum < 0) {
                continue;
            }
            for (int colIndex = firstCellNum; colIndex < lastCellNum; colIndex++) {
                Cell cell = row.getCell(colIndex);
                if (isCellUsed(cell)) {
                    rowUsed = true;
                    firstCol = Math.min(firstCol, colIndex);
                    lastCol = Math.max(lastCol, colIndex);
                }
            }
            if (rowUsed) {
                firstRow = Math.min(firstRow, row.getRowNum());
                lastRow = Math.max(lastRow, row.getRowNum());
            }
        }

        if (lastRow < 0 || lastCol < 0) {
            return null;
        }
        return new PrintArea(firstRow, lastRow, firstCol, lastCol);
    }

    private boolean isCellUsed(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return false;
        }
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue() != null && !cell.getStringCellValue().trim().isEmpty();
        }
        return true;
    }

    private Path runConverter(Path input, Path outputDir) {
        try {
            return runLibreOffice(input, outputDir);
        } catch (BusinessException ex) {
            return runExcelExport(input, outputDir, ex);
        }
    }

    private Path runLibreOffice(Path input, Path outputDir) {
        String command = stripSurroundingQuotes(properties.getLibreOfficeCommand());
        ProcessBuilder builder = new ProcessBuilder(
                command,
                "--headless",
                "--convert-to",
                "pdf",
                "--outdir",
                outputDir.toString(),
                input.toString()
        );
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readProcessOutput(process.getInputStream()));
            boolean finished = process.waitFor(properties.getLibreOfficeTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException("LibreOffice 转换超时");
            }
            String output = outputFuture.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                throw new BusinessException("LibreOffice 转换失败: " + output);
            }
            return findGeneratedPdf(outputDir).orElseThrow(() -> new BusinessException("LibreOffice 未生成 PDF 文件: " + output));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("执行 LibreOffice 命令失败: " + command, ex);
        }
    }

    private Path runExcelExport(Path input, Path outputDir, BusinessException libreOfficeError) {
        Path outputPdf = outputDir.resolve(stripExtension(input.getFileName().toString()) + ".pdf");
        String script = """
                $ErrorActionPreference = 'Stop';
                $excel = New-Object -ComObject Excel.Application;
                $excel.Visible = $false;
                $excel.DisplayAlerts = $false;
                try {
                  $workbook = $excel.Workbooks.Open('%s');
                  $workbook.ExportAsFixedFormat(0, '%s');
                  $workbook.Close($false);
                } finally {
                  $excel.Quit();
                  [System.Runtime.InteropServices.Marshal]::ReleaseComObject($excel) | Out-Null;
                }
                """.formatted(escapePowerShell(input), escapePowerShell(outputPdf));
        ProcessBuilder builder = new ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                script
        );
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readProcessOutput(process.getInputStream()));
            boolean finished = process.waitFor(properties.getLibreOfficeTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException("Excel 导出 PDF 超时");
            }
            String output = outputFuture.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                throw new BusinessException("Excel 导出 PDF 失败: " + output);
            }
            if (!Files.exists(outputPdf)) {
                throw new BusinessException("Excel 未生成 PDF 文件: " + output);
            }
            return outputPdf;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("执行 LibreOffice 失败，且 Excel 导出 PDF 也失败。LibreOffice 错误: "
                    + libreOfficeError.getMessage(), ex);
        }
    }

    private Optional<Path> findGeneratedPdf(Path outputDir) throws IOException {
        try (Stream<Path> paths = Files.list(outputDir)) {
            return paths
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .findFirst();
        }
    }

    private String readProcessOutput(InputStream inputStream) {
        try {
            return new String(inputStream.readAllBytes(), Charset.defaultCharset());
        } catch (IOException ex) {
            return "";
        }
    }

    private String extensionOf(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            throw new BusinessException("Excel 文件缺少扩展名");
        }
        return filename.substring(index + 1);
    }

    private String stripExtension(String filename) {
        int index = filename.lastIndexOf('.');
        return index < 0 ? filename : filename.substring(0, index);
    }

    private String escapePowerShell(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("'", "''");
    }

    private String stripSurroundingQuotes(String value) {
        if (value == null || value.isBlank()) {
            return "soffice";
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temporary conversion files can be cleaned up by the OS if deletion fails.
                }
            });
        } catch (IOException ignored) {
            // Ignore cleanup failures.
        }
    }

    private record PrintArea(int firstRow, int lastRow, int firstCol, int lastCol) {
    }
}
