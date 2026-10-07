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
            assertEquals(24.595,totals.get("鞋面材料小计"),0.00000001);
            assertEquals(79.095,totals.get("计算合计"),0.00000001);
            assertEquals(80,totals.get("最终报价（自动取整）"),0.00000001);
            assertTrue(sheet.getRow(2).getCell(0).getStringCellValue().contains("195-13-23"));
        }
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
