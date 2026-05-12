package com.shoefactory.assistant.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.entity.OrderLine;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.SourceFile;
import com.shoefactory.assistant.enums.OrderRecognitionStatus;
import com.shoefactory.assistant.service.OrderExcelImportService;
import com.shoefactory.assistant.service.OrderImportResult;
import com.shoefactory.assistant.util.FileStorageUtil;
import org.apache.poi.ooxml.POIXMLDocumentPart;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFPictureData;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OrderExcelImportServiceImpl implements OrderExcelImportService {

    private static final String ORDER_SHEET_NAME = "订单";
    private static final int HEADER_ROW_INDEX = 4;
    private static final int FIRST_DATA_ROW_INDEX = 5;
    private static final int COL_IMAGE = 5;
    private static final int COL_LAST_NO = 6;
    private static final int COL_DEVELOPMENT_NO = 7;
    private static final int COL_CUSTOMER = 8;
    private static final int COL_CUSTOMER_ORDER_NO = 9;
    private static final int COL_DELIVERY_DATE = 10;
    private static final int COL_PO = 11;
    private static final int COL_CUSTOMER_STYLE_NO = 12;
    private static final int COL_ENGLISH_COLOR = 13;
    private static final int COL_ENGLISH_MATERIAL = 14;
    private static final int COL_UPPER_MATERIAL = 15;
    private static final int COL_LINING_MATERIAL = 16;
    private static final int COL_ACCESSORY = 17;
    private static final int COL_INSOLE_PLATFORM = 18;
    private static final int COL_OUTSOLE = 19;
    private static final int COL_TRADEMARK = 20;
    private static final int COL_SIZE_START = 21;
    private static final int COL_SIZE_END = 37;
    private static final int COL_QUANTITY = 38;
    private static final int COL_CARTON_COUNT = 39;
    private static final int COL_TOTAL_QUANTITY = 40;

    private final DataFormatter formatter = new DataFormatter(Locale.CHINA);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FileStorageUtil fileStorageUtil;

    public OrderExcelImportServiceImpl(FileStorageUtil fileStorageUtil) {
        this.fileStorageUtil = fileStorageUtil;
    }

    @Override
    public OrderImportResult importOrder(Path sourceExcel, SourceFile sourceFile) {
        try (Workbook workbook = WorkbookFactory.create(sourceExcel.toFile())) {
            Sheet sheet = workbook.getSheet(ORDER_SHEET_NAME);
            if (sheet == null) {
                throw new BusinessException("Excel 中未找到“订单”sheet");
            }
            Map<Integer, PictureInfo> picturesByRow = extractPicturesByRow(sheet);
            OrderRecord order = buildOrderRecord(sheet, sourceFile);
            List<OrderLine> lines = buildLines(sheet, sourceFile, order, picturesByRow);
            if (lines.isEmpty()) {
                throw new BusinessException("订单 sheet 未解析到明细行");
            }
            return new OrderImportResult(order, lines);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException("读取 Excel 订单失败", ex);
        }
    }

    private OrderRecord buildOrderRecord(Sheet sheet, SourceFile sourceFile) {
        LocalDateTime now = LocalDateTime.now();
        OrderRecord order = new OrderRecord();
        order.setSourceFileId(sourceFile.getId());
        order.setOrderNo(blankToNull(text(sheet, 1, 32)));
        order.setCustomerName(blankToNull(text(sheet, 1, 6)));
        order.setDeliveryDate(dateValue(sheet.getRow(3) == null ? null : sheet.getRow(3).getCell(15)));
        order.setSourceSheetName(sheet.getSheetName());
        order.setRecognitionStatus(OrderRecognitionStatus.RECOGNIZED.name());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setQuantity(integerValue(sheet, 13, COL_TOTAL_QUANTITY));
        order.setCartonCount(integerValue(sheet, 13, COL_CARTON_COUNT));
        return order;
    }

    private List<OrderLine> buildLines(
            Sheet sheet,
            SourceFile sourceFile,
            OrderRecord order,
            Map<Integer, PictureInfo> picturesByRow
    ) {
        List<OrderLine> lines = new ArrayList<>();
        Row header = sheet.getRow(HEADER_ROW_INDEX);
        LocalDateTime now = LocalDateTime.now();
        for (int rowIndex = FIRST_DATA_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isTotalRow(row)) {
                break;
            }
            if (isBlankRow(row)) {
                continue;
            }
            OrderLine line = new OrderLine();
            line.setSourceFileId(sourceFile.getId());
            line.setOrderNo(order.getOrderNo());
            line.setInvoiceNo(blankToNull(text(sheet, 3, 6)));
            line.setCustomerName(blankToNull(text(row, COL_CUSTOMER)));
            line.setOrderDate(dateValue(sheet.getRow(1) == null ? null : sheet.getRow(1).getCell(15)));
            line.setDeliveryDate(dateValue(row.getCell(COL_DELIVERY_DATE)));
            line.setLastNo(blankToNull(text(row, COL_LAST_NO)));
            line.setStyleNo(blankToNull(text(row, COL_LAST_NO)));
            line.setDevelopmentNo(blankToNull(text(row, COL_DEVELOPMENT_NO)));
            line.setCustomerOrderNo(blankToNull(text(row, COL_CUSTOMER_ORDER_NO)));
            line.setPoNo(blankToNull(text(row, COL_PO)));
            line.setCustomerStyleNo(blankToNull(text(row, COL_CUSTOMER_STYLE_NO)));
            line.setEnglishColor(blankToNull(text(row, COL_ENGLISH_COLOR)));
            line.setEnglishMaterial(blankToNull(text(row, COL_ENGLISH_MATERIAL)));
            line.setUpperMaterial(blankToNull(text(row, COL_UPPER_MATERIAL)));
            line.setLiningMaterial(blankToNull(text(row, COL_LINING_MATERIAL)));
            line.setAccessory(blankToNull(text(row, COL_ACCESSORY)));
            line.setInsolePlatform(blankToNull(text(row, COL_INSOLE_PLATFORM)));
            line.setOutsole(blankToNull(text(row, COL_OUTSOLE)));
            line.setTrademark(blankToNull(text(row, COL_TRADEMARK)));
            line.setQuantity(integerValue(row, COL_QUANTITY));
            line.setCartonCount(integerValue(row, COL_CARTON_COUNT));
            line.setTotalQuantity(integerValue(row, COL_TOTAL_QUANTITY));
            line.setSizeQuantitiesJson(toJson(readSizeQuantities(header, row)));
            line.setImportStatus("IMPORTED");
            line.setSourceSheetName(sheet.getSheetName());
            line.setRowIndex(rowIndex + 1);
            line.setCreatedAt(now);
            line.setUpdatedAt(now);

            PictureInfo picture = picturesByRow.get(rowIndex + 1);
            if (picture != null) {
                Path imagePath = fileStorageUtil.saveOrderLineImage(
                        picture.bytes(),
                        sourceFile.getFileNo(),
                        order.getOrderNo(),
                        rowIndex + 1,
                        picture.extension()
                );
                line.setImagePath(imagePath == null ? null : imagePath.toString());
            }
            lines.add(line);
        }
        return lines;
    }

    private Map<String, Integer> readSizeQuantities(Row header, Row row) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (header == null) {
            return result;
        }
        for (int col = COL_SIZE_START; col <= COL_SIZE_END; col++) {
            String size = text(header, col);
            Integer quantity = integerValue(row, col);
            if (size == null || size.isBlank() || quantity == null || quantity <= 0) {
                continue;
            }
            result.merge(size, quantity, Integer::sum);
        }
        return result;
    }

    private Map<Integer, PictureInfo> extractPicturesByRow(Sheet sheet) {
        Map<Integer, PictureInfo> result = new LinkedHashMap<>();
        if (!(sheet instanceof XSSFSheet xssfSheet)) {
            return result;
        }
        for (POIXMLDocumentPart relation : xssfSheet.getRelations()) {
            if (!(relation instanceof XSSFDrawing drawing)) {
                continue;
            }
            for (XSSFPicture picture : drawing.getShapes().stream()
                    .filter(XSSFPicture.class::isInstance)
                    .map(XSSFPicture.class::cast)
                    .toList()) {
                int row = picture.getPreferredSize().getFrom().getRow() + 1;
                int col = picture.getPreferredSize().getFrom().getCol();
                if (col != COL_IMAGE || row < FIRST_DATA_ROW_INDEX + 1) {
                    continue;
                }
                XSSFPictureData pictureData = picture.getPictureData();
                String extension = pictureData.suggestFileExtension();
                result.putIfAbsent(row, new PictureInfo(pictureData.getData(), extension));
            }
        }
        return result;
    }

    private boolean isTotalRow(Row row) {
        String styleNo = text(row, COL_LAST_NO);
        String developmentNo = text(row, COL_DEVELOPMENT_NO);
        String totalLabel = text(row, COL_TRADEMARK);
        return (styleNo.isBlank() && developmentNo.isBlank() && integerValue(row, COL_TOTAL_QUANTITY) != null)
                || totalLabel.contains("合计");
    }

    private boolean isBlankRow(Row row) {
        for (int col = COL_LAST_NO; col <= COL_TOTAL_QUANTITY; col++) {
            if (!text(row, col).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String text(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        return row == null ? "" : text(row, colIndex);
    }

    private String text(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return "";
        }
        String value = formatter.formatCellValue(cell);
        return value == null ? "" : value.trim();
    }

    private Integer integerValue(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        return row == null ? null : integerValue(row, colIndex);
    }

    private Integer integerValue(Row row, int colIndex) {
        String value = text(row, colIndex);
        if (value.isBlank()) {
            return null;
        }
        String normalized = value.replace(",", "").replaceAll("[^0-9.\\-]", "");
        if (normalized.isBlank() || "-".equals(normalized)) {
            return null;
        }
        try {
            return BigDecimal.valueOf(Double.parseDouble(normalized)).intValue();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDate dateValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double numericValue = cell.getNumericCellValue();
            if (DateUtil.isCellDateFormatted(cell) || DateUtil.isValidExcelDate(numericValue)) {
                return DateUtil.getJavaDate(numericValue).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
        }
        String value = formatter.formatCellValue(cell);
        if (value == null || value.isBlank()) {
            return null;
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
                DateTimeFormatter.ofPattern("M-d-yy"),
                DateTimeFormatter.ofPattern("M-d-yyyy")
        );
        for (DateTimeFormatter dateFormatter : formatters) {
            try {
                return LocalDate.parse(normalized, dateFormatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported date format.
            }
        }
        try {
            double serial = Double.parseDouble(normalized);
            if (DateUtil.isValidExcelDate(serial)) {
                return DateUtil.getJavaDate(serial).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
        } catch (NumberFormatException ignored) {
            // Not an Excel serial date.
        }
        return null;
    }

    private String toJson(Map<String, Integer> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("尺码数量序列化失败", ex);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record PictureInfo(byte[] bytes, String extension) {
    }
}
