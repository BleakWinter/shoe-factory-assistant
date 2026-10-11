package com.shoefactory.assistant.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.entity.CustomerConfig;
import com.shoefactory.assistant.entity.ShoeQuotation;
import com.shoefactory.assistant.mapper.*;
import com.shoefactory.assistant.util.FileStorageUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuotationEntryTest {
    private ShoeQuotationMapper quotations;
    private CustomerConfigService customers;
    private CustomerPriceService prices;
    private QuotationService service;
    private LambdaQueryWrapper<ShoeQuotation> query;
    private ShoeQuotation selected;

    @BeforeEach void prepare() {
        var configuration=new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration,"test"),ShoeQuotation.class);
        quotations=mock(ShoeQuotationMapper.class); customers=mock(CustomerConfigService.class); prices=mock(CustomerPriceService.class);
        when(customers.require(2L)).thenReturn(new CustomerConfig());
        when(quotations.selectOne(any())).thenAnswer(call->{ query=call.getArgument(0); return selected; });
        service=new QuotationService(quotations,mock(ShoeQuotationItemMapper.class),customers,prices,
                mock(QuotationImageStore.class),mock(OrderRecordDetailMapper.class),mock(OrderRecordMapper.class),mock(FileStorageUtil.class));
    }
    @Test void adoptedHistoricalVersionsRemainEligibleWithMostRecentAdoptionFirst() {
        selected=new ShoeQuotation(); selected.setId(10L); selected.setVersionNo(1);
        assertEquals(10L,service.forPriceConfig(2L," jcd－253-1-01 "));
        String sql=query.getSqlSegment();
        assertTrue(sql.contains("customer_id =")); assertTrue(sql.contains("development_no ="));
        assertFalse(sql.contains("LIKE")); assertFalse(sql.contains("NOT EXISTS"));
        assertTrue(sql.contains("ORDER BY adopted_at DESC,created_at DESC,id DESC"));
        assertTrue(sql.endsWith("LIMIT 1"));
        assertEquals(2,query.getParamNameValuePairs().size());
        assertTrue(query.getParamNameValuePairs().containsValue(2L));
        assertTrue(query.getParamNameValuePairs().containsValue("JCD-253-1-01"));
        verifyNoInteractions(prices);
    }
    @Test void unadoptedVersionsUseNewestCreationWithIdAsTieBreaker() {
        selected=new ShoeQuotation(); selected.setId(42L); selected.setVersionNo(2);
        assertEquals(42L,service.forPriceConfig(2L,"RV23155-1-01"));
        String sql=query.getSqlSegment();
        assertFalse(sql.contains("adopted_at IS NOT NULL"));
        assertTrue(sql.indexOf("created_at DESC")<sql.indexOf("id DESC"));
        assertTrue(query.getParamNameValuePairs().containsValue("RV23155-1-01"));
        verifyNoInteractions(prices);
    }
    @Test void noQuotationReturnsNullForANewDraft() {
        assertNull(service.forPriceConfig(2L,"JCD-202-7-02"));
        verify(quotations,never()).insert(any(ShoeQuotation.class)); verifyNoInteractions(prices);
    }
    @Test void blankStyleCannotOpenAnUnrelatedQuotation() {
        assertThrows(BusinessException.class,()->service.forPriceConfig(2L,"  "));
        verifyNoInteractions(quotations,prices);
    }
}
