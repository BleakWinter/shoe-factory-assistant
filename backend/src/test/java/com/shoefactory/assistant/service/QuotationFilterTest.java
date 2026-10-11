package com.shoefactory.assistant.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.mapper.*;
import com.shoefactory.assistant.util.FileStorageUtil;
import org.apache.ibatis.mapping.BoundSql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuotationFilterTest {
    private ShoeQuotationMapper mapper;
    private CustomerPriceService prices;
    private QuotationService service;
    private MybatisConfiguration configuration;

    @BeforeEach void prepare() {
        mapper = mock(ShoeQuotationMapper.class);
        prices = mock(CustomerPriceService.class);
        var customers = mock(CustomerConfigService.class);
        when(customers.list()).thenReturn(List.of());
        when(prices.prices()).thenReturn(Map.of());
        service = new QuotationService(mapper,mock(ShoeQuotationItemMapper.class),customers,prices,
                mock(QuotationImageStore.class),mock(OrderRecordDetailMapper.class),mock(OrderRecordMapper.class),mock(FileStorageUtil.class));
        configuration = new MybatisConfiguration();
        configuration.addMapper(ShoeQuotationMapper.class);
    }

    private BoundSql query(String method,Long customerId,List<String> styles,LocalDate startDate,LocalDate endDate) {
        Map<String,Object> params = new HashMap<>();
        params.put("customerId",customerId);params.put("style","");params.put("styles",styles);
        params.put("startDate",startDate);params.put("endDate",endDate);
        return configuration.getMappedStatement(ShoeQuotationMapper.class.getName()+"."+method).getBoundSql(params);
    }

    @Test void actualMapperFiltersLatestVersionsByExactStylesAndInclusiveQuotationDates() {
        var date = LocalDate.of(2026,10,11);
        var bound = query("listLatest",2L,List.of("JCD-253-1-01","RV-253-1-01"),date,date);
        String sql = bound.getSql().replaceAll("\\s+"," ");
        assertTrue(sql.contains("q.development_no IN ( ? , ? )"),sql);
        assertFalse(sql.contains("LIKE"));
        assertTrue(sql.contains("q.quotation_date>=?"),sql);
        assertTrue(sql.contains("q.quotation_date<=?"),sql);
        assertFalse(sql.contains("q.created_at>="));
        assertTrue(sql.contains("WHERE newer.version_group=q.version_group AND newer.version_no>q.version_no)"),sql);
        // 最新版本判断不受日期筛选影响，不能用符合日期的旧版本代替最新版本。
        var inner = sql.substring(sql.indexOf("WHERE newer."),sql.indexOf(")"));
        assertFalse(inner.contains("quotation_date"));
        assertEquals(5,bound.getParameterMappings().size());
        assertEquals("JCD-253-1-01",bound.getAdditionalParameter(bound.getParameterMappings().get(1).getProperty()));
        assertEquals("RV-253-1-01",bound.getAdditionalParameter(bound.getParameterMappings().get(2).getProperty()));
    }

    @Test void clearedDatesAndStylesRemoveRestrictionsAndOneDateBoundWorks() {
        String all = query("listLatest",null,List.of(),null,null).getSql();
        assertFalse(all.contains("quotation_date"));assertFalse(all.contains("development_no IN"));
        assertFalse(all.contains("q.customer_id="));
        var date = LocalDate.of(2026,10,11);
        String start = query("listLatest",null,List.of(),date,null).getSql();
        assertTrue(start.contains("q.quotation_date>=?"));assertFalse(start.contains("q.quotation_date<=?"));
        String end = query("listLatest",null,List.of(),null,date).getSql();
        assertTrue(end.contains("q.quotation_date<=?"));assertFalse(end.contains("q.quotation_date>=?"));
    }

    @Test void styleOptionsComeFromLatestQuotationsForTheCustomerAcrossAllDates() {
        when(mapper.listLatestDevelopmentNos(2L)).thenReturn(List.of("JCD-253-1-01","JCD-253-1-02","JCD-253-1-01"));
        var options = service.developmentOptions(2L);
        assertEquals("253",options.get(0).getLabel());
        assertEquals(List.of("JCD-253-1-01","JCD-253-1-02"),options.get(0).getChildren().get(0).getChildren()
                .stream().map(option->option.getValue()).toList());
        verifyNoInteractions(prices);
        String sql = query("listLatestDevelopmentNos",2L,List.of(),null,null).getSql();
        assertTrue(sql.contains("FROM shoe_quotation"));assertTrue(sql.contains("q.customer_id=?"));
        assertTrue(sql.contains("newer.version_no>q.version_no"));assertFalse(sql.contains("quotation_date"));
    }

    @Test void listNormalizesMultipleFullStylesBeforePassingThemToMapper() {
        var date = LocalDate.of(2026,10,11);
        when(mapper.listLatest(any(),any(),anyString(),anyList(),any(),any())).thenAnswer(call->{
            assertEquals(2L,call.<Long>getArgument(1));assertEquals("",call.getArgument(2));
            assertEquals(List.of("JCD-253-1-01","RV-253-1-01"),call.getArgument(3));
            assertEquals(date,call.getArgument(4));assertEquals(date,call.getArgument(5));
            return call.getArgument(0);
        });
        assertEquals(0,service.list(2L,null," jcd-253-1-01, RV-253-1-01, jcd-253-1-01, ",date,date,1,20).getTotal());
    }

    @Test void reversedDatesDoNotQueryOrWritePrices() {
        var date = LocalDate.of(2026,10,11);
        assertThrows(BusinessException.class,()->service.list(null,null,null,date,date.minusDays(1),1,20));
        verifyNoInteractions(mapper,prices);
    }
}
