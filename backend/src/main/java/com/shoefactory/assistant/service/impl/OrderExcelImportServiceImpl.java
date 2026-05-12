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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OrderExcelImportServiceImpl implements OrderExcelImportService {

    private static final String ORDER_SHEET_NAME = "订单";
    private static final int FALLBACK_HEADER_ROW_INDEX = 4;
    private static final int MAX_HEADER_SCAN_ROWS = 30;
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
    private static final int COL_QUANTITY = 38;
    private static final int COL_CARTON_COUNT = 39;
    private static final int COL_TOTAL_QUANTITY = 40;
    private static final Pattern LEADING_ORDER_NO_PATTERN = Pattern.compile("^(\\d{4,})");

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
            Row header = findHeaderRow(sheet);
            if (header == null) {
                throw new BusinessException("订单 sheet 未找到明细表头行");
            }
            TableColumns columns = TableColumns.from(header);
            int firstDataRowIndex = header.getRowNum() + 1;
            Map<Integer, PictureInfo> picturesByRow = extractPicturesByRow(sheet, columns.image(), firstDataRowIndex);
            OrderRecord order = buildOrderRecord(sheet, sourceFile, columns);
            List<OrderLine> lines = buildLines(sheet, sourceFile, order, picturesByRow, columns, firstDataRowIndex);
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

    private OrderRecord buildOrderRecord(Sheet sheet, SourceFile sourceFile, TableColumns columns) {
        LocalDateTime now = LocalDateTime.now();
        OrderRecord order = new OrderRecord();
        order.setSourceFileId(sourceFile.getId());
        order.setOrderNo(blankToNull(resolveOrderNo(sheet, sourceFile)));
        order.setCustomerName(blankToNull(text(sheet, 1, 6)));
        order.setDeliveryDate(dateValue(sheet.getRow(3) == null ? null : sheet.getRow(3).getCell(15)));
        order.setSourceSheetName(sheet.getSheetName());
        order.setRecognitionStatus(OrderRecognitionStatus.RECOGNIZED.name());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        Row totalRow = findTotalRow(sheet, columns);
        order.setQuantity(totalRow == null ? null : integerValue(totalRow, columns.totalQuantity()));
        order.setCartonCount(totalRow == null ? null : integerValue(totalRow, columns.cartonCount()));
        return order;
    }

    private Row findHeaderRow(Sheet sheet) {
        Row fallback = sheet.getRow(FALLBACK_HEADER_ROW_INDEX);
        Row bestRow = null;
        int bestScore = 0;
        int lastScanRow = Math.min(sheet.getLastRowNum(), MAX_HEADER_SCAN_ROWS - 1);
        for (int rowIndex = 0; rowIndex <= lastScanRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            int score = headerScore(row);
            if (score > bestScore) {
                bestScore = score;
                bestRow = row;
            }
        }
        return bestScore >= 4 ? bestRow : fallback;
    }

    private int headerScore(Row row) {
        if (row == null) {
            return 0;
        }
        int score = 0;
        score += hasHeader(row, "图片") ? 2 : 0;
        score += hasHeader(row, "楦头", "楦头号") ? 2 : 0;
        score += hasHeader(row, "开发编号", "开发号") ? 2 : 0;
        score += hasHeader(row, "客人", "客户") ? 1 : 0;
        score += hasHeader(row, "客人订单号", "客户订单号") ? 1 : 0;
        score += hasHeader(row, "PO", "PONO", "PO号", "PO号码") ? 1 : 0;
        score += hasHeader(row, "大底") ? 1 : 0;
        score += hasHeader(row, "双数", "数量") ? 1 : 0;
        return score;
    }

    private boolean hasHeader(Row row, String... aliases) {
        if (row == null) {
            return false;
        }
        int first = Math.max(0, row.getFirstCellNum());
        int last = Math.max(first, row.getLastCellNum() - 1);
        for (int col = first; col <= last; col++) {
            String value = normalizeHeader(text(row, col));
            for (String alias : aliases) {
                if (value.equals(normalizeHeader(alias))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String resolveOrderNo(Sheet sheet, SourceFile sourceFile) {
        String labeledOrderNo = findValueAfterLabel(sheet, "订单流水号", 0, 8);
        if (!labeledOrderNo.isBlank()) {
            return labeledOrderNo;
        }

        String fixedOrderNo = text(sheet, 1, 32);
        if (!fixedOrderNo.isBlank()) {
            return fixedOrderNo;
        }

        String invoiceNo = findValueAfterLabel(sheet, "发票编号", 0, 8);
        if (!invoiceNo.isBlank()) {
            return invoiceNo;
        }

        String fixedInvoiceNo = text(sheet, 3, 6);
        if (!fixedInvoiceNo.isBlank()) {
            return fixedInvoiceNo;
        }

        return leadingOrderNoFromFileName(sourceFile.getOriginalName());
    }

    private String findValueAfterLabel(Sheet sheet, String label, int startRow, int endRow) {
        String normalizedLabel = normalizeHeader(label);
        for (int rowIndex = startRow; rowIndex <= Math.min(endRow, sheet.getLastRowNum()); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            int first = Math.max(0, row.getFirstCellNum());
            int last = Math.max(first, row.getLastCellNum() - 1);
            for (int col = first; col <= last; col++) {
                String cellText = normalizeHeader(text(row, col)).replace(":", "");
                if (!cellText.contains(normalizedLabel)) {
                    continue;
                }
                for (int offset = 1; offset <= 6 && col + offset <= last; offset++) {
                    String value = text(row, col + offset);
                    if (!value.isBlank()) {
                        return value;
                    }
                }
            }
        }
        return "";
    }

    private String leadingOrderNoFromFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        Matcher matcher = LEADING_ORDER_NO_PATTERN.matcher(fileName.trim());
        return matcher.find() ? matcher.group(1) : "";
    }

    private List<OrderLine> buildLines(
            Sheet sheet,
            SourceFile sourceFile,
            OrderRecord order,
            Map<Integer, PictureInfo> picturesByRow,
            TableColumns columns,
            int firstDataRowIndex
    ) {
        List<OrderLine> lines = new ArrayList<>();
        Row header = sheet.getRow(columns.headerRowIndex());
        LocalDateTime now = LocalDateTime.now();
        for (int rowIndex = firstDataRowIndex; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isTotalRow(row, columns)) {
                break;
            }
            if (isBlankRow(row, columns) || isRepeatedHeaderRow(row, columns)) {
                continue;
            }
            OrderLine line = new OrderLine();
            line.setSourceFileId(sourceFile.getId());
            line.setOrderNo(order.getOrderNo());
            line.setInvoiceNo(blankToNull(text(sheet, 3, 6)));
            line.setCustomerName(blankToNull(text(row, columns.customer())));
            line.setOrderDate(dateValue(cell(sheet.getRow(1), 15)));
            line.setDeliveryDate(dateValue(cell(row, columns.deliveryDate())));
            line.setLastNo(blankToNull(text(row, columns.lastNo())));
            line.setStyleNo(blankToNull(text(row, columns.lastNo())));
            line.setDevelopmentNo(blankToNull(text(row, columns.developmentNo())));
            line.setCustomerOrderNo(blankToNull(text(row, columns.customerOrderNo())));
            line.setPoNo(blankToNull(text(row, columns.po())));
            line.setCustomerStyleNo(blankToNull(text(row, columns.customerStyleNo())));
            line.setEnglishColor(blankToNull(text(row, columns.englishColor())));
            line.setEnglishMaterial(blankToNull(text(row, columns.englishMaterial())));
            line.setUpperMaterial(blankToNull(text(row, columns.upperMaterial())));
            line.setLiningMaterial(blankToNull(text(row, columns.liningMaterial())));
            line.setAccessory(blankToNull(text(row, columns.accessory())));
            line.setInsolePlatform(blankToNull(text(row, columns.insolePlatform())));
            line.setOutsole(blankToNull(text(row, columns.outsole())));
            line.setTrademark(blankToNull(text(row, columns.trademark())));
            line.setQuantity(integerValue(row, columns.quantity()));
            line.setCartonCount(integerValue(row, columns.cartonCount()));
            line.setTotalQuantity(integerValue(row, columns.totalQuantity()));
            line.setSizeQuantitiesJson(toJson(readSizeQuantities(header, row, columns)));
            line.setShipmentStatus("NOT_SHIPPED");
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

    private Map<String, Integer> readSizeQuantities(Row header, Row row, TableColumns columns) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (header == null) {
            return result;
        }
        for (int col = columns.sizeStart(); col <= columns.sizeEnd(); col++) {
            String size = text(header, col);
            Integer quantity = integerValue(row, col);
            if (size == null || size.isBlank() || quantity == null || quantity <= 0) {
                continue;
            }
            result.merge(size, quantity, Integer::sum);
        }
        return result;
    }

    private Map<Integer, PictureInfo> extractPicturesByRow(Sheet sheet, int imageColumn, int firstDataRowIndex) {
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
                if (col != imageColumn || row < firstDataRowIndex + 1) {
                    continue;
                }
                XSSFPictureData pictureData = picture.getPictureData();
                String extension = pictureData.suggestFileExtension();
                result.putIfAbsent(row, new PictureInfo(pictureData.getData(), extension));
            }
        }
        return result;
    }

    private Row findTotalRow(Sheet sheet, TableColumns columns) {
        for (int rowIndex = columns.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && isTotalRow(row, columns)) {
                return row;
            }
        }
        return null;
    }

    private boolean isTotalRow(Row row, TableColumns columns) {
        String styleNo = text(row, columns.lastNo());
        String developmentNo = text(row, columns.developmentNo());
        String totalLabel = rowText(row, columns.firstDataColumn(), columns.dataEnd());
        return (styleNo.isBlank() && developmentNo.isBlank() && integerValue(row, columns.totalQuantity()) != null)
                || totalLabel.contains("合计");
    }

    private boolean isBlankRow(Row row, TableColumns columns) {
        for (int col = columns.firstDataColumn(); col <= columns.dataEnd(); col++) {
            if (!text(row, col).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private boolean isRepeatedHeaderRow(Row row, TableColumns columns) {
        return normalizeHeader(text(row, columns.image())).equals("图片")
                || normalizeHeader(text(row, columns.lastNo())).equals("楦头")
                || normalizeHeader(text(row, columns.developmentNo())).equals("开发编号");
    }

    private String rowText(Row row, int startColumn, int endColumn) {
        StringBuilder builder = new StringBuilder();
        for (int col = Math.max(0, startColumn); col <= endColumn; col++) {
            builder.append(text(row, col));
        }
        return builder.toString();
    }

    private String text(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        return row == null ? "" : text(row, colIndex);
    }

    private String text(Row row, int colIndex) {
        if (row == null || colIndex < 0) {
            return "";
        }
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return "";
        }
        String value = formatter.formatCellValue(cell);
        return value == null ? "" : value.trim();
    }

    private Cell cell(Row row, int colIndex) {
        if (row == null || colIndex < 0) {
            return null;
        }
        return row.getCell(colIndex);
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

    private static String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\u00A0", "")
                .replaceAll("\\s+", "")
                .replace("：", ":")
                .replace("／", "/")
                .trim();
    }

    private static int findColumn(Row header, int fallback, String... aliases) {
        if (header == null) {
            return fallback;
        }
        int first = Math.max(0, header.getFirstCellNum());
        int last = Math.max(first, header.getLastCellNum() - 1);
        for (int col = first; col <= last; col++) {
            Cell cell = header.getCell(col);
            if (cell == null) {
                continue;
            }
            String name = normalizeHeader(new DataFormatter(Locale.CHINA).formatCellValue(cell));
            for (String alias : aliases) {
                String normalizedAlias = normalizeHeader(alias);
                if (name.equals(normalizedAlias)) {
                    return col;
                }
            }
        }
        return fallback;
    }

    private static int firstPositive(int... columns) {
        int result = Integer.MAX_VALUE;
        for (int column : columns) {
            if (column >= 0 && column < result) {
                result = column;
            }
        }
        return result == Integer.MAX_VALUE ? -1 : result;
    }

    private record TableColumns(
            int image,
            int lastNo,
            int developmentNo,
            int customer,
            int customerOrderNo,
            int deliveryDate,
            int po,
            int customerStyleNo,
            int englishColor,
            int englishMaterial,
            int upperMaterial,
            int liningMaterial,
            int accessory,
            int insolePlatform,
            int outsole,
            int trademark,
            int quantity,
            int cartonCount,
            int totalQuantity,
            int sizeStart,
            int sizeEnd,
            int dataEnd,
            int headerRowIndex
    ) {
        static TableColumns from(Row header) {
            int image = findColumn(header, COL_IMAGE, "图片");
            int lastNo = findColumn(header, COL_LAST_NO, "楦头", "楦头号");
            int developmentNo = findColumn(header, COL_DEVELOPMENT_NO, "开发编号", "开发号");
            int customer = findColumn(header, COL_CUSTOMER, "客人", "客户");
            int customerOrderNo = findColumn(header, COL_CUSTOMER_ORDER_NO, "客人订单号", "客户订单号");
            int deliveryDate = findColumn(header, COL_DELIVERY_DATE, "出货时间", "出货日期");
            int po = findColumn(header, COL_PO, "PO", "PONO", "PO号");
            int customerStyleNo = findColumn(header, COL_CUSTOMER_STYLE_NO, "客人型体号", "客户型体号");
            int englishColor = findColumn(header, COL_ENGLISH_COLOR, "英文颜色");
            int englishMaterial = findColumn(header, COL_ENGLISH_MATERIAL, "英文材质");
            int upperMaterial = findColumn(header, COL_UPPER_MATERIAL, "面料");
            int liningMaterial = findColumn(header, COL_LINING_MATERIAL, "里料/垫脚", "里料", "垫脚");
            int accessory = findColumn(header, COL_ACCESSORY, "饰扣/鞋带", "饰扣", "鞋带");
            int insolePlatform = findColumn(header, COL_INSOLE_PLATFORM, "中底/包中底", "中底", "包中底");
            int outsole = findColumn(header, COL_OUTSOLE, "大底");
            int trademark = findColumn(header, COL_TRADEMARK, "商标");
            int quantity = findColumn(header, COL_QUANTITY, "双数", "数量");
            int cartonCount = findColumn(header, COL_CARTON_COUNT, "箱数");
            int totalQuantity = findColumn(header, COL_TOTAL_QUANTITY, "总数量", "总数");
            int firstSummary = firstPositive(quantity, cartonCount, totalQuantity);
            int sizeStart = Math.max(COL_SIZE_START, trademark + 1);
            int sizeEnd = firstSummary > sizeStart ? firstSummary - 1 : Math.max(sizeStart - 1, header == null ? COL_QUANTITY - 1 : header.getLastCellNum() - 1);
            int dataEnd = Math.max(totalQuantity, Math.max(cartonCount, Math.max(quantity, sizeEnd)));
            return new TableColumns(
                    image,
                    lastNo,
                    developmentNo,
                    customer,
                    customerOrderNo,
                    deliveryDate,
                    po,
                    customerStyleNo,
                    englishColor,
                    englishMaterial,
                    upperMaterial,
                    liningMaterial,
                    accessory,
                    insolePlatform,
                    outsole,
                    trademark,
                    quantity,
                    cartonCount,
                    totalQuantity,
                    sizeStart,
                    sizeEnd,
                    dataEnd,
                    header == null ? FALLBACK_HEADER_ROW_INDEX : header.getRowNum()
            );
        }

        int firstDataColumn() {
            return Math.min(lastNo, developmentNo);
        }
    }

    private record PictureInfo(byte[] bytes, String extension) {
    }
}
