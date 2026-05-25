package com.shoefactory.assistant.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.entity.OrderPackingDetail;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import com.shoefactory.assistant.enums.OrderExcelColumn;
import com.shoefactory.assistant.enums.OrderExcelTemplate;
import com.shoefactory.assistant.enums.OrderSourceType;
import com.shoefactory.assistant.service.OrderExcelImportService;
import com.shoefactory.assistant.service.OrderImportResult;
import com.shoefactory.assistant.util.ExcelCellImageUtil;
import com.shoefactory.assistant.util.FileStorageUtil;
import com.shoefactory.assistant.util.StoredFile;
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
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

@Service
public class OrderExcelImportServiceImpl implements OrderExcelImportService {

    private static final LocalDate MIN_SUPPORTED_DATE = LocalDate.of(2000, 1, 1);
    private static final LocalDate MAX_SUPPORTED_DATE = LocalDate.of(2100, 12, 31);

    private final DataFormatter formatter = new DataFormatter(Locale.CHINA);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FileStorageUtil fileStorageUtil;

    public OrderExcelImportServiceImpl(FileStorageUtil fileStorageUtil) {
        this.fileStorageUtil = fileStorageUtil;
    }

    @Override
    public OrderRecord readOrderSummary(Path sourceExcel, StoredFile storedFile) {
        try (Workbook workbook = WorkbookFactory.create(sourceExcel.toFile())) {
            return parseOrderSummary(workbook, storedFile);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException("读取 Excel 订单失败", ex);
        }
    }

    @Override
    public OrderImportResult importOrderDetails(Path sourceExcel, StoredFile storedFile, String fileNo) {
        try (Workbook workbook = WorkbookFactory.create(sourceExcel.toFile())) {
            OrderSheetImport orderSheet = parseOrderSheet(workbook, storedFile, fileNo);
            return new OrderImportResult(orderSheet.order(), orderSheet.details());
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException("读取 Excel 订单失败", ex);
        }
    }

    @Override
    public List<OrderPackingDetail> importPackingDetails(Path sourceExcel, OrderRecord order, String fileNo) {
        try (Workbook workbook = WorkbookFactory.create(sourceExcel.toFile())) {
            return parsePackingSheet(workbook, fileNo, order);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException("读取 Excel 装箱单失败", ex);
        }
    }

    private OrderRecord parseOrderSummary(Workbook workbook, StoredFile storedFile) {
        Sheet sheet = workbook.getSheet(OrderExcelTemplate.DEFAULT.getOrderSheetName());
        if (sheet == null) {
            throw new BusinessException("Excel 中未找到“订单”sheet");
        }
        Row header = findHeaderRow(sheet);
        if (header == null) {
            throw new BusinessException("订单 sheet 未找到明细表头行");
        }
        TableColumns columns = TableColumns.from(header);
        OrderRecord order = buildOrderRecord(sheet, storedFile, columns);
        fillUploadSummaryFromDetails(sheet, order, columns);
        return order;
    }

    private OrderSheetImport parseOrderSheet(Workbook workbook, StoredFile storedFile, String fileNo) {
        Sheet sheet = workbook.getSheet(OrderExcelTemplate.DEFAULT.getOrderSheetName());
        if (sheet == null) {
            throw new BusinessException("Excel 中未找到“订单”sheet");
        }
        Row header = findHeaderRow(sheet);
        if (header == null) {
            throw new BusinessException("订单 sheet 未找到明细表头行");
        }
        // TableColumns 会把“图片、楦头、开发编号...”这些业务字段映射成真实列号。
        TableColumns columns = TableColumns.from(header);
        int firstDataRowIndex = header.getRowNum() + 1;
        Map<Integer, PictureInfo> picturesByRow = extractPicturesByRow(sheet, columns.image(), firstDataRowIndex);
        OrderRecord order = buildOrderRecord(sheet, storedFile, columns);
        List<OrderRecordDetail> details = buildOrderDetails(sheet, fileNo, order, picturesByRow, columns, firstDataRowIndex);
        if (details.isEmpty()) {
            throw new BusinessException("订单 sheet 未解析到明细行");
        }
        order.setDevelopmentNos(joinDevelopmentNos(details));
        fillTotalsFromDetails(order, details);
        return new OrderSheetImport(order, details);
    }

    private void fillUploadSummaryFromDetails(Sheet sheet, OrderRecord order, TableColumns columns) {
        List<OrderRecordDetail> details = buildOrderDetails(
                sheet,
                "SUMMARY",
                order,
                Map.of(),
                columns,
                columns.headerRowIndex() + 1
        );
        if (details.isEmpty()) {
            return;
        }
        order.setDevelopmentNos(joinDevelopmentNos(details));
        fillTotalsFromDetails(order, details);
    }

    private OrderRecord buildOrderRecord(Sheet sheet, StoredFile storedFile, TableColumns columns) {
        LocalDateTime now = LocalDateTime.now();
        OrderRecord order = new OrderRecord();
        order.setOrderNo(blankToNull(resolveOrderNo(sheet, storedFile.getOriginalName())));
        order.setCustomerName(blankToNull(resolveCustomerName(sheet)));
        order.setSourceType(OrderSourceType.EXCEL.getCode());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        // 订单总双数和箱数以“合计行”为准，避免逐行累加时遇到空行/重复表头出错。
        Row totalRow = findTotalRow(sheet, columns);
        order.setTotalQuantity(totalRow == null ? 0 : nullToZero(integerValue(totalRow, columns.totalQuantity())));
        order.setTotalCartonCount(totalRow == null ? 0 : nullToZero(integerValue(totalRow, columns.cartonCount())));
        return order;
    }

    private Row findHeaderRow(Sheet sheet) {
        Row fallback = sheet.getRow(OrderExcelTemplate.DEFAULT.getFallbackHeaderRowIndex());
        Row bestRow = null;
        int bestScore = 0;
        int lastScanRow = Math.min(sheet.getLastRowNum(), OrderExcelTemplate.DEFAULT.getMaxHeaderScanRows() - 1);
        for (int rowIndex = 0; rowIndex <= lastScanRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            int score = headerScore(row);
            if (score > bestScore) {
                bestScore = score;
                bestRow = row;
            }
        }
        // 分数太低说明没有可靠表头，退回到第 5 行的老样本位置。
        return bestScore >= 4 ? bestRow : fallback;
    }

    private int headerScore(Row row) {
        if (row == null) {
            return 0;
        }
        int score = 0;
        for (OrderExcelColumn column : OrderExcelColumn.values()) {
            if (column.participatesInHeaderScore() && hasHeader(row, column)) {
                score += column.getHeaderScoreWeight();
            }
        }
        return score;
    }

    private boolean hasHeader(Row row, OrderExcelColumn column) {
        if (row == null) {
            return false;
        }
        int first = Math.max(0, row.getFirstCellNum());
        int last = Math.max(first, row.getLastCellNum() - 1);
        for (int col = first; col <= last; col++) {
            String value = normalizeHeader(text(row, col));
            for (String alias : column.getAliases()) {
                if (value.equals(normalizeHeader(alias))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String resolveOrderNo(Sheet sheet, String originalFileName) {
        // 订单号在不同模板里位置不完全一致，所以从“标签附近、固定单元格、文件名”逐级兜底。
        String invoiceNo = findValueAfterLabel(sheet, "发票编号", 0, 8);
        if (!invoiceNo.isBlank()) {
            return invoiceNo;
        }

        String labeledOrderNo = findValueAfterLabel(sheet, "订单流水号", 0, 8);
        if (!labeledOrderNo.isBlank()) {
            return labeledOrderNo;
        }

        String fixedInvoiceNo = text(sheet, 3, 6);
        if (!fixedInvoiceNo.isBlank()) {
            return fixedInvoiceNo;
        }

        String fixedOrderNo = text(sheet, 1, 32);
        if (!fixedOrderNo.isBlank()) {
            return fixedOrderNo;
        }

        return leadingOrderNoFromFileName(originalFileName);
    }

    private String resolveCustomerName(Sheet sheet) {
        String labeledCustomerName = findValueAfterExactLabel(sheet, "客户", 0, 4);
        if (!labeledCustomerName.isBlank()) {
            return labeledCustomerName;
        }
        return text(sheet, 1, 6);
    }

    private String findValueAfterExactLabel(Sheet sheet, String label, int startRow, int endRow) {
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
                if (!cellText.equals(normalizedLabel)) {
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
        Matcher matcher = OrderExcelTemplate.DEFAULT.getLeadingOrderNoPattern().matcher(fileName.trim());
        return matcher.find() ? matcher.group(1) : "";
    }

    private List<OrderRecordDetail> buildOrderDetails(
            Sheet sheet,
            String fileNo,
            OrderRecord order,
            Map<Integer, PictureInfo> picturesByRow,
            TableColumns columns,
            int firstDataRowIndex
    ) {
        Row header = sheet.getRow(columns.headerRowIndex());
        LocalDateTime now = LocalDateTime.now();
        return parseDataRows(
                sheet,
                columns,
                firstDataRowIndex,
                true,
                this::isTotalRow,
                this::isBlankRow,
                this::isRepeatedHeaderRow,
                (row, rowIndex, lineNo) -> buildOrderDetailRow(
                        sheet,
                        header,
                        fileNo,
                        order,
                        picturesByRow,
                        columns,
                        now,
                        row,
                        rowIndex,
                        lineNo
                )
        );
    }

    private OrderRecordDetail buildOrderDetailRow(
            Sheet sheet,
            Row header,
            String fileNo,
            OrderRecord order,
            Map<Integer, PictureInfo> picturesByRow,
            TableColumns columns,
            LocalDateTime now,
            Row row,
            int rowIndex,
            int lineNo
    ) {
        OrderRecordDetail detail = new OrderRecordDetail();
        detail.setLineNo(lineNo);
        detail.setCustomerName(blankToNull(firstText(text(row, columns.customer()), order.getCustomerName())));
        detail.setDeliveryDate(dateValue(cell(row, columns.deliveryDate())));
        detail.setLastNo(blankToNull(text(row, columns.lastNo())));
        detail.setDevelopmentNo(blankToNull(text(row, columns.developmentNo())));
        detail.setCustomerOrderNo(blankToNull(text(row, columns.customerOrderNo())));
        detail.setPoNo(blankToNull(text(row, columns.po())));
        detail.setCustomerStyleNo(blankToNull(text(row, columns.customerStyleNo())));
        detail.setEnglishColor(blankToNull(text(row, columns.englishColor())));
        detail.setEnglishMaterial(blankToNull(text(row, columns.englishMaterial())));
        detail.setUpperMaterial(blankToNull(text(row, columns.upperMaterial())));
        detail.setLiningMaterial(blankToNull(text(row, columns.liningMaterial())));
        detail.setAccessory(blankToNull(text(row, columns.accessory())));
        detail.setInsolePlatform(blankToNull(text(row, columns.insolePlatform())));
        detail.setOutsole(blankToNull(text(row, columns.outsole())));
        detail.setTrademark(blankToNull(text(row, columns.trademark())));

        Map<String, Integer> sizeQuantities = readSizeQuantities(header, row, columns);
        int sizeQuantityTotal = sumPositiveValues(sizeQuantities);
        detail.setQuantity(sizeQuantityTotal > 0 ? sizeQuantityTotal : nullToZero(integerValue(row, columns.quantity())));
        detail.setCartonCount(nullToZero(integerValue(row, columns.cartonCount())));
        detail.setCartonStart(blankToNull(text(row, columns.cartonStart())));
        detail.setCartonEnd(blankToNull(text(row, columns.cartonEnd())));
        detail.setSizeQuantitiesJson(toJson(sizeQuantities));
        detail.setSourceSheetName(sheet.getSheetName());
        detail.setRowIndex(rowIndex + 1);
        detail.setCreatedAt(now);
        detail.setUpdatedAt(now);

        // POI 图片锚点里的行号按 0 开始，picturesByRow 存的是 Excel 可见行号，所以这里 +1。
        PictureInfo picture = picturesByRow.get(rowIndex + 1);
        if (picture != null) {
            Path imagePath = fileStorageUtil.saveOrderDetailImage(
                    picture.bytes(),
                    fileNo,
                    order.getOrderNo(),
                    rowIndex + 1,
                    picture.extension()
            );
            detail.setStyleImagePath(imagePath == null ? null : imagePath.toString());
        }
        return detail;
    }

    private List<OrderPackingDetail> parsePackingSheet(Workbook workbook, String fileNo, OrderRecord order) {
        Sheet packingSheet = findPackingSheet(workbook);
        if (packingSheet == null) {
            throw new BusinessException("Excel 中未找到“装箱单”sheet");
        }
        Row header = findPackingHeaderRow(packingSheet);
        if (header == null) {
            throw new BusinessException("装箱单 sheet 未找到明细表头行");
        }
        PackingColumns columns = PackingColumns.from(header);
        int firstDataRowIndex = header.getRowNum() + 1;
        Map<Integer, PictureInfo> picturesByRow = extractPicturesByRow(packingSheet, columns.image(), firstDataRowIndex);
        LocalDateTime now = LocalDateTime.now();
        List<OrderPackingDetail> details = parseDataRows(
                packingSheet,
                columns,
                firstDataRowIndex,
                false,
                this::isPackingTotalRow,
                this::isPackingBlankRow,
                this::isPackingRepeatedHeaderRow,
                (row, rowIndex, lineNo) -> buildPackingDetailRow(
                        packingSheet,
                        header,
                        fileNo,
                        order,
                        picturesByRow,
                        columns,
                        now,
                        row,
                        rowIndex,
                        lineNo
                )
        );
        if (details.isEmpty()) {
            throw new BusinessException("装箱单 sheet 未解析到明细行");
        }
        return details;
    }

    private OrderPackingDetail buildPackingDetailRow(
            Sheet sheet,
            Row header,
            String fileNo,
            OrderRecord order,
            Map<Integer, PictureInfo> picturesByRow,
            PackingColumns columns,
            LocalDateTime now,
            Row row,
            int rowIndex,
            int lineNo
    ) {
        OrderPackingDetail detail = new OrderPackingDetail();
        detail.setLineNo(lineNo);
        detail.setCompanyStyleNo(blankToNull(text(row, columns.companyStyleNo())));
        detail.setCustomerName(blankToNull(text(row, columns.customer())));
        detail.setCustomerOrderNo(blankToNull(text(row, columns.customerOrderNo())));
        detail.setWarehouseStoreNo(blankToNull(text(row, columns.warehouseStoreNo())));
        detail.setPoNo(blankToNull(text(row, columns.po())));
        detail.setCustomerStyleNo(blankToNull(text(row, columns.customerStyleNo())));
        detail.setCustomerColor(blankToNull(text(row, columns.customerColor())));
        detail.setMaterial(blankToNull(text(row, columns.material())));
        detail.setItemNumber(blankToNull(text(row, columns.itemNumber())));
        detail.setTrademark(blankToNull(text(row, columns.trademark())));
        Map<String, Integer> sizeQuantities = readPackingSizeQuantities(header, row, columns);
        Integer cartonCount = integerValue(row, columns.cartonCount());
        detail.setSizeQuantitiesJson(toJson(sizeQuantities));
        detail.setCartonCount(nullToZero(cartonCount));
        detail.setTotalPairs(calculatePackingTotalPairs(sizeQuantities, cartonCount, integerValue(row, columns.totalPairs())));
        detail.setCartonStart(blankToNull(text(row, columns.cartonStart())));
        detail.setCartonEnd(blankToNull(text(row, columns.cartonEnd())));
        detail.setSourceSheetName(sheet.getSheetName());
        detail.setRowIndex(rowIndex + 1);
        detail.setCreatedAt(now);
        detail.setUpdatedAt(now);

        PictureInfo picture = picturesByRow.get(rowIndex + 1);
        if (picture != null) {
            Path imagePath = fileStorageUtil.saveOrderDetailImage(
                    picture.bytes(),
                    fileNo + "-packing",
                    order.getOrderNo(),
                    rowIndex + 1,
                    picture.extension()
            );
            detail.setStyleImagePath(imagePath == null ? null : imagePath.toString());
        }
        return detail;
    }

    private int calculatePackingTotalPairs(
            Map<String, Integer> sizeQuantities,
            Integer cartonCount,
            Integer parsedTotalPairs
    ) {
        int perCartonPairs = sumPositiveValues(sizeQuantities);
        if (perCartonPairs > 0 && cartonCount != null && cartonCount > 0) {
            return perCartonPairs * cartonCount;
        }
        return nullToZero(parsedTotalPairs);
    }

    private <C, D> List<D> parseDataRows(
            Sheet sheet,
            C columns,
            int firstDataRowIndex,
            boolean stopOnMissingRow,
            BiPredicate<Row, C> totalRowMatcher,
            BiPredicate<Row, C> blankRowMatcher,
            BiPredicate<Row, C> repeatedHeaderMatcher,
            DetailRowMapper<D> rowMapper
    ) {
        List<D> details = new ArrayList<>();
        for (int rowIndex = firstDataRowIndex; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                if (stopOnMissingRow) {
                    break;
                }
                continue;
            }
            if (totalRowMatcher.test(row, columns)) {
                break;
            }
            if (blankRowMatcher.test(row, columns) || repeatedHeaderMatcher.test(row, columns)) {
                continue;
            }
            details.add(rowMapper.map(row, rowIndex, details.size() + 1));
        }
        return details;
    }

    private Sheet findPackingSheet(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (normalizeHeader(sheet.getSheetName()).contains("装箱单")) {
                return sheet;
            }
        }
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (normalizeHeader(sheet.getSheetName()).contains("箱")) {
                return sheet;
            }
        }
        return null;
    }

    private Row findPackingHeaderRow(Sheet sheet) {
        Row bestRow = null;
        int bestScore = 0;
        int lastScanRow = Math.min(sheet.getLastRowNum(), OrderExcelTemplate.DEFAULT.getMaxHeaderScanRows() - 1);
        for (int rowIndex = 0; rowIndex <= lastScanRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            int score = packingHeaderScore(row);
            if (score > bestScore) {
                bestScore = score;
                bestRow = row;
            }
        }
        return bestScore >= 4 ? bestRow : null;
    }

    private int packingHeaderScore(Row row) {
        if (row == null) {
            return 0;
        }
        int score = 0;
        if (findColumnContains(row, "图片") >= 0) {
            score += 1;
        }
        if (findColumnContains(row, "公司款号") >= 0) {
            score += 2;
        }
        if (findColumnContains(row, "PRS") >= 0) {
            score += 1;
        }
        if (findColumnContains(row, "CTNS") >= 0) {
            score += 1;
        }
        if (findColumnContains(row, "CTNEND", "结束箱号") >= 0) {
            score += 2;
        }
        return score;
    }

    private Map<String, Integer> readPackingSizeQuantities(Row header, Row row, PackingColumns columns) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int col = columns.sizeStart(); col <= columns.sizeEnd(); col++) {
            String size = sizeHeaderText(header, col);
            Integer quantity = integerValue(row, col);
            if (size == null || size.isBlank() || quantity == null || quantity <= 0) {
                continue;
            }
            result.merge(size, quantity, Integer::sum);
        }
        return result;
    }

    private boolean isPackingTotalRow(Row row, PackingColumns columns) {
        String rowText = rowText(row, columns.firstDataColumn(), columns.dataEnd());
        return rowText.contains("合计")
                || (text(row, columns.companyStyleNo()).isBlank()
                && integerValue(row, columns.totalPairs()) != null
                && integerValue(row, columns.cartonCount()) != null);
    }

    private boolean isPackingBlankRow(Row row, PackingColumns columns) {
        for (int col = columns.firstDataColumn(); col <= columns.dataEnd(); col++) {
            if (!text(row, col).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private boolean isPackingRepeatedHeaderRow(Row row, PackingColumns columns) {
        return normalizeHeader(text(row, columns.image())).equals("图片")
                || normalizeHeader(text(row, columns.companyStyleNo())).contains("公司款号")
                || normalizeHeader(text(row, columns.pairs())).contains("PRS");
    }

    private String joinDevelopmentNos(List<OrderRecordDetail> details) {
        return details.stream()
                .map(OrderRecordDetail::getDevelopmentNo)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private void fillTotalsFromDetails(OrderRecord order, List<OrderRecordDetail> details) {
        int detailQuantityTotal = details.stream()
                .map(OrderRecordDetail::getQuantity)
                .filter(value -> value != null && value > 0)
                .mapToInt(Integer::intValue)
                .sum();
        int detailCartonTotal = details.stream()
                .map(OrderRecordDetail::getCartonCount)
                .filter(value -> value != null && value > 0)
                .mapToInt(Integer::intValue)
                .sum();
        if (detailQuantityTotal > 0) {
            order.setTotalQuantity(detailQuantityTotal);
        }
        if (detailCartonTotal > 0) {
            order.setTotalCartonCount(detailCartonTotal);
        }
    }

    private int sumPositiveValues(Map<String, Integer> values) {
        return values.values().stream()
                .filter(value -> value != null && value > 0)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private Map<String, Integer> readSizeQuantities(Row header, Row row, TableColumns columns) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (header == null) {
            return result;
        }
        // 尺码列不是固定数量，从“商标”后面一直读到“双数/箱数/总数量”前面。
        for (int col = columns.sizeStart(); col <= columns.sizeEnd(); col++) {
            String size = sizeHeaderText(header, col);
            Integer quantity = integerValue(row, col);
            if (size == null || size.isBlank() || quantity == null || quantity <= 0) {
                continue;
            }
            result.merge(size, quantity, Integer::sum);
        }
        return result;
    }

    private String sizeHeaderText(Row header, int colIndex) {
        String primary = text(header, colIndex);
        if (primary.isBlank()) {
            return "";
        }
        // 有些订单是“双码”表头：表头上一行保存欧码，当前表头行保存美码，例如 6.5 / 36。
        String secondary = text(header.getSheet(), header.getRowNum() - 1, colIndex);
        if (isUsableSecondarySize(primary, secondary)) {
            return primary + "/" + secondary;
        }
        return primary;
    }

    private boolean isUsableSecondarySize(String primary, String secondary) {
        if (secondary == null || secondary.isBlank()) {
            return false;
        }
        String normalizedPrimary = normalizeHeader(primary);
        String normalizedSecondary = normalizeHeader(secondary);
        if (normalizedSecondary.equals(normalizedPrimary)) {
            return false;
        }
        if (normalizedSecondary.contains("图片")
                || normalizedSecondary.contains("楦头")
                || normalizedSecondary.contains("开发编号")
                || normalizedSecondary.contains("商标")
                || normalizedSecondary.contains("双数")
                || normalizedSecondary.contains("箱数")
                || normalizedSecondary.contains("CTNSTART")
                || normalizedSecondary.contains("CTNEND")
                || normalizedSecondary.contains("开始箱号")
                || normalizedSecondary.contains("结束箱号")
                || normalizedSecondary.contains("总数量")) {
            return false;
        }
        return normalizedSecondary.matches("\\d+(\\.\\d+)?");
    }

    private Map<Integer, PictureInfo> extractPicturesByRow(Sheet sheet, int imageColumn, int firstDataRowIndex) {
        Map<Integer, PictureInfo> result = new LinkedHashMap<>();
        if (imageColumn < 0) {
            return result;
        }
        if (!(sheet instanceof XSSFSheet xssfSheet)) {
            return result;
        }
        // 只有 xlsx 能通过 XSSF 读取内嵌图片；xls 仍可导入文字明细，但图片会为空。
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
        // 合计行有时写“合计”，有时只在总数量列有数字；两个规则都兼容。
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
        if (ExcelCellImageUtil.findCellImageId(cell).isPresent()) {
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
                return supportedDateOrNull(DateUtil.getJavaDate(numericValue)
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate());
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
                LocalDate parsedDate = LocalDate.parse(normalized, dateFormatter);
                if (isSupportedDate(parsedDate)) {
                    return parsedDate;
                }
            } catch (DateTimeParseException ignored) {
                // Try the next supported date format.
            }
        }
        try {
            double serial = Double.parseDouble(normalized);
            if (DateUtil.isValidExcelDate(serial)) {
                return supportedDateOrNull(DateUtil.getJavaDate(serial)
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate());
            }
        } catch (NumberFormatException ignored) {
            // Not an Excel serial date.
        }
        return null;
    }

    private LocalDate supportedDateOrNull(LocalDate date) {
        return isSupportedDate(date) ? date : null;
    }

    private boolean isSupportedDate(LocalDate date) {
        return date != null && !date.isBefore(MIN_SUPPORTED_DATE) && !date.isAfter(MAX_SUPPORTED_DATE);
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

    private String firstText(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private Integer nullToZero(Integer value) {
        return value == null ? 0 : value;
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

    private static int findColumn(Row header, OrderExcelColumn column) {
        if (header == null) {
            return column.getFallbackIndex();
        }
        int first = Math.max(0, header.getFirstCellNum());
        int last = Math.max(first, header.getLastCellNum() - 1);
        for (int col = first; col <= last; col++) {
            Cell cell = header.getCell(col);
            if (cell == null) {
                continue;
            }
            String name = normalizeHeader(new DataFormatter(Locale.CHINA).formatCellValue(cell));
            for (String alias : column.getAliases()) {
                String normalizedAlias = normalizeHeader(alias);
                if (name.equalsIgnoreCase(normalizedAlias)) {
                    return col;
                }
            }
        }
        // 找不到表头文字时，用样本订单的固定列号兜底。
        return column.getFallbackIndex();
    }

    private static int findColumnContains(Row header, String... aliases) {
        return findColumnByHeader(header, false, aliases);
    }

    private static int findColumnExact(Row header, String... aliases) {
        return findColumnByHeader(header, true, aliases);
    }

    private static int findColumnByHeader(Row header, boolean exact, String... aliases) {
        if (header == null) {
            return -1;
        }
        int first = Math.max(0, header.getFirstCellNum());
        int last = Math.max(first, header.getLastCellNum() - 1);
        for (int col = first; col <= last; col++) {
            Cell cell = header.getCell(col);
            if (cell == null) {
                continue;
            }
            String name = normalizeHeader(new DataFormatter(Locale.CHINA).formatCellValue(cell)).toUpperCase(Locale.ROOT);
            for (String alias : aliases) {
                String normalizedAlias = normalizeHeader(alias).toUpperCase(Locale.ROOT);
                if (exact ? name.equals(normalizedAlias) : name.contains(normalizedAlias)) {
                    return col;
                }
            }
        }
        return -1;
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

    /*
     * 解析出来的“业务字段 -> Excel 列号”映射。
     * record 只是一个轻量数据盒子，避免在方法之间传一长串 int 参数。
     */
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
            int cartonStart,
            int cartonEnd,
            int totalQuantity,
            int sizeStart,
            int sizeEnd,
            int dataEnd,
            int headerRowIndex
    ) {
        static TableColumns from(Row header) {
            // 每个字段都支持常见别名，比如“客人/客户”“开发编号/开发号”。
            int image = findColumn(header, OrderExcelColumn.IMAGE);
            int lastNo = findColumn(header, OrderExcelColumn.LAST_NO);
            int developmentNo = findColumn(header, OrderExcelColumn.DEVELOPMENT_NO);
            int customer = findColumn(header, OrderExcelColumn.CUSTOMER);
            int customerOrderNo = findColumn(header, OrderExcelColumn.CUSTOMER_ORDER_NO);
            int deliveryDate = findColumn(header, OrderExcelColumn.DELIVERY_DATE);
            int po = findColumn(header, OrderExcelColumn.PO);
            int customerStyleNo = findColumn(header, OrderExcelColumn.CUSTOMER_STYLE_NO);
            int englishColor = findColumn(header, OrderExcelColumn.ENGLISH_COLOR);
            int englishMaterial = findColumn(header, OrderExcelColumn.ENGLISH_MATERIAL);
            int upperMaterial = findColumn(header, OrderExcelColumn.UPPER_MATERIAL);
            int liningMaterial = findColumn(header, OrderExcelColumn.LINING_MATERIAL);
            int accessory = findColumn(header, OrderExcelColumn.ACCESSORY);
            int insolePlatform = findColumn(header, OrderExcelColumn.INSOLE_PLATFORM);
            int outsole = findColumn(header, OrderExcelColumn.OUTSOLE);
            int trademark = findColumn(header, OrderExcelColumn.TRADEMARK);
            int quantity = findColumn(header, OrderExcelColumn.QUANTITY);
            int cartonCount = findColumn(header, OrderExcelColumn.CARTON_COUNT);
            int cartonStart = findColumn(header, OrderExcelColumn.CARTON_START);
            int cartonEnd = findColumn(header, OrderExcelColumn.CARTON_END);
            int totalQuantity = findColumn(header, OrderExcelColumn.TOTAL_QUANTITY);
            int firstSummary = firstPositive(quantity, cartonCount, totalQuantity, cartonStart, cartonEnd);
            // 尺码从商标后一列开始；如果商标列识别异常，再退回样本订单的尺码起始列。
            int sizeStart = Math.max(OrderExcelColumn.TRADEMARK.getFallbackIndex() + 1, trademark + 1);
            int sizeEnd = firstSummary > sizeStart
                    ? firstSummary - 1
                    : Math.max(sizeStart - 1, header == null ? OrderExcelColumn.QUANTITY.getFallbackIndex() - 1 : header.getLastCellNum() - 1);
            int dataEnd = Math.max(totalQuantity, Math.max(cartonEnd, Math.max(cartonStart, Math.max(cartonCount, Math.max(quantity, sizeEnd)))));
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
                    cartonStart,
                    cartonEnd,
                    totalQuantity,
                    sizeStart,
                    sizeEnd,
                    dataEnd,
                    header == null ? OrderExcelTemplate.DEFAULT.getFallbackHeaderRowIndex() : header.getRowNum()
            );
        }

        int firstDataColumn() {
            // 判断空行/合计行时，从最早的业务列开始看，不把表格左侧说明文字算进去。
            return Math.min(lastNo, developmentNo);
        }
    }

    private record PackingColumns(
            int image,
            int companyStyleNo,
            int customer,
            int customerOrderNo,
            int warehouseStoreNo,
            int po,
            int customerStyleNo,
            int customerColor,
            int material,
            int itemNumber,
            int trademark,
            int pairs,
            int cartonCount,
            int totalPairs,
            int cartonStart,
            int cartonEnd,
            int sizeStart,
            int sizeEnd,
            int dataEnd
    ) {
        static PackingColumns from(Row header) {
            int image = findColumnContains(header, "图片");
            int companyStyleNo = findColumnContains(header, "公司款号");
            int customer = findColumnExact(header, "客人", "客户");
            int customerOrderNo = findColumnContains(header, "客人订单号", "客户订单号");
            int warehouseStoreNo = findColumnContains(header, "仓库号", "店铺号");
            int po = findColumnContains(header, "PO");
            int customerStyleNo = findColumnContains(header, "STYLE/客人款号", "客人款号");
            int customerColor = findColumnContains(header, "COLOR/客人颜色", "客人颜色");
            int material = findColumnContains(header, "MATERIAL/面料材质", "面料材质");
            int itemNumber = findColumnContains(header, "ITEMNUMBER", "项目编号");
            int trademark = findColumnContains(header, "商标");
            int pairs = findColumnExact(header, "PRS", "双数");
            int cartonCount = findColumnExact(header, "CTNS", "箱数");
            int totalPairs = findColumnContains(header, "TTLPRS", "TOTALPRS", "总数量");
            int cartonStart = findColumnContains(header, "CTNSTART", "开始箱号");
            int cartonEnd = findColumnContains(header, "CTNEND", "结束箱号");
            int firstSummary = firstPositive(pairs, cartonCount, totalPairs, cartonStart, cartonEnd);
            int sizeStart = trademark >= 0 ? trademark + 1 : -1;
            int sizeEnd = sizeStart >= 0 && firstSummary > sizeStart ? firstSummary - 1 : sizeStart - 1;
            int dataEnd = Math.max(cartonEnd, Math.max(totalPairs, Math.max(cartonCount, Math.max(pairs, sizeEnd))));
            if (dataEnd == Integer.MAX_VALUE || dataEnd < 0) {
                dataEnd = header == null ? 0 : Math.max(0, header.getLastCellNum() - 1);
            }
            return new PackingColumns(
                    image,
                    companyStyleNo,
                    customer,
                    customerOrderNo,
                    warehouseStoreNo,
                    po,
                    customerStyleNo,
                    customerColor,
                    material,
                    itemNumber,
                    trademark,
                    pairs,
                    cartonCount,
                    totalPairs,
                    cartonStart,
                    cartonEnd,
                    sizeStart,
                    sizeEnd,
                    dataEnd
            );
        }

        int firstDataColumn() {
            int first = firstPositive(image, companyStyleNo, customer, customerOrderNo);
            return first < 0 ? 0 : first;
        }
    }

    private record OrderSheetImport(OrderRecord order, List<OrderRecordDetail> details) {
    }

    @FunctionalInterface
    private interface DetailRowMapper<D> {
        D map(Row row, int rowIndex, int lineNo);
    }

    private record PictureInfo(byte[] bytes, String extension) {
    }
}
