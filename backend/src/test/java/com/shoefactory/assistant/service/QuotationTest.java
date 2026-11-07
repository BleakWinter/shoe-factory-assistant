package com.shoefactory.assistant.service;

import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.config.FileStorageProperties;
import com.shoefactory.assistant.dto.QuotationSaveRequest;
import com.shoefactory.assistant.entity.*;
import com.shoefactory.assistant.mapper.*;
import com.shoefactory.assistant.util.FileStorageUtil;
import jakarta.validation.Validation;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuotationTest {
    @TempDir Path temp;
    ShoeQuotationMapper quotations;
    ShoeQuotationItemMapper items;
    CustomerConfigService customers;
    CustomerPriceService prices;
    QuotationImageStore images;
    QuotationService service;
    Map<Long,ShoeQuotation> headers;
    List<ShoeQuotationItem> rows;

    @BeforeEach void prepare() {
        quotations=mock(ShoeQuotationMapper.class); items=mock(ShoeQuotationItemMapper.class);
        customers=mock(CustomerConfigService.class); prices=mock(CustomerPriceService.class);
        headers=new LinkedHashMap<>(); rows=new ArrayList<>();
        var properties=new FileStorageProperties(); properties.setRootPath(temp.toString());
        images=new QuotationImageStore(properties);
        var customer=new CustomerConfig(); customer.setId(2L); customer.setName("宝翔");
        when(customers.require(2L)).thenReturn(customer);
        when(quotations.insert(any(ShoeQuotation.class))).thenAnswer(call->{
            ShoeQuotation quote=call.getArgument(0); quote.setId((long)headers.size()+1); headers.put(quote.getId(),quote); return 1;
        });
        when(quotations.selectById(anyLong())).thenAnswer(call->headers.get(call.getArgument(0)));
        when(quotations.selectList(any())).thenAnswer(call->headers.values().stream().sorted(Comparator.comparing(ShoeQuotation::getVersionNo).reversed()).toList());
        when(quotations.lockLatest(anyString())).thenAnswer(call->headers.values().stream().max(Comparator.comparing(ShoeQuotation::getVersionNo)).orElse(null));
        when(quotations.lockById(anyLong())).thenAnswer(call->headers.get(call.getArgument(0)));
        when(items.insert(any(ShoeQuotationItem.class))).thenAnswer(call->{ rows.add(call.getArgument(0)); return 1; });
        when(items.selectList(any())).thenAnswer(call->{
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?> wrapper=call.getArgument(0);
            wrapper.getSqlSegment(); Object id=wrapper.getParamNameValuePairs().values().iterator().next();
            return rows.stream().filter(row->row.getQuotationId().equals(id)).toList();
        });
        // MyBatis 的 Lambda 元信息通常由 mapper 初始化；此处不启动数据库。
        initializeTable(ShoeQuotationItem.class);
        initializeTable(ShoeQuotation.class);
        service=new QuotationService(quotations,items,customers,prices,images,mock(OrderRecordDetailMapper.class),mock(OrderRecordMapper.class),new FileStorageUtil(properties));
    }
    private static BigDecimal decimal(String value) { return value==null?null:new BigDecimal(value); }
    private static void initializeTable(Class<?> entity) {
        var configuration=new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(configuration,"test"),entity);
    }
    private static QuotationSaveRequest.Item item(String category,String quantity,String price) {
        return new QuotationSaveRequest.Item(category,"部位","材料",decimal(quantity),"双",decimal(price),null);
    }
    private QuotationSaveRequest request(Integer expected,boolean include,String mode,boolean manual,String finalPrice,List<QuotationSaveRequest.Item> rows) {
        return new QuotationSaveRequest(expected,2L,"195-13-23","195","杏色超纤",LocalDate.of(2026,9,12),null,null,
                decimal("48"),include,mode,manual,decimal(finalPrice),"报价条件",rows);
    }
    private QuotationSaveRequest sample(Integer expected,String mode,boolean manual,String finalPrice) {
        return request(expected,false,mode,manual,finalPrice,List.of(item("MATERIAL","1","24.595"),item("FEE","1","1.5"),item("FEE","1","5"),item("OUTSOLE",null,null)));
    }
    @Test void originalWorkbookExampleAndRoundingKeepPrecision() {
        var quote=QuotationCalculator.calculate(sample(null,"CEIL",false,null));
        assertEquals(0,quote.calculated().compareTo(decimal("79.095")));
        assertEquals(decimal("80.00"),quote.finalPrice());
        assertEquals(decimal("79.10"),QuotationCalculator.calculate(sample(null,"CENT",false,null)).finalPrice());
        assertEquals(decimal("79.00"),QuotationCalculator.calculate(sample(null,"ROUND",false,null)).finalPrice());
        assertEquals(decimal("72.55"),QuotationCalculator.calculate(sample(null,"CEIL",true,"72.55")).finalPrice());
        var precise=QuotationCalculator.calculate(request(null,false,"CENT",false,null,List.of(item("MATERIAL","0.1234","0.5678"))));
        assertEquals(decimal("0.07006652"),precise.itemAmounts().get(0));
    }
    @Test void missingValuesNeverBecomeZeroAndInactiveOutsoleDoesNotBlock() {
        assertNotNull(QuotationCalculator.calculate(sample(null,"CEIL",false,null)).calculated());
        assertNull(QuotationCalculator.calculate(request(null,true,"CEIL",false,null,List.of(item("OUTSOLE",null,null)))).calculated());
        assertNull(QuotationCalculator.calculate(request(null,false,"CEIL",false,null,List.of(item("MATERIAL",null,"0")))).finalPrice());
        assertEquals(decimal("48.00"),QuotationCalculator.calculate(request(null,false,"CEIL",false,null,List.of(item("MATERIAL","0","0")))).finalPrice());
        assertNull(QuotationCalculator.calculate(request(null,true,"CEIL",false,null,List.of())).calculated());
    }
    @Test void validationRejectsNegativeAndExcessPrecision() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            assertFalse(factory.getValidator().validate(request(null,false,"CEIL",false,null,List.of(item("MATERIAL","-1","1")))).isEmpty());
            assertFalse(factory.getValidator().validate(request(null,false,"CEIL",false,null,List.of(item("MATERIAL","1.12345","1")))).isEmpty());
        }
        assertThrows(BusinessException.class,()->QuotationCalculator.calculate(sample(null,"CEIL",true,"1.001")));
        assertThrows(BusinessException.class,()->QuotationCalculator.calculate(sample(null,"CEIL",true,"100000000")));
    }
    @Test void savingAndVersioningNeverChangeCustomerPriceOrOldSnapshot() {
        var first=service.create(sample(null,"CEIL",false,null));
        var second=service.createVersion(first.id(),sample(1,"CENT",false,null));
        assertEquals(2,second.versionNo()); assertEquals(decimal("79.10"),second.finalPrice());
        assertEquals(decimal("80.00"),service.get(first.id()).finalPrice());
        assertEquals(2,service.get(first.id()).latestVersionNo());
        verify(prices,never()).adoptQuotationPrice(any(),any(),any());
        assertThrows(BusinessException.class,()->service.createVersion(first.id(),sample(1,"CEIL",false,null)));
        assertEquals(2,headers.size());
    }
    @Test void adoptingOnlyLatestCompleteVersionWritesExactCustomerAndStyle() {
        var first=service.create(sample(null,"CEIL",false,null));
        var second=service.createVersion(first.id(),sample(1,"CENT",false,null));
        assertThrows(BusinessException.class,()->service.adopt(first.id()));
        verify(prices,never()).adoptQuotationPrice(any(),any(),any());
        service.adopt(second.id());
        verify(prices).adoptQuotationPrice(2L,"195-13-23",decimal("79.10"));
    }
    @Test void incompleteDraftCanSaveButCannotAdoptEvenWithManualFinal() {
        var draft=request(null,false,"CEIL",true,"80",List.of(item("MATERIAL",null,null)));
        var quote=service.create(draft);
        assertNull(quote.calculatedAmount()); assertEquals(decimal("80.00"),quote.finalPrice());
        assertThrows(BusinessException.class,()->service.adopt(quote.id()));
        verify(prices,never()).adoptQuotationPrice(any(),any(),any());
    }
    @Test void quotedIdentityCannotBeChangedAcrossVersions() {
        var first=service.create(sample(null,"CEIL",false,null));
        var sample=sample(1,"CEIL",false,null);
        var changed=new QuotationSaveRequest(1,2L,"another-style",sample.lastNo(),sample.colorMaterial(),sample.quotationDate(),null,null,sample.laborCost(),false,"CEIL",false,null,null,sample.items());
        assertThrows(BusinessException.class,()->service.createVersion(first.id(),changed));
        assertEquals(1,headers.size());
    }
    @Test void imageStoreChecksContentAndRejectsTraversal() throws Exception {
        var output=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",output);
        var uploaded=images.upload(new MockMultipartFile("file","shoe.png","image/png",output.toByteArray()));
        assertTrue(java.nio.file.Files.exists(images.require(uploaded.imageKey())));
        assertArrayEquals(output.toByteArray(),java.nio.file.Files.readAllBytes(images.require(uploaded.imageKey())));
        assertThrows(BusinessException.class,()->images.upload(new MockMultipartFile("file","fake.png","image/png","not an image".getBytes())));
        assertThrows(BusinessException.class,()->images.require("../../application.yml"));
        assertThrows(BusinessException.class,()->images.require("2026/10/00000000-0000-0000-0000-000000000000.png"));
    }
    @Test void exportedExcelHasEvaluatedFormulasAndSavedSnapshot() throws Exception {
        var quote=service.create(sample(null,"CEIL",false,null));
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(new QuotationExcelService(images).export(quote)))) {
            var sheet=workbook.getSheetAt(0); var evaluator=workbook.getCreationHelper().createFormulaEvaluator();
            var totals=new HashMap<String,Double>();
            for(var row:sheet) { var label=row.getCell(0); var amount=row.getCell(5);
                if(label!=null && label.getCellType()==CellType.STRING && amount!=null) {
                    var result=evaluator.evaluate(amount);
                    if(result!=null && result.getCellType()==CellType.NUMERIC) totals.put(label.getStringCellValue(),result.getNumberValue());
                }
            }
            assertEquals(24.595,totals.get("材料成本合计"),0.00000001);
            assertEquals(80,totals.get("加工费合计"),0.00000001);
            assertEquals("195-13-23",sheet.getRow(2).getCell(5).getStringCellValue());
            assertEquals("清化鞋业成本分析表",sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals(31,sheet.getLastRowNum());
            assertTrue(sheet.getMergedRegions().stream().anyMatch(range -> "A3:D6".equals(range.formatAsString())));
            assertTrue(sheet.getMergedRegions().stream().anyMatch(range -> "D25:E25".equals(range.formatAsString())));
            assertEquals(24,sheet.getRow(7).getHeightInPoints());
            assertEquals(30,sheet.getRow(2).getHeightInPoints());
            assertEquals(Math.round(11.6640625*256),sheet.getColumnWidth(0));
            assertEquals(91,sheet.getPrintSetup().getScale());
            assertTrue(sheet.getFitToPage());
            assertEquals(1,sheet.getPrintSetup().getFitWidth());
            assertEquals(1,sheet.getPrintSetup().getFitHeight());
            assertFalse(sheet.getPrintSetup().getLandscape());
            assertEquals(.35,sheet.getMargin(org.apache.poi.ss.usermodel.PageMargin.LEFT),0.00000001);
            assertEquals(.35,sheet.getMargin(org.apache.poi.ss.usermodel.PageMargin.RIGHT),0.00000001);
            assertEquals(.375,sheet.getMargin(org.apache.poi.ss.usermodel.PageMargin.TOP),0.00000001);
            assertEquals(.375,sheet.getMargin(org.apache.poi.ss.usermodel.PageMargin.BOTTOM),0.00000001);
            assertEquals("宋体",workbook.getFontAt(sheet.getRow(0).getCell(0).getCellStyle().getFontIndex()).getFontName());
            assertEquals(22,workbook.getFontAt(sheet.getRow(0).getCell(0).getCellStyle().getFontIndex()).getFontHeightInPoints());
            assertEquals("0.00_);[Red]\\(0.00\\)",sheet.getRow(17).getCell(5).getCellStyle().getDataFormatString());
            assertEquals(CellType.STRING,evaluator.evaluate(sheet.getRow(31).getCell(5)).getCellType());
        }
    }
    @Test void exportedTemplateKeepsEntryOrderAndLeavesUnusedRowsBlank() throws Exception {
        var input=sample(null,"CENT",false,null);
        var lines=new ArrayList<QuotationSaveRequest.Item>();
        lines.add(new QuotationSaveRequest.Item("MATERIAL","鞋身里","杏PU",decimal("0.04"),"码",decimal("26"),null));
        lines.add(new QuotationSaveRequest.Item("MATERIAL","面料","羊皮",decimal("1.25"),"尺",decimal("17.5"),null));
        lines.add(new QuotationSaveRequest.Item("MATERIAL","鞋身里","另一种里料",decimal("0.04"),"码",decimal("26"),null));
        lines.add(new QuotationSaveRequest.Item("OUTSOLE","鞋跟","跟",decimal("1"),"双",decimal("2"),null));
        lines.add(new QuotationSaveRequest.Item("FEE","包跟","工序",decimal("1"),"双",decimal("1.5"),null));
        var imported=new QuotationSaveRequest(null,2L,input.developmentNo(),input.lastNo(),input.colorMaterial(),input.quotationDate(),null,null,
                input.laborCost(),false,"CENT",false,null,"Excel 导入：报价2026-9-12.xlsx；工作表：195-13-23；版本：V1",lines);
        var quote=service.create(imported);
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(new QuotationExcelService(images).export(quote)))) {
            var sheet=book.getSheetAt(0);
            assertEquals(31,sheet.getLastRowNum());
            assertEquals("鞋身里",sheet.getRow(7).getCell(0).getStringCellValue());
            assertEquals("杏PU",sheet.getRow(7).getCell(1).getStringCellValue());
            assertEquals("面料",sheet.getRow(8).getCell(0).getStringCellValue());
            assertEquals("羊皮",sheet.getRow(8).getCell(1).getStringCellValue());
            assertEquals("鞋身里",sheet.getRow(9).getCell(0).getStringCellValue());
            assertEquals("另一种里料",sheet.getRow(9).getCell(1).getStringCellValue());
            assertEquals("包跟",sheet.getRow(18).getCell(0).getStringCellValue());
            assertEquals("鞋跟",sheet.getRow(28).getCell(0).getStringCellValue());
            for(int row: new int[]{10,11,12,13,14,15,16,19,20,21,22,23,29,30}) {
                for(int col=0;col<7;col++) assertEquals("",new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(sheet.getRow(row).getCell(col)));
            }
            assertEquals(23.955,book.getCreationHelper().createFormulaEvaluator().evaluate(sheet.getRow(17).getCell(5)).getNumberValue(),1e-8);
        }
    }
    @Test void fullyFilledTemplateKeepsAllThirtyTwoRowsAndAmounts() throws Exception {
        var lines=new ArrayList<QuotationSaveRequest.Item>();
        for(var group: Map.of("MATERIAL",10,"FEE",6,"OUTSOLE",3).entrySet()) {
            for(int i=0;i<group.getValue();i++) lines.add(item(group.getKey(),"1","1"));
        }
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(new QuotationExcelService(images).export(service.create(request(null,true,"CEIL",false,null,lines)))))) {
            var sheet=book.getSheetAt(0); var evaluator=book.getCreationHelper().createFormulaEvaluator();
            assertEquals(31,sheet.getLastRowNum());
            assertEquals(10,evaluator.evaluate(sheet.getRow(17).getCell(5)).getNumberValue());
            assertEquals(67,evaluator.evaluate(sheet.getRow(25).getCell(5)).getNumberValue());
            assertEquals(3,evaluator.evaluate(sheet.getRow(31).getCell(5)).getNumberValue());
        }
    }
    @Test void exportExpandsEachSectionWithoutDroppingItems() throws Exception {
        for(var group: Map.of("MATERIAL",10,"FEE",6,"OUTSOLE",3).entrySet()) {
            var lines=new ArrayList<QuotationSaveRequest.Item>();
            for(int i=0;i<=group.getValue();i++) lines.add(item(group.getKey(),"1","1"));
            var quote=service.create(request(null,"OUTSOLE".equals(group.getKey()),"CEIL",false,null,lines));
            try(var book=new XSSFWorkbook(new ByteArrayInputStream(new QuotationExcelService(images).export(quote)))) {
                var sheet=book.getSheetAt(0); var evaluator=book.getCreationHelper().createFormulaEvaluator();
                assertEquals(32,sheet.getLastRowNum());
                int start=switch(group.getKey()) { case "MATERIAL" -> 7; case "FEE" -> 18; default -> 28; };
                for(int index=0;index<=group.getValue();index++) {
                    assertEquals("材料",sheet.getRow(start+index).getCell(1).getStringCellValue());
                    assertEquals(1,evaluator.evaluate(sheet.getRow(start+index).getCell(5)).getNumberValue());
                }
                assertEquals(quote.finalPrice().doubleValue(),evaluator.evaluate(sheet.getRow(group.getKey().equals("OUTSOLE")?25:26).getCell(5)).getNumberValue());
                assertEquals("成本分析表!$A$1:$G$33",book.getPrintArea(0));
            }
            assertEquals(group.getValue()+1,service.get(quote.id()).items().size());
        }
    }
    @Test void expandedSectionsKeepTotalsStylesAndPrintAllRows() throws Exception {
        var lines=new ArrayList<QuotationSaveRequest.Item>();
        for(var group: Map.of("MATERIAL",12,"FEE",8,"OUTSOLE",5).entrySet()) {
            for(int i=0;i<group.getValue();i++) {
                lines.add(new QuotationSaveRequest.Item(group.getKey(),group.getKey()+i,"材料"+i,decimal("1"),"双",decimal("1"),null));
            }
        }
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(new QuotationExcelService(images).export(service.create(request(null,true,"CEIL",false,null,lines)))))) {
            var sheet=book.getSheetAt(0); var evaluator=book.getCreationHelper().createFormulaEvaluator();
            assertEquals(37,sheet.getLastRowNum());
            assertEquals("MATERIAL11",sheet.getRow(18).getCell(0).getStringCellValue());
            assertEquals("FEE7",sheet.getRow(27).getCell(0).getStringCellValue());
            assertEquals("OUTSOLE4",sheet.getRow(36).getCell(0).getStringCellValue());
            assertEquals(sheet.getRow(16).getCell(5).getCellStyle(),sheet.getRow(18).getCell(5).getCellStyle());
            assertEquals(12,evaluator.evaluate(sheet.getRow(19).getCell(5)).getNumberValue());
            assertEquals(73,evaluator.evaluate(sheet.getRow(29).getCell(5)).getNumberValue());
            assertEquals(5,evaluator.evaluate(sheet.getRow(37).getCell(5)).getNumberValue());
            assertEquals("成本分析表!$A$1:$G$38",book.getPrintArea(0));
            assertEquals(0,sheet.getPrintSetup().getFitHeight());
            assertEquals("1:7",sheet.getRepeatingRows().formatAsString());
        }
    }
    @Test void exportIncludesOutsoleWithoutChangingBlankAndManualPriceSemantics() throws Exception {
        var complete=request(null,true,"CENT",false,null,List.of(item("MATERIAL","1","1.2345"),item("OUTSOLE","1","2")));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(new QuotationExcelService(images).export(service.create(complete))))) {
            var sheet=book.getSheetAt(0); var evaluator=book.getCreationHelper().createFormulaEvaluator();
            assertEquals(51.23,evaluator.evaluate(sheet.getRow(25).getCell(5)).getNumberValue());
            assertEquals(2,evaluator.evaluate(sheet.getRow(31).getCell(5)).getNumberValue());
            assertEquals("含大底",sheet.getRow(25).getCell(6).getStringCellValue());
        }
        var incomplete=request(null,false,"CEIL",true,"72.55",List.of(item("MATERIAL",null,null)));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(new QuotationExcelService(images).export(service.create(incomplete))))) {
            var sheet=book.getSheetAt(0); var evaluator=book.getCreationHelper().createFormulaEvaluator();
            assertEquals("",evaluator.evaluate(sheet.getRow(17).getCell(5)).getStringValue());
            assertEquals(72.55,sheet.getRow(25).getCell(5).getNumericCellValue());
        }
    }
    @Test void exportedPictureStaysCenteredInsideMergedHeaderAndKeepsBytes() throws Exception {
        var output=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(400,200,BufferedImage.TYPE_INT_RGB),"png",output);
        var uploaded=images.upload(new MockMultipartFile("file","shoe.png","image/png",output.toByteArray()));
        var input=sample(null,"CEIL",false,null);
        var request=new QuotationSaveRequest(null,input.customerId(),input.developmentNo(),input.lastNo(),input.colorMaterial(),input.quotationDate(),uploaded.imageKey(),null,
                input.laborCost(),input.includeOutsole(),input.roundingMode(),input.manualPrice(),input.finalPrice(),input.note(),input.items());
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(new QuotationExcelService(images).export(service.create(request))))) {
            assertArrayEquals(output.toByteArray(),book.getAllPictures().get(0).getData());
            var picture=(org.apache.poi.xssf.usermodel.XSSFPicture)book.getSheetAt(0).getDrawingPatriarch().getShapes().get(0);
            var anchor=picture.getClientAnchor();
            assertEquals(2,anchor.getRow1()); assertTrue(anchor.getRow2()<=6);
            assertTrue(anchor.getCol1()<4); assertTrue(anchor.getCol2()<=4);
            assertCenteredPicture(book.getSheetAt(0),anchor);
        }
        var imported=new QuotationSaveRequest(null,input.customerId(),"195-25-03",input.lastNo(),input.colorMaterial(),input.quotationDate(),uploaded.imageKey(),null,
                input.laborCost(),input.includeOutsole(),input.roundingMode(),input.manualPrice(),input.finalPrice(),"Excel 导入：报价2026-9-12.xlsx；工作表：195-25-03；版本：V1",input.items());
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(new QuotationExcelService(images).export(service.create(imported))))) {
            var picture=(org.apache.poi.xssf.usermodel.XSSFPicture)book.getSheetAt(0).getDrawingPatriarch().getShapes().get(0);
            assertEquals(0,picture.getClientAnchor().getCol1());
            assertCenteredPicture(book.getSheetAt(0),picture.getClientAnchor());
        }
    }
    private static void assertCenteredPicture(org.apache.poi.ss.usermodel.Sheet sheet,org.apache.poi.ss.usermodel.ClientAnchor anchor) {
        double left = anchor.getDx1()/(double)org.apache.poi.util.Units.EMU_PER_PIXEL;
        double right = anchor.getDx2()/(double)org.apache.poi.util.Units.EMU_PER_PIXEL;
        for (int col = 0; col < anchor.getCol1(); col++) left += sheet.getColumnWidthInPixels(col);
        for (int col = 0; col < anchor.getCol2(); col++) right += sheet.getColumnWidthInPixels(col);
        double areaWidth = 0;
        for (int col = 0; col < 4; col++) areaWidth += sheet.getColumnWidthInPixels(col);
        assertEquals(areaWidth,left+right,2);
        assertTrue(left >= 8); assertTrue(right <= areaWidth-8);
        double top = anchor.getDy1()/(double)org.apache.poi.util.Units.EMU_PER_PIXEL;
        double bottom = anchor.getDy2()/(double)org.apache.poi.util.Units.EMU_PER_PIXEL;
        for (int row = 2; row < anchor.getRow1(); row++) top += sheet.getRow(row).getHeightInPoints()*96/72;
        for (int row = 2; row < anchor.getRow2(); row++) bottom += sheet.getRow(row).getHeightInPoints()*96/72;
        assertEquals(160,top+bottom,2);
        assertEquals(2,(right-left)/(bottom-top),0.02);
    }
    @Test void adoptionPriceIsScopedToCustomerAndNormalizedStyle() {
        initializeTable(CustomerPriceConfig.class);
        var mapper=mock(CustomerPriceConfigMapper.class);
        var priceService=new CustomerPriceService(mapper,customers,mock(OrderRecordMapper.class),mock(OrderRecordDetailMapper.class));
        priceService.adoptQuotationPrice(2L,"  a－123  ",decimal("80.00"));
        verify(mapper).ensurePendingPrice(2L,"A-123");
        var capture=org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(mapper).update(isNull(),capture.capture());
        var update=capture.getValue();
        String sql=update.getSqlSegment();
        assertTrue(sql.contains("customer_id")); assertTrue(sql.contains("development_no"));
        assertTrue(update.getParamNameValuePairs().containsValue(2L));
        assertTrue(update.getParamNameValuePairs().containsValue("A-123"));
        assertTrue(update.getParamNameValuePairs().containsValue(decimal("80.00")));
    }
}
