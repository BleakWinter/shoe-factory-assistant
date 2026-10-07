package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.PackingOrderItemRequest;
import com.shoefactory.assistant.entity.OrderPackingDetail;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import com.shoefactory.assistant.entity.ShoeStyleConfig;
import com.shoefactory.assistant.entity.InnerBoxTypeConfig;
import com.shoefactory.assistant.service.InnerBoxTypeConfigService;
import com.shoefactory.assistant.mapper.OrderPackingDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.mapper.ShoeStyleConfigMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class PackingOrderSummaryServiceTest {
    private final OrderRecordDetailMapper details = mock(OrderRecordDetailMapper.class);
    private final OrderPackingDetailMapper packing = mock(OrderPackingDetailMapper.class);
    private final OrderRecordMapper orders = mock(OrderRecordMapper.class);
    private final ShoeStyleConfigMapper styles = mock(ShoeStyleConfigMapper.class);
    private final InnerBoxTypeConfigService boxTypes = mock(InnerBoxTypeConfigService.class);
    private final PackingOrderSummaryService service = new PackingOrderSummaryService(details, packing, orders, styles, boxTypes);
    private ShoeStyleConfig configuredStyle;
    private final List<OrderPackingDetail> packingRows = new ArrayList<>();
    private OrderRecordDetail source;

    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderPackingDetail.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ShoeStyleConfig.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderRecordDetail.class);
    }

    @BeforeEach
    void setUp() {
        source = source(11L, "1", "20");
        source.setQuantity(999); // 汇总以装箱数据为准，不能使用来源订单或缓存的总双数。
        when(details.selectBatchIds(anyCollection())).thenReturn(List.of(source));
        when(details.selectList(any())).thenReturn(List.of(source));
        OrderRecord order = new OrderRecord();
        order.setId(101L);
        order.setOrderNo("INV-A");
        when(orders.selectBatchIds(anyCollection())).thenReturn(List.of(order));
        when(packing.selectList(any())).thenAnswer(call -> List.copyOf(packingRows));
        configuredStyle = new ShoeStyleConfig();
        configuredStyle.setDevelopmentNo("JCD-205-3-17");
        configuredStyle.setBoxSpec("30×20×10");
        configuredStyle.setInnerBoxTypeId(7L);
        when(styles.selectList(any())).thenReturn(List.of(configuredStyle));
        InnerBoxTypeConfig first = new InnerBoxTypeConfig();
        first.setId(7L); first.setName("标准盒第一版"); first.setEnabled(true);
        InnerBoxTypeConfig second = new InnerBoxTypeConfig();
        second.setId(8L); second.setName("标准盒第二版"); second.setEnabled(true);
        when(boxTypes.byIds(anyCollection())).thenReturn(Map.of(7L, first, 8L, second));
    }

    @Test
    void weightsEachCapacityByTheMergedRowsCartonCount() {
        packingRows.add(row(21L, "1", "10", 10, "{\"36\":6,\"37\":6}"));
        packingRows.add(row(22L, "11", "12", 2, "{\"36\":4,\"37\":6}"));
        packingRows.get(0).setTotalPairs(999);

        var item = service.prepare(List.of(request(11L))).get(0).detail();

        assertThat(item.getPackingCapacities()).isEqualTo(Map.of("12", 10, "10", 2));
        assertThat(item.getQuantity()).isEqualTo(140);
        assertThat(item.getCartonCount()).isEqualTo(12);
        assertThat(item.getSourcePackingDetailIds()).containsExactly(21L, 22L);
        assertThat(item.getPackingProblems()).isEmpty();
    }

    @Test
    void preservesOtherCapacitiesAndInfersMissingCartonCountsFromTheRange() {
        packingRows.add(row(21L, "1", "3", null, "{\"36\":15}"));
        packingRows.add(row(22L, "4", "4", 0, "{\"36\":1}"));

        var item = service.prepare(List.of(request(11L))).get(0).detail();

        assertThat(item.getPackingCapacities()).isEqualTo(Map.of("15", 3, "1", 1));
        assertThat(item.getQuantity()).isEqualTo(46);
        assertThat(item.getCartonCount()).isEqualTo(4);
        assertThat(item.getPackingProblems()).isEmpty();
    }

    @Test
    void explicitRowCartonCountHasPriorityOverTheRangeLength() {
        packingRows.add(row(21L, "1", "10", 2, "{\"36\":12}"));
        var item = service.prepare(List.of(request(11L))).get(0).detail();
        assertThat(item.getPackingCapacities()).isEqualTo(Map.of("12", 2));
        assertThat(item.getQuantity()).isEqualTo(24);
    }

    @Test
    void usesConfiguredInnerBoxAndAllowsPerRecordOverridesWithoutChangingConfiguration() {
        source.setDevelopmentNo("jcd-205-3-17");
        packingRows.add(row(21L, "1", "1", 1, "{\"36\":12}"));
        assertThat(service.prepare(List.of(request(11L))).get(0).detail().getBoxSpec()).isEqualTo("30×20×10");
        PackingOrderItemRequest request = request(11L);
        request.setBoxSpec("  31×21×11  ");
        request.setInnerBoxTypeId(8L);
        request.setRemark("  先订内盒  ");
        var item = service.prepare(List.of(request)).get(0).detail();
        assertThat(item.getBoxSpec()).isEqualTo("31×21×11");
        assertThat(item.getInnerBoxTypeId()).isEqualTo(8L);
        assertThat(item.getInnerBoxTypeName()).isEqualTo("标准盒第二版");
        assertThat(configuredStyle.getInnerBoxTypeId()).isEqualTo(7L);
        assertThat(item.getRemark()).isEqualTo("先订内盒");
        request.setBoxSpec("");
        assertThat(service.prepare(List.of(request)).get(0).detail().getBoxSpec()).isNull();
        verify(styles, never()).updateById(any(ShoeStyleConfig.class));
    }

    @Test
    void defaultsTypeFromStyleAndAllowsUnconfiguredLegacyStylesInPreview() {
        var item = service.prepare(List.of(request(11L))).get(0).detail();
        assertThat(item.getInnerBoxTypeId()).isEqualTo(7L);
        assertThat(item.getInnerBoxTypeName()).isEqualTo("标准盒第一版");
        configuredStyle.setInnerBoxTypeId(null);
        when(boxTypes.byIds(anyCollection())).thenReturn(Map.of());
        item = service.prepare(List.of(request(11L))).get(0).detail();
        assertThat(item.getInnerBoxTypeId()).isNull();
        assertThat(item.getInnerBoxTypeName()).isNull();
    }

    @Test
    void sameOrderStyleCannotContainConflictingInnerBoxVersions() {
        when(details.selectBatchIds(anyCollection())).thenReturn(List.of(source, source(12L, "1", "20")));
        var first = request(11L); first.setInnerBoxTypeId(7L);
        var second = request(12L); second.setInnerBoxTypeId(8L);
        assertThatThrownBy(() -> service.prepare(List.of(first, second))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("内盒类型不一致");
    }

    @Test
    void doesNotMixAnotherOrderStyleOrCartonRange() {
        packingRows.add(row(21L, "1", "1", 1, "{\"36\":12}"));
        OrderPackingDetail anotherOrder = row(22L, "2", "2", 1, "{\"36\":12}");
        anotherOrder.setOrderId(102L);
        packingRows.add(anotherOrder);
        OrderPackingDetail anotherStyle = row(23L, "3", "3", 1, "{\"36\":12}");
        anotherStyle.setCompanyStyleNo("JCD-205-3-18");
        packingRows.add(anotherStyle);
        packingRows.add(row(24L, "21", "21", 1, "{\"36\":12}"));

        var item = service.prepare(List.of(request(11L))).get(0).detail();
        assertThat(item.getSourcePackingDetailIds()).containsExactly(21L);
        assertThat(item.getQuantity()).isEqualTo(12);
    }

    @Test
    void mergesSameOrderAndStyleAndCountsSharedPackingRowsOnce() {
        when(details.selectBatchIds(anyCollection())).thenReturn(List.of(source, source(12L, "1", "20")));
        packingRows.add(row(21L, "1", "1", 1, "{\"36\":12}"));

        var prepared = service.prepare(List.of(request(12L), request(11L)));
        assertThat(prepared).hasSize(1);
        assertThat(prepared.get(0).detail().getSourceDetailIds()).containsExactly(11L, 12L);
        assertThat(prepared.get(0).detail().getSourcePackingDetailIds()).containsExactly(21L);
        assertThat(prepared.get(0).detail().getQuantity()).isEqualTo(12);
        assertThat(prepared.get(0).detail().getCartonCount()).isEqualTo(1);
        assertThat(prepared.get(0).detail().getPackingProblems()).isEmpty();
    }

    @Test
    void combinesContinuousSourceRangesBeforeMatchingAMergedPackingRow() {
        when(details.selectBatchIds(anyCollection())).thenReturn(List.of(source(11L, "1", "5"), source(12L, "6", "10")));
        packingRows.add(row(21L, "1", "10", 10, "{\"36\":12}"));
        PackingOrderItemRequest request = request(11L);
        request.setSourceDetailIds(List.of(11L, 12L));
        var item = service.prepare(List.of(request)).get(0).detail();
        assertThat(item.getKey()).isEqualTo("101:JCD-205-3-17");
        assertThat(item.getSourceDetailIds()).containsExactly(11L, 12L);
        assertThat(item.getPackingCapacities()).isEqualTo(Map.of("12", 10));
        assertThat(item.getQuantity()).isEqualTo(120);
        assertThat(item.getPackingProblems()).isEmpty();
    }

    @Test
    void includesAdditionalSameStyleRowsWhenRefreshingTheWholeOrderStyle() {
        OrderRecordDetail appended = source(12L, "21", "25");
        when(details.selectList(any())).thenReturn(List.of(source, appended));
        packingRows.add(row(21L, "1", "1", 2, "{\"36\":12}"));
        packingRows.add(row(22L, "21", "25", 5, "{\"36\":10}"));
        var item = service.prepare(List.of(request(11L))).get(0).detail();
        assertThat(item.getSourceDetailIds()).containsExactly(11L, 12L);
        assertThat(item.getPackingCapacities()).isEqualTo(Map.of("12", 2, "10", 5));
        assertThat(item.getQuantity()).isEqualTo(74);
        assertThat(item.getPackingProblems()).isEmpty();
    }

    @Test
    void keepsSameStyleOnDifferentOrdersSeparate() {
        OrderRecordDetail second = source(12L, "1", "20");
        second.setOrderId(102L);
        when(details.selectBatchIds(anyCollection())).thenReturn(List.of(source, second));
        OrderRecord firstOrder = new OrderRecord();
        firstOrder.setId(101L); firstOrder.setOrderNo("INV-A");
        OrderRecord secondOrder = new OrderRecord();
        secondOrder.setId(102L); secondOrder.setOrderNo("INV-B");
        when(orders.selectBatchIds(anyCollection())).thenReturn(List.of(firstOrder, secondOrder));
        packingRows.add(row(21L, "1", "1", 2, "{\"36\":12}"));
        OrderPackingDetail secondPacking = row(22L, "1", "1", 3, "{\"36\":10}");
        secondPacking.setOrderId(102L);
        packingRows.add(secondPacking);

        var prepared = service.prepare(List.of(request(11L), request(12L)));
        assertThat(prepared).hasSize(2);
        assertThat(prepared.get(0).detail().getQuantity()).isEqualTo(24);
        assertThat(prepared.get(1).detail().getQuantity()).isEqualTo(30);
        assertThat(prepared.get(0).detail().getKey()).isNotEqualTo(prepared.get(1).detail().getKey());
    }

    @Test
    void flagsAmbiguousLegacyPackingAcrossDifferentStyles() {
        OrderRecordDetail second = source(12L, "1", "20");
        second.setDevelopmentNo("JCD-205-3-18");
        when(details.selectBatchIds(anyCollection())).thenReturn(List.of(source, second));
        OrderPackingDetail packingRow = row(21L, "1", "1", 1, "{\"36\":12}");
        packingRow.setCompanyStyleNo(null);
        packingRows.add(packingRow);
        var prepared = service.prepare(List.of(request(11L), request(12L)));
        assertThat(prepared).hasSize(2).allSatisfy(item -> assertThat(item.detail().getPackingProblems())
                .anyMatch(problem -> problem.contains("重复关联")));
    }

    @Test
    void refusesMixedStylesInsideOneSubmittedItem() {
        OrderRecordDetail second = source(12L, "1", "20");
        second.setDevelopmentNo("JCD-205-3-18");
        when(details.selectBatchIds(anyCollection())).thenReturn(List.of(source, second));
        PackingOrderItemRequest request = request(11L);
        request.setSourceDetailIds(List.of(11L, 12L));
        assertThatThrownBy(() -> service.prepare(List.of(request))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("同一个订单和款号");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "invalid", "{\"36\":-1}", "{\"36\":0}", "{\"36\":null}", "{\"36\":1.5}"})
    void invalidSizeDataIsReportedInsteadOfSilentlyBecomingAValidZero(String json) {
        packingRows.add(row(21L, "1", "1", 1, json));
        assertThat(service.prepare(List.of(request(11L))).get(0).detail().getPackingProblems())
                .anyMatch(problem -> problem.contains("无效"));
    }

    @Test
    void negativeCartonCountsAndMissingPackingAreReported() {
        assertThat(service.prepare(List.of(request(11L))).get(0).detail().getPackingProblems())
                .anyMatch(problem -> problem.contains("未匹配"));
        packingRows.add(row(21L, "1", "1", -2, "{\"36\":12}"));
        assertThat(service.prepare(List.of(request(11L))).get(0).detail().getPackingProblems())
                .anyMatch(problem -> problem.contains("无效"));
    }

    @Test
    void rejectsDuplicateMissingAndNullSourceDetails() {
        assertThatThrownBy(() -> service.prepare(List.of(request(11L), request(11L)))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.prepare(List.of(request(99L)))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.prepare(Arrays.asList(request(11L), null))).isInstanceOf(BusinessException.class);
    }

    @Test
    void fallsBackToPackingImageAndKeepsItsPathForHistory() {
        OrderPackingDetail row = row(21L, "1", "1", 1, "{\"36\":12}");
        row.setStyleImagePath("2026/10/images/style-a.png");
        packingRows.add(row);
        var prepared = service.prepare(List.of(request(11L))).get(0);
        assertThat(prepared.imagePath()).isEqualTo("2026/10/images/style-a.png");
        assertThat(prepared.detail().getImageUrl()).isEqualTo("/api/orders/packing-details/21/image");
        source.setStyleImagePath("2026/10/images/order-a.png");
        prepared = service.prepare(List.of(request(11L))).get(0);
        assertThat(prepared.imagePath()).isEqualTo("2026/10/images/order-a.png");
        assertThat(prepared.detail().getImageUrl()).isEqualTo("/api/orders/details/11/image");
    }

    private static PackingOrderItemRequest request(Long id) {
        PackingOrderItemRequest request = new PackingOrderItemRequest();
        request.setSourceDetailIds(List.of(id));
        return request;
    }

    private static OrderRecordDetail source(Long id, String start, String end) {
        OrderRecordDetail source = new OrderRecordDetail();
        source.setId(id);
        source.setOrderId(101L);
        source.setDevelopmentNo("JCD-205-3-17");
        source.setCartonStart(start);
        source.setCartonEnd(end);
        return source;
    }

    private static OrderPackingDetail row(Long id, String start, String end, Integer cartons, String sizes) {
        OrderPackingDetail row = new OrderPackingDetail();
        row.setId(id);
        row.setOrderId(101L);
        row.setCompanyStyleNo("JCD-205-3-17");
        row.setCartonStart(start);
        row.setCartonEnd(end);
        row.setCartonCount(cartons);
        row.setSizeQuantitiesJson(sizes);
        row.setRowIndex(id.intValue());
        return row;
    }
}
