package com.shoefactory.assistant.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shoefactory.assistant.entity.CustomerPriceConfig;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerPriceServiceTest {
    private CustomerPriceConfigMapper mapper;
    private CustomerPriceService service;
    private LambdaQueryWrapper<CustomerPriceConfig> query;

    @BeforeEach void prepare() {
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        for (var entity:List.of(CustomerPriceConfig.class,OrderRecord.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration,"test"),entity);
        }
        mapper = mock(CustomerPriceConfigMapper.class);
        var customers = mock(CustomerConfigService.class);
        var orders = mock(OrderRecordMapper.class);
        when(customers.list()).thenReturn(List.of());
        when(orders.selectList(any())).thenReturn(List.of());
        when(mapper.selectPage(any(Page.class),any())).thenAnswer(call->{
            query = call.getArgument(1);
            return call.getArgument(0);
        });
        service = new CustomerPriceService(mapper,customers,orders,mock(OrderRecordDetailMapper.class));
    }

    private Map<String,Object> queryValues() {
        query.getSqlSegment();
        return query.getParamNameValuePairs();
    }

    @Test void multipleStylesMatchExactFullNumbersTogetherWithCustomerAndPendingFilters() {
        service.list(2L," jcd-253-1-01, RV-253-1-01, jcd-253-1-01, ",true,1,20);
        String sql = query.getSqlSegment();
        assertTrue(sql.contains("customer_id ="));
        assertTrue(sql.contains("development_no IN"));
        assertTrue(sql.contains("shoe_price IS NULL"));
        assertFalse(sql.contains("LIKE"));
        assertFalse(sql.contains("updated_at"));
        assertEquals(3,queryValues().size());
        assertTrue(queryValues().containsValue(2L));
        assertTrue(queryValues().containsValue("JCD-253-1-01"));
        assertTrue(queryValues().containsValue("RV-253-1-01"));
    }

    @Test void optionsUseThreeLevelsWithFullLeafValuesAndNaturalNumericOrder() {
        when(mapper.selectList(any())).thenAnswer(call->{
            LambdaQueryWrapper<CustomerPriceConfig> optionsQuery = call.getArgument(0);
            optionsQuery.getSqlSegment();
            assertTrue(optionsQuery.getParamNameValuePairs().containsValue(2L));
            return List.of(price("JCD-253-10-02"),price("JCD-253-2-10"),price("JCD-253-2-02"),
                    price("RV-253-2-02"),price("JCD-253-2-02"),price(null));
        });
        var options = service.developmentOptions(2L);
        assertEquals(1,options.size());
        assertEquals("253",options.get(0).getLabel());
        var groups = options.get(0).getChildren();
        assertEquals(List.of("2","10"),groups.stream().map(option->option.getLabel()).toList());
        var leaves = groups.get(0).getChildren();
        assertEquals(3,leaves.size());
        assertTrue(leaves.stream().anyMatch(option->"JCD-253-2-02".equals(option.getValue()) && "JCD-253-2-02".equals(option.getLabel())));
        assertTrue(leaves.stream().anyMatch(option->"RV-253-2-02".equals(option.getValue()) && "RV-253-2-02".equals(option.getLabel())));
        assertTrue(leaves.stream().allMatch(option->option.getChildren()==null));
        assertEquals("JCD-253-10-02",groups.get(1).getChildren().get(0).getValue());
    }

    private CustomerPriceConfig price(String style) {
        var price = new CustomerPriceConfig();
        price.setDevelopmentNo(style);
        return price;
    }
}
