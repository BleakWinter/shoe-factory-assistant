package com.shoefactory.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.QuotationResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.*;

/** 保存的报价快照按原 Excel 成本分析表导出，打印范围保留原表 32 行结构。 */
@Service
public class QuotationExcelService {
    private final QuotationImageStore images;
    public QuotationExcelService(QuotationImageStore images) { this.images = images; }

    public byte[] export(QuotationResponse quote) {
        var layout = new QuotationPrintLayout(quote.note());
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("成本分析表");
            for (int col = 0; col < 7; col++) sheet.setColumnWidth(col, (int)Math.round(layout.property("columns").get(col).asDouble()*256));
            var styles = styles(book, layout);
            line(sheet, 0, 1, layout, styles); merge(sheet, 0, 0, 0, 6);
            sheet.getRow(0).getCell(0).setCellValue(layout.template().path("title").asText());
            line(sheet, 1, 2, layout, styles); merge(sheet, 1, 1, 0, 1);
            sheet.getRow(1).getCell(0).setCellValue(layout.template().path("subtitle").asText());
            sheet.getRow(1).getCell(6).setCellValue(layout.property("newColor").asText());
            String[] labels = {"款号", "楦号", "客户", "日期"};
            String[] values = {quote.developmentNo(), quote.lastNo(), quote.customerName(),
                    quote.quotationDate() == null ? "" : quote.quotationDate().getYear()+"."+quote.quotationDate().getMonthValue()+"."+quote.quotationDate().getDayOfMonth()};
            for (int i = 0; i < 4; i++) {
                var row = line(sheet, 2+i, 3+i, layout, styles);
                value(row, 4, labels[i]); value(row, 5, values[i]); merge(sheet, 2+i, 2+i, 5, 6);
            }
            merge(sheet, 2, 5, 0, 3);
            if (quote.imageKey() != null) addImage(book, sheet, quote.imageKey(), layout);
            header(sheet, 6, 7, "headers", layout, styles);
            var materials = details(sheet, 7, layout.lines(quote.items(), "MATERIAL", 8), layout, styles);
            int materialTotal = 7+materials.rows();
            total(sheet, materialTotal, 18, "材料成本合计", layout, styles).setCellFormula(sum(materials.amountCells(), false));
            var fees = details(sheet, materialTotal+1, layout.lines(quote.items(), "FEE", 19), layout, styles);
            int laborRow = materialTotal+1+fees.rows();
            var labor = line(sheet, laborRow, 25, layout, styles);
            value(labor, 0, "加工费"); value(labor, 5, quote.laborCost()); merge(sheet, laborRow, laborRow, 3, 4);
            int finalRow = laborRow+1;
            var finalCell = total(sheet, finalRow, 26, "加工费合计", layout, styles);
            if (Boolean.TRUE.equals(quote.includeOutsole())) value(sheet.getRow(finalRow), 6, "含大底");
            var bottomHeading = line(sheet, finalRow+1, 27, layout, styles); value(bottomHeading, 0, "大底报价");
            header(sheet, finalRow+2, 28, "outsoleHeaders", layout, styles);
            var outsole = details(sheet, finalRow+3, layout.lines(quote.items(), "OUTSOLE", 29), layout, styles);
            int bottomTotal = finalRow+3+outsole.rows();
            total(sheet, bottomTotal, 32, "合计", layout, styles).setCellFormula(sum(outsole.amountCells(), true));
            var refs = new ArrayList<String>(); refs.add("F"+(materialTotal+1)); refs.addAll(fees.amountCells()); refs.add("F"+(laborRow+1));
            if (Boolean.TRUE.equals(quote.includeOutsole())) refs.add("F"+(bottomTotal+1));
            if (Boolean.TRUE.equals(quote.manualPrice())) value(sheet.getRow(finalRow), 5, quote.finalPrice());
            else {
                String cells = String.join(",", refs);
                String sum = "SUM("+cells+")";
                String rounded = switch (quote.roundingMode()) { case "CENT" -> "ROUND("+sum+",2)"; case "ROUND" -> "ROUND("+sum+",0)"; default -> "ROUNDUP("+sum+",0)"; };
                finalCell.setCellFormula("IF(COUNT("+cells+")="+refs.size()+","+rounded+",\"\")");
            }
            sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
            sheet.getPrintSetup().setLandscape(false);
            sheet.getPrintSetup().setScale((short)Math.round(layout.template().path("printPage").path("scale").asDouble()*100));
            sheet.setFitToPage(true);
            sheet.getPrintSetup().setFitWidth((short)1); sheet.getPrintSetup().setFitHeight((short)1);
            sheet.setMargin(PageMargin.LEFT, .7); sheet.setMargin(PageMargin.RIGHT, .7);
            sheet.setMargin(PageMargin.TOP, .75); sheet.setMargin(PageMargin.BOTTOM, .75);
            sheet.setMargin(PageMargin.HEADER, .3); sheet.setMargin(PageMargin.FOOTER, .3);
            book.setPrintArea(0, 0, 6, 0, bottomTotal);
            book.getCreationHelper().createFormulaEvaluator().evaluateAll();
            book.write(output); return output.toByteArray();
        } catch (IOException ex) { throw new BusinessException("导出报价单失败", ex); }
    }
    private static Map<Integer, CellStyle> styles(XSSFWorkbook book, QuotationPrintLayout layout) {
        var result = new HashMap<Integer, CellStyle>();
        var definitions = layout.template().path("styles");
        for (int i = 0; i < definitions.size(); i++) {
            JsonNode definition = definitions.get(i);
            var style = book.createCellStyle(); var font = book.createFont();
            font.setFontName("宋体"); font.setFontHeightInPoints((short)definition.path("fontSize").asInt());
            font.setBold(definition.path("bold").asBoolean());
            font.setColor(new XSSFColor(java.awt.Color.decode(definition.path("color").asText()), null));
            style.setFont(font); style.setVerticalAlignment(VerticalAlignment.CENTER); style.setShrinkToFit(true);
            style.setAlignment(switch (definition.path("align").asText()) { case "center" -> HorizontalAlignment.CENTER; case "right" -> HorizontalAlignment.RIGHT; default -> HorizontalAlignment.LEFT; });
            for (var side : definition.path("borders")) switch (side.asText()) {
                case "left" -> style.setBorderLeft(BorderStyle.THIN); case "right" -> style.setBorderRight(BorderStyle.THIN);
                case "top" -> style.setBorderTop(BorderStyle.THIN); case "bottom" -> style.setBorderBottom(BorderStyle.THIN);
            }
            style.setDataFormat(book.createDataFormat().getFormat(definition.path("numberFormat").asText()));
            result.put(i, style);
        }
        return result;
    }
    private static Row line(Sheet sheet, int rowIndex, int templateRow, QuotationPrintLayout layout, Map<Integer, CellStyle> styles) {
        var row = sheet.createRow(rowIndex); row.setHeightInPoints(layout.rowHeight(templateRow));
        for (int col = 0; col < 7; col++) {
            row.createCell(col).setCellStyle(styles.get(layout.styleIndex(templateRow, col)));
        }
        return row;
    }
    private static void header(Sheet sheet, int rowIndex, int templateRow, String key, QuotationPrintLayout layout, Map<Integer, CellStyle> styles) {
        var row = line(sheet, rowIndex, templateRow, layout, styles);
        for (int col = 0; col < 7; col++) value(row, col, layout.template().path(key).get(col).asText());
    }
    private record Details(int rows, List<String> amountCells) {}
    private static Details details(Sheet sheet, int start, List<QuotationPrintLayout.Line> lines, QuotationPrintLayout layout, Map<Integer, CellStyle> styles) {
        var amounts = new ArrayList<String>();
        for (int i = 0; i < lines.size(); i++) {
            var entry = lines.get(i); int rowNo = start+i+1;
            var row = line(sheet, start+i, entry.templateRow(), layout, styles); value(row, 0, entry.label());
            if (entry.item() == null) continue;
            var item = entry.item(); value(row, 1, item.getMaterialName()); value(row, 2, item.getQuantity());
            value(row, 3, item.getUnit()); value(row, 4, item.getUnitPrice()); value(row, 6, item.getRemark());
            row.getCell(5).setCellFormula("IF(OR(C"+rowNo+"=\"\",E"+rowNo+"=\"\"),\"\",C"+rowNo+"*E"+rowNo+")");
            amounts.add("F"+rowNo);
        }
        return new Details(lines.size(), amounts);
    }
    private static String sum(List<String> refs, boolean emptyIsBlank) {
        if (refs.isEmpty()) return emptyIsBlank ? "\"\"" : "0";
        String cells = String.join(",", refs);
        return "IF(COUNT("+cells+")="+refs.size()+",SUM("+cells+"),\"\")";
    }
    private static Cell total(Sheet sheet, int rowIndex, int templateRow, String label, QuotationPrintLayout layout, Map<Integer, CellStyle> styles) {
        var row = line(sheet, rowIndex, templateRow, layout, styles); value(row, 0, label); return row.getCell(5);
    }
    private static void value(Row row, int col, Object value) {
        if (value instanceof BigDecimal decimal) row.getCell(col).setCellValue(decimal.doubleValue());
        else if (value != null) row.getCell(col).setCellValue(value.toString());
    }
    private static void merge(Sheet sheet, int firstRow, int lastRow, int firstCol, int lastCol) {
        sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, firstCol, lastCol));
    }
    private void addImage(XSSFWorkbook book, Sheet sheet, String key, QuotationPrintLayout layout) throws IOException {
        var path = images.require(key); var bytes = Files.readAllBytes(path);
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        var box = layout.property("imageBox");
        double areaWidth = box.path("width").asDouble(), areaHeight = box.path("height").asDouble();
        double scale = Math.min(areaWidth/image.getWidth(), areaHeight/image.getHeight());
        double width = image.getWidth()*scale, height = image.getHeight()*scale;
        double x = box.path("left").asDouble()+(areaWidth-width)/2, y = box.path("top").asDouble()+(areaHeight-height)/2;
        for (int i = 1; i <= 2; i++) y += layout.rowHeight(i)*96/72;
        var anchor = book.getCreationHelper().createClientAnchor();
        position(anchor, x, y, true, layout); position(anchor, x+width, y+height, false, layout);
        anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
        int picture = book.addPicture(bytes, key.endsWith(".png") ? Workbook.PICTURE_TYPE_PNG : Workbook.PICTURE_TYPE_JPEG);
        sheet.createDrawingPatriarch().createPicture(anchor, picture);
    }
    private static void position(ClientAnchor anchor, double x, double y, boolean first, QuotationPrintLayout layout) {
        int col = 0, row = 0;
        var page = layout.template().path("printPage");
        double width = page.path("tableWidthMm").asDouble()*96/25.4/page.path("scale").asDouble();
        double units = 0; for(var value : layout.property("columns")) units += value.asDouble();
        while (col < 6 && x >= layout.property("columns").get(col).asDouble()/units*width) x -= layout.property("columns").get(col++).asDouble()/units*width;
        while (row < 31 && y >= layout.rowHeight(row+1)*96/72) y -= layout.rowHeight(++row)*96/72;
        if (first) { anchor.setCol1(col); anchor.setRow1(row); anchor.setDx1(Units.pixelToEMU((int)Math.round(x))); anchor.setDy1(Units.pixelToEMU((int)Math.round(y))); }
        else { anchor.setCol2(col); anchor.setRow2(row); anchor.setDx2(Units.pixelToEMU((int)Math.round(x))); anchor.setDy2(Units.pixelToEMU((int)Math.round(y))); }
    }
}
