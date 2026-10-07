package com.shoefactory.assistant.service;

import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.QuotationResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** 导出保存时的报价快照；金额公式保留在 Excel 中，便于离线核对。 */
@Service
public class QuotationExcelService {
    private final QuotationImageStore images;
    public QuotationExcelService(QuotationImageStore images) { this.images = images; }

    public byte[] export(QuotationResponse quote) {
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("成本报价单");
            int[] widths = {16,30,12,10,14,16,28};
            for (int i=0;i<widths.length;i++) sheet.setColumnWidth(i,widths[i]*256);
            var regular = book.createCellStyle();
            regular.setWrapText(true); regular.setVerticalAlignment(VerticalAlignment.CENTER);
            regular.setBorderBottom(BorderStyle.THIN); regular.setBorderTop(BorderStyle.THIN);
            regular.setBorderLeft(BorderStyle.THIN); regular.setBorderRight(BorderStyle.THIN);
            var font = book.createFont(); font.setFontName("Microsoft YaHei"); font.setFontHeightInPoints((short)10); regular.setFont(font);
            var number = book.createCellStyle(); number.cloneStyleFrom(regular);
            number.setDataFormat(book.createDataFormat().getFormat("0.########"));
            var heading = book.createCellStyle(); heading.cloneStyleFrom(regular);
            var bold = book.createFont(); bold.setFontName("Microsoft YaHei"); bold.setBold(true); heading.setFont(bold);
            heading.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex()); heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var title = book.createCellStyle(); title.cloneStyleFrom(heading); title.setAlignment(HorizontalAlignment.CENTER);
            var titleFont = book.createFont(); titleFont.setFontName("Microsoft YaHei"); titleFont.setBold(true); titleFont.setFontHeightInPoints((short)18); title.setFont(titleFont);
            merged(sheet,0,0,6,"鞋款成本报价单",title).setHeightInPoints(34);
            merged(sheet,1,0,6,"版本 V"+quote.versionNo()+" · "+(quote.adoptedAt()==null?"已保存报价":"曾采用为客户单价"),regular);
            merged(sheet,2,0,4,"客户："+text(quote.customerName())+"    款号："+text(quote.developmentNo()),regular);
            merged(sheet,3,0,4,"楦号："+text(quote.lastNo())+"    日期："+text(quote.quotationDate()),regular);
            merged(sheet,4,0,4,"颜色 / 材料："+text(quote.colorMaterial()),regular);
            merged(sheet,5,0,4,"币种：人民币    金额单位：元 / 双",regular);
            if (quote.imageKey()!=null) {
                var path = images.require(quote.imageKey());
                int picture = book.addPicture(Files.readAllBytes(path),path.toString().endsWith(".png")?Workbook.PICTURE_TYPE_PNG:Workbook.PICTURE_TYPE_JPEG);
                var anchor = book.getCreationHelper().createClientAnchor();
                anchor.setCol1(5); anchor.setCol2(7); anchor.setRow1(2); anchor.setRow2(6);
                anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
                sheet.createDrawingPatriarch().createPicture(anchor,picture);
            }
            int row = 7;
            List<Integer> totals = new ArrayList<>();
            String[] categories = {"MATERIAL","FEE","OUTSOLE"};
            String[] labels = {"鞋面材料","包装与辅料","大底 / 中底 / 鞋跟"};
            for (int group=0;group<categories.length;group++) {
                boolean excluded = group==2 && !Boolean.TRUE.equals(quote.includeOutsole());
                merged(sheet,row++,0,6,labels[group]+(excluded?"（未计入本次报价）":""),heading);
                var header = sheet.createRow(row++);
                String[] names = {"部位","材料 / 工序","用量","单位","单价","金额","备注"};
                for (int c=0;c<7;c++) cell(header,c,names[c],heading);
                int start=row;
                var category=categories[group];
                var entries=quote.items().stream().filter(item->category.equals(item.getCategory())).toList();
                for (var item:entries) {
                    var line=sheet.createRow(row++); line.setHeightInPoints(28);
                    cell(line,0,item.getPartName(),regular); cell(line,1,item.getMaterialName(),regular);
                    cell(line,2,item.getQuantity(),number); cell(line,3,item.getUnit(),regular);
                    cell(line,4,item.getUnitPrice(),number);
                    var amount=cell(line,5,null,number);
                    amount.setCellFormula("IF(OR(C"+row+"=\"\",E"+row+"=\"\"),\"\",C"+row+"*E"+row+")");
                    cell(line,6,item.getRemark(),regular);
                }
                var subtotal=merged(sheet,row,0,4,excluded?"本次未计入":labels[group]+"小计",heading);
                var amount=cell(subtotal,5,null,number);
                if (excluded || entries.isEmpty() && group!=2) amount.setCellValue(0);
                else if (!entries.isEmpty()) amount.setCellFormula("IF(COUNT(F"+(start+1)+":F"+row+")="+entries.size()+",SUM(F"+(start+1)+":F"+row+"),\"\")");
                totals.add(++row);
            }
            var labor=merged(sheet,row,0,4,"加工费",heading); cell(labor,5,quote.laborCost(),number); totals.add(++row);
            var calculated=merged(sheet,row,0,4,"计算合计",heading);
            String refs=String.join(",",totals.stream().map(i->"F"+i).toList());
            cell(calculated,5,null,number).setCellFormula("IF(COUNT("+refs+")=4,SUM("+refs+"),\"\")");
            int calculatedRow=++row;
            var finalRow=merged(sheet,row++,0,4,"最终报价"+(Boolean.TRUE.equals(quote.manualPrice())?"（手动）":"（自动取整）"),heading);
            var finalCell=cell(finalRow,5,quote.finalPrice(),number);
            if (!Boolean.TRUE.equals(quote.manualPrice())) {
                String formula=switch(quote.roundingMode()) { case "CENT"->"ROUND(F"+calculatedRow+",2)"; case "ROUND"->"ROUND(F"+calculatedRow+",0)"; default->"ROUNDUP(F"+calculatedRow+",0)"; };
                finalCell.setCellFormula("IF(ISNUMBER(F"+calculatedRow+"),"+formula+",\"\")");
            }
            merged(sheet,row++,0,6,"说明：空白表示待填写；大底未计入时不包含底料成本。",regular);
            if (quote.note()!=null) merged(sheet,row++,0,6,"备注："+quote.note(),regular).setHeightInPoints(56);
            sheet.setRepeatingRows(new CellRangeAddress(0,1,-1,-1));
            sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
            sheet.getPrintSetup().setFitWidth((short)1); sheet.getPrintSetup().setFitHeight((short)0);
            sheet.setFitToPage(true); sheet.setHorizontallyCenter(true);
            sheet.setMargin(PageMargin.LEFT,0.25); sheet.setMargin(PageMargin.RIGHT,0.25);
            book.setPrintArea(0,0,6,0,row-1);
            book.getCreationHelper().createFormulaEvaluator().evaluateAll();
            book.write(output); return output.toByteArray();
        } catch (IOException ex) { throw new BusinessException("导出报价单失败",ex); }
    }
    private static String text(Object value) { return value==null || value.toString().isBlank()?"待填写":value.toString(); }
    private static Row merged(Sheet sheet,int row,int first,int last,String value,CellStyle style) {
        var line=sheet.getRow(row); if(line==null) line=sheet.createRow(row); line.setHeightInPoints(24);
        for(int c=first;c<=last;c++) cell(line,c,c==first?value:null,style);
        if(last>first) sheet.addMergedRegion(new CellRangeAddress(row,row,first,last));
        return line;
    }
    private static Cell cell(Row row,int col,Object value,CellStyle style) {
        var cell=row.createCell(col); cell.setCellStyle(style);
        if(value instanceof BigDecimal decimal) cell.setCellValue(decimal.doubleValue());
        else if(value!=null) cell.setCellValue(value.toString());
        return cell;
    }
}
