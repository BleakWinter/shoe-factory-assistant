package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shoefactory.assistant.dto.OrderRecordResponse;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.entity.OrderPackingDetail;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import com.shoefactory.assistant.mapper.OrderPackingDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private OrderRecordMapper orders;
    @Mock private OrderRecordDetailMapper details;
    @Mock private OrderPackingDetailMapper packing;
    @InjectMocks private OrderServiceImpl service;

    private final List<OrderRecord> records = new ArrayList<>();

    @BeforeAll
    static void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, OrderRecord.class);
        TableInfoHelper.initTableInfo(assistant, OrderRecordDetail.class);
        TableInfoHelper.initTableInfo(assistant, OrderPackingDetail.class);
    }

    @BeforeEach
    void setUp() {
        when(orders.selectList(any())).thenReturn(records);
        when(details.selectList(any())).thenReturn(List.of());
        when(packing.selectList(any())).thenReturn(List.of());
    }

    @Test
    void filtersByTheNumericLeafReturnedByTheDevelopmentNumberSelector() {
        records.add(order(1L, "A", "JCD-172-1-02"));
        records.add(order(2L, "B", "JCD-1172-1-02"));
        records.add(order(3L, "C", "JCD-172-1-020"));
        records.add(order(4L, "D", "JCD-205-1-02"));
        records.add(order(5L, "E", null));

        var parent = service.listDevelopmentNoOptions().stream()
                .filter(option -> "172".equals(option.getValue()))
                .findFirst().orElseThrow();
        String selected = parent.getChildren().get(0).getChildren().get(0).getValue();
        assertThat(selected).isEqualTo("172-1-02");

        var result = list(selected);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).extracting(OrderRecordResponse::getId).containsExactly(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"172－1－02", " 172 - 1 - 02 ", "jcd-172-1-02"})
    void normalizesSearchHyphensWhitespaceAndLetterCase(String selected) {
        records.add(order(1L, "A", "JCD－172－1－02（新颜色待确认）"));
        records.add(order(2L, "B", "JCD-205-3-17"));

        assertThat(list(selected).getRecords()).extracting(OrderRecordResponse::getId).containsExactly(1L);
    }

    @Test
    void combinesSelectedNumbersAndPaginatesAfterFiltering() {
        records.add(order(1L, "A", "JCD-172-1-02,JCD-162-1-01"));
        records.add(order(2L, "B", "JCD-205-3-17"));
        records.add(order(3L, "C", "JCD-395-3-01"));

        var first = service.listOrders(null, "172-1-02,205-3-17", null, null, null, null, 1, 1);
        var second = service.listOrders(null, "172-1-02,205-3-17", null, null, null, null, 2, 1);

        assertThat(first.getTotal()).isEqualTo(2);
        assertThat(second.getTotal()).isEqualTo(2);
        assertThat(first.getRecords()).extracting(OrderRecordResponse::getId).containsExactly(2L);
        assertThat(second.getRecords()).extracting(OrderRecordResponse::getId).containsExactly(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"新颜色待确认", "!!!", "999-1-01"})
    void invalidOrUnknownSelectionsDoNotMatchEveryOrder(String selected) {
        records.add(order(1L, "A", "JCD-172-1-02"));
        records.add(order(2L, "B", null));

        var result = list(selected);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
    }

    @Test
    void anInvalidSelectionDoesNotBroadenOtherSelections() {
        records.add(order(1L, "A", "JCD-172-1-02"));
        records.add(order(2L, "B", "JCD-205-3-17"));

        assertThat(list("172-1-02,!!!").getRecords())
                .extracting(OrderRecordResponse::getId).containsExactly(1L);
    }

    @Test
    void supportsNumericStylesOnManualOrders() {
        records.add(order(1L, "A", "1001"));
        records.add(order(2L, "B", "10010"));

        assertThat(list("1001").getRecords()).extracting(OrderRecordResponse::getId).containsExactly(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"172", "172-1"})
    void supportsDevelopmentNumberGroupsWithoutMatchingSimilarDigits(String selected) {
        records.add(order(1L, "A", "JCD-172-1-02"));
        records.add(order(2L, "B", "JCD-172-1-03"));
        records.add(order(3L, "C", "JCD-1172-1-02"));

        assertThat(list(selected).getRecords()).extracting(OrderRecordResponse::getId).containsExactly(2L, 1L);
    }

    @Test
    void includesTheEntireOrderWhenAnAppendedPartMatches() {
        records.add(order(1L, "A", "JCD-205-3-17"));
        records.add(order(2L, "A", "JCD-172-1-02"));
        records.add(order(3L, "B", "JCD-395-3-01"));

        var result = list("172-1-02");

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords().get(0).getPartOrderIds()).containsExactly(1L, 2L);
        assertThat(result.getRecords().get(0).getTotalQuantity()).isEqualTo(20);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", ","})
    void clearingTheSelectionReturnsAllOrders(String selected) {
        records.add(order(1L, "A", "JCD-172-1-02"));
        records.add(order(2L, "B", null));

        assertThat(list(selected).getTotal()).isEqualTo(2);
    }

    private PageResponse<OrderRecordResponse> list(String selected) {
        return service.listOrders(null, selected, null, null, null, null, 1, 20);
    }

    private OrderRecord order(long id, String orderNo, String developmentNos) {
        var order = new OrderRecord();
        order.setId(id);
        order.setCustomerId(1L);
        order.setOrderNo(orderNo);
        order.setDevelopmentNos(developmentNos);
        order.setSourceType(3);
        order.setTotalQuantity(10);
        order.setCreatedAt(LocalDateTime.of(2026, 10, 1, 0, 0).plusMinutes(id));
        return order;
    }
}
