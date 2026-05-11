package com.shoefactory.assistant.service.impl;

import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.service.OrderRecognitionResult;
import com.shoefactory.assistant.service.OrderRecognitionService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class OrderRecognitionServiceImpl implements OrderRecognitionService {

    private static final String ORDER_SHEET_NAME = "订单";
    private static final List<String> ORDER_NO_LABELS = List.of("订单号", "订单编号", "客户单号", "单号");
    private static final List<String> CUSTOMER_LABELS = List.of("客户", "客户名称", "客户名");
    private static final List<String> STYLE_LABELS = List.of("款号", "货号", "型号", "鞋款");
    private static final List<String> COLOR_LABELS = List.of("颜色", "色号", "配色");
    private static final List<String> QUANTITY_LABELS = List.of("数量", "总数量", "订单数量", "双数");
    private static final List<String> CARTON_LABELS = List.of("箱数", "总箱数", "件数");
    private static final List<String> DELIVERY_LABELS = List.of("交期", "交货日期", "出货日期", "交付日期");

    private final DataFormatter formatter = new DataFormatter(Locale.CHINA);

    @Override
    public OrderRecognitionResult recognizeExcelOrder(Path sourceExcel) {
        try (Workbook workbook = WorkbookFactory.create(sourceExcel.toFile())) {
            Sheet sheet = findSheet(workbook, ORDER_SHEET_NAME)
                    .orElseThrow(() -> new BusinessException("Excel 中未找到“订单”sheet"));
            OrderRecognitionResult result = new OrderRecognitionResult();
            result.setSourceSheetName(sheet.getSheetName());
            result.setOrderNo(findValue(sheet, ORDER_NO_LABELS).orElse(null));
            result.setCustomerName(findValue(sheet, CUSTOMER_LABELS).orElse(null));
            result.setStyleNo(findValue(sheet, STYLE_LABELS).orElse(null));
            result.setColor(findValue(sheet, COLOR_LABELS).orElse(null));
            result.setQuantity(findValue(sheet, QUANTITY_LABELS).flatMap(this::parseInteger).orElse(null));
            result.setCartonCount(findValue(sheet, CARTON_LABELS).flatMap(this::parseInteger).orElse(null));
            result.setDeliveryDate(findDateValue(sheet, DELIVERY_LABELS).orElse(null));
            if (!result.hasCoreFields()) {
                result.setErrorMessage("未识别到订单号、客户或款号等核心字段");
            }
            return result;
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException("读取 Excel 订单失败", ex);
        }
    }

    private Optional<Sheet> findSheet(Workbook workbook, String expectedName) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (sheet.getSheetName().equals(expectedName)) {
                return Optional.of(sheet);
            }
        }
        String normalizedExpected = normalize(expectedName);
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (normalize(sheet.getSheetName()).contains(normalizedExpected)) {
                return Optional.of(sheet);
            }
        }
        return Optional.empty();
    }

    private Optional<String> findValue(Sheet sheet, List<String> labels) {
        for (Row row : sheet) {
            short firstCell = row.getFirstCellNum();
            short lastCell = row.getLastCellNum();
            if (firstCell < 0 || lastCell < 0) {
                continue;
            }
            for (int col = firstCell; col < lastCell; col++) {
                Cell cell = row.getCell(col);
                String text = text(cell);
                if (text.isBlank()) {
                    continue;
                }
                for (String label : labels) {
                    Optional<String> inline = extractInlineValue(text, label);
                    if (inline.isPresent()) {
                        return inline;
                    }
                    if (isLabel(text, label)) {
                        Optional<String> neighbor = findNeighborValue(sheet, row.getRowNum(), col);
                        if (neighbor.isPresent()) {
                            return neighbor;
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<LocalDate> findDateValue(Sheet sheet, List<String> labels) {
        for (Row row : sheet) {
            short firstCell = row.getFirstCellNum();
            short lastCell = row.getLastCellNum();
            if (firstCell < 0 || lastCell < 0) {
                continue;
            }
            for (int col = firstCell; col < lastCell; col++) {
                Cell cell = row.getCell(col);
                String text = text(cell);
                if (text.isBlank()) {
                    continue;
                }
                for (String label : labels) {
                    Optional<String> inline = extractInlineValue(text, label);
                    if (inline.isPresent()) {
                        return parseDate(inline.get());
                    }
                    if (isLabel(text, label)) {
                        Cell right = row.getCell(col + 1);
                        Optional<LocalDate> rightDate = parseDateCell(right);
                        if (rightDate.isPresent()) {
                            return rightDate;
                        }
                        Row belowRow = sheet.getRow(row.getRowNum() + 1);
                        Optional<LocalDate> belowDate = parseDateCell(belowRow == null ? null : belowRow.getCell(col));
                        if (belowDate.isPresent()) {
                            return belowDate;
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> findNeighborValue(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row != null) {
            for (int offset = 1; offset <= 3; offset++) {
                String value = text(row.getCell(colIndex + offset));
                if (!value.isBlank()) {
                    return Optional.of(value);
                }
            }
        }
        for (int offset = 1; offset <= 2; offset++) {
            Row below = sheet.getRow(rowIndex + offset);
            if (below != null) {
                String value = text(below.getCell(colIndex));
                if (!value.isBlank()) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> extractInlineValue(String text, String label) {
        String normalizedText = normalize(text);
        String normalizedLabel = normalize(label);
        if (!normalizedText.startsWith(normalizedLabel)) {
            return Optional.empty();
        }
        String value = text.substring(Math.min(text.length(), label.length())).trim();
        value = value.replaceFirst("^[：:：\\-\\s]+", "").trim();
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private boolean isLabel(String text, String label) {
        String normalizedText = normalize(text);
        String normalizedLabel = normalize(label);
        return normalizedText.equals(normalizedLabel)
                || normalizedText.equals(normalizedLabel + ":")
                || normalizedText.equals(normalizedLabel + "：");
    }

    private String text(Cell cell) {
        if (cell == null) {
            return "";
        }
        String value = formatter.formatCellValue(cell);
        return value == null ? "" : value.trim();
    }

    private Optional<LocalDate> parseDateCell(Cell cell) {
        if (cell == null) {
            return Optional.empty();
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return Optional.of(cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        }
        return parseDate(text(cell));
    }

    private Optional<Integer> parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.replace(",", "").replace("，", "").replaceAll("[^0-9.\\-]", "");
        if (normalized.isBlank() || "-".equals(normalized)) {
            return Optional.empty();
        }
        try {
            return Optional.of(BigDecimal.valueOf(Double.parseDouble(normalized)).intValue());
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private Optional<LocalDate> parseDate(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim()
                .replace("年", "-")
                .replace("月", "-")
                .replace("日", "")
                .replace("/", "-")
                .replace(".", "-");
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("yyyy-M-d"),
                DateTimeFormatter.ofPattern("yyyy-MM-d"),
                DateTimeFormatter.ofPattern("yyyy-M-dd"),
                DateTimeFormatter.ofPattern("M-d-yyyy"),
                DateTimeFormatter.ofPattern("M-d-yy")
        );
        for (DateTimeFormatter dateFormatter : formatters) {
            try {
                return Optional.of(LocalDate.parse(normalized, dateFormatter));
            } catch (DateTimeParseException ignored) {
                // Try the next supported date format.
            }
        }
        return Optional.empty();
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace(" ", "").replace("\n", "").replace("\t", "").trim();
    }
}
