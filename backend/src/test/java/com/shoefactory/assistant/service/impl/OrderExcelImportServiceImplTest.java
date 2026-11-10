package com.shoefactory.assistant.service.impl;

import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.config.FileStorageProperties;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.service.OrderImportResult;
import com.shoefactory.assistant.util.FileStorageUtil;
import com.shoefactory.assistant.util.StoredFile;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderExcelImportServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void readsOrderSheetWhenSheetNameHasTrailingSpace() throws Exception {
        Path workbookPath = tempDir.resolve("order.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            populateOrderSheet(workbook.createSheet("订单 "));
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            try (OutputStream outputStream = Files.newOutputStream(workbookPath)) {
                workbook.write(outputStream);
            }
        }

        OrderExcelImportServiceImpl service = new OrderExcelImportServiceImpl(fileStorageUtil());
        StoredFile storedFile = new StoredFile(
                "JCC-2607-JCD-172.xlsx",
                "xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Files.size(workbookPath),
                workbookPath
        );

        OrderRecord summary = service.readOrderSummary(workbookPath, storedFile);
        assertEquals("JCC-2607", summary.getOrderNo());
        assertEquals(43, summary.getTotalQuantity());
        assertEquals(5, summary.getTotalCartonCount());

        OrderImportResult importResult = service.importOrderDetails(workbookPath, storedFile, "SFTEST");
        assertEquals(2, importResult.getDetails().size());
        assertEquals(43, importResult.getOrder().getTotalQuantity());
        assertEquals(5, importResult.getOrder().getTotalCartonCount());
    }

    @Test
    void skipsEmptyPackingTemplateRows() throws Exception {
        Path workbookPath = tempDir.resolve("packing.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("装箱单");
            populatePackingHeader(sheet.createRow(0));
            sheet.createRow(1).createCell(2).setCellValue("模板中的空行");
            populatePackingDetailRow(sheet.createRow(2));
            try (OutputStream outputStream = Files.newOutputStream(workbookPath)) {
                workbook.write(outputStream);
            }
        }

        OrderExcelImportServiceImpl service = new OrderExcelImportServiceImpl(fileStorageUtil());
        var details = service.importPackingDetails(workbookPath, packingOrder(), "SFTEST");

        assertEquals(1, details.size());
        assertEquals("JCD-401-1-01", details.get(0).getCompanyStyleNo());
    }

    @Test
    void readsPackingContinuationRowsBeforeTotal() throws Exception {
        Path workbookPath = tempDir.resolve("packing-continuation.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("装箱单");
            populatePackingHeader(sheet.createRow(0));
            populatePackingDetailRow(sheet.createRow(1));
            Row continuation = sheet.createRow(2);
            continuation.createCell(5).setCellValue(12);
            continuation.createCell(6).setCellFormula("F3");
            continuation.createCell(7).setCellValue(2);
            continuation.createCell(8).setCellFormula("G3*H3");
            continuation.createCell(9).setCellValue("101");
            continuation.createCell(10).setCellValue("102");

            Row nextStyle = sheet.createRow(3);
            populatePackingDetailRow(nextStyle);
            nextStyle.getCell(1).setCellValue("JCD-401-1-02");
            nextStyle.getCell(9).setCellValue("103");
            nextStyle.getCell(10).setCellValue("103");

            Row total = sheet.createRow(4);
            total.createCell(7).setCellFormula("SUM(H2:H4)");
            total.createCell(8).setCellFormula("SUM(I2:I4)");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            try (OutputStream outputStream = Files.newOutputStream(workbookPath)) {
                workbook.write(outputStream);
            }
        }

        OrderExcelImportServiceImpl service = new OrderExcelImportServiceImpl(fileStorageUtil());
        var details = service.importPackingDetails(workbookPath, packingOrder(), "SFTEST");

        assertEquals(3, details.size());
        assertEquals("JCD-401-1-01", details.get(1).getCompanyStyleNo());
        assertEquals("TEST", details.get(1).getCustomerName());
        assertEquals("101", details.get(1).getCartonStart());
        assertEquals(24, details.get(1).getTotalPairs());
        assertEquals("JCD-401-1-02", details.get(2).getCompanyStyleNo());
        assertEquals(4, details.stream().mapToInt(detail -> detail.getCartonCount()).sum());
    }

    @Test
    void rejectsPackingSheetWithoutActualData() throws Exception {
        Path workbookPath = tempDir.resolve("empty-packing.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("装箱单");
            populatePackingHeader(sheet.createRow(0));
            sheet.createRow(1).createCell(2).setCellValue("模板中的空行");
            try (OutputStream outputStream = Files.newOutputStream(workbookPath)) {
                workbook.write(outputStream);
            }
        }

        OrderExcelImportServiceImpl service = new OrderExcelImportServiceImpl(fileStorageUtil());
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.importPackingDetails(workbookPath, packingOrder(), "SFTEST")
        );

        assertTrue(error.getMessage().contains("未解析到明细行"));
    }

    private FileStorageUtil fileStorageUtil() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setRootPath(tempDir.resolve("storage").toString());
        return new FileStorageUtil(properties);
    }

    private void populateOrderSheet(Sheet sheet) {
        Row metadata = sheet.createRow(3);
        metadata.createCell(6).setCellValue("发票编号：");
        metadata.createCell(7).setCellValue("JCC-2607");

        Row header = sheet.createRow(4);
        header.createCell(6).setCellValue("图片");
        header.createCell(7).setCellValue("楦头");
        header.createCell(8).setCellValue("开发编号");
        header.createCell(9).setCellValue("客人");
        header.createCell(10).setCellValue("客人订单号");
        header.createCell(12).setCellValue("PO号码");
        header.createCell(22).setCellValue("商标");
        header.createCell(24).setCellValue("5");
        header.createCell(25).setCellValue("5.5");
        header.createCell(39).setCellValue("双数");
        header.createCell(40).setCellValue("箱数");
        header.createCell(41).setCellValue("总数量");
        header.createCell(42).setCellValue("开始箱号");
        header.createCell(43).setCellValue("结束箱号");

        populateDetailRow(sheet.createRow(5), "JCD-172-57-03", 26, 3);
        populateDetailRow(sheet.createRow(6), "JCD-172-57-02", 17, 2);

        Row total = sheet.createRow(7);
        total.createCell(6).setCellValue("合计");
        total.createCell(40).setCellFormula("SUM(AO6:AO7)");
        total.createCell(41).setCellFormula("SUM(AP6:AP7)");
    }

    private void populateDetailRow(Row row, String developmentNo, int quantity, int cartons) {
        row.createCell(7).setCellValue("JCD-172");
        row.createCell(8).setCellValue(developmentNo);
        row.createCell(9).setCellValue("JCC");
        row.createCell(10).setCellValue("DV2026007");
        row.createCell(22).setCellValue("Jeffrey Campbell");
        row.createCell(24).setCellValue(quantity);
        int rowNumber = row.getRowNum() + 1;
        row.createCell(39).setCellFormula("Y" + rowNumber);
        row.createCell(40).setCellFormula(cartons + "+0");
        row.createCell(41).setCellFormula("AN" + rowNumber);
    }

    private OrderRecord packingOrder() {
        OrderRecord order = new OrderRecord();
        order.setOrderNo("ORDER-401");
        order.setCustomerName("TEST");
        return order;
    }

    private void populatePackingHeader(Row header) {
        header.createCell(0).setCellValue("图片");
        header.createCell(1).setCellValue("公司款号");
        header.createCell(2).setCellValue("客人");
        header.createCell(3).setCellValue("客人订单号");
        header.createCell(4).setCellValue("商标");
        header.createCell(5).setCellValue("6");
        header.createCell(6).setCellValue("PRS");
        header.createCell(7).setCellValue("CTNS");
        header.createCell(8).setCellValue("TTL PRS");
        header.createCell(9).setCellValue("CTN START");
        header.createCell(10).setCellValue("CTN END");
    }

    private void populatePackingDetailRow(Row row) {
        row.createCell(1).setCellValue("JCD-401-1-01");
        row.createCell(2).setCellValue("TEST");
        row.createCell(3).setCellValue("ORDER-401");
        row.createCell(4).setCellValue("TEST");
        row.createCell(5).setCellValue(10);
        row.createCell(6).setCellValue(10);
        row.createCell(7).setCellValue(1);
        row.createCell(8).setCellValue(10);
        row.createCell(9).setCellValue("100");
        row.createCell(10).setCellValue("100");
    }
}
