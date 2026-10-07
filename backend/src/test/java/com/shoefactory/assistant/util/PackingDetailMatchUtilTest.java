package com.shoefactory.assistant.util;

import com.shoefactory.assistant.entity.OrderPackingDetail;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackingDetailMatchUtilTest {

    @Test
    void matchesSameStyleInsideOrderCartonRange() {
        OrderRecordDetail orderDetail = orderDetail(1L, "JCD-205-3-17", "1", "24");
        OrderPackingDetail packingDetail = packingDetail(1L, "jcd-205-3-17", "3", "4");

        assertThat(PackingDetailMatchUtil.isMatchingPackingDetail(orderDetail, packingDetail)).isTrue();
    }

    @Test
    void rejectsDifferentStyleEvenWhenCartonRangeOverlaps() {
        OrderRecordDetail orderDetail = orderDetail(1L, "JCD-205-3-17", "1", "24");
        OrderPackingDetail packingDetail = packingDetail(1L, "JCD-205-3-18", "3", "4");

        assertThat(PackingDetailMatchUtil.isMatchingPackingDetail(orderDetail, packingDetail)).isFalse();
    }

    @Test
    void rejectsPackingDetailFromAnotherOrder() {
        OrderRecordDetail orderDetail = orderDetail(1L, "JCD-205-3-17", "1", "24");
        OrderPackingDetail packingDetail = packingDetail(2L, "JCD-205-3-17", "3", "4");

        assertThat(PackingDetailMatchUtil.isMatchingPackingDetail(orderDetail, packingDetail)).isFalse();
    }

    @Test
    void fallsBackToCartonRangeWhenLegacyStyleNoIsMissing() {
        OrderRecordDetail orderDetail = orderDetail(1L, "JCD-205-3-17", "1", "24");
        OrderPackingDetail packingDetail = packingDetail(1L, null, "3", "4");

        assertThat(PackingDetailMatchUtil.isMatchingPackingDetail(orderDetail, packingDetail)).isTrue();
    }

    @Test
    void rejectsSameStyleOutsideOrderCartonRange() {
        OrderRecordDetail orderDetail = orderDetail(1L, "JCD-205-3-17", "1", "24");
        OrderPackingDetail packingDetail = packingDetail(1L, "JCD-205-3-17", "25", "26");

        assertThat(PackingDetailMatchUtil.isMatchingPackingDetail(orderDetail, packingDetail)).isFalse();
    }

    @Test
    void countsMergedCartonsUsingTheExplicitRowCount() {
        OrderPackingDetail detail = packingDetail(1L, "STYLE-A", "1", "10");
        detail.setCartonCount(4);
        assertThat(PackingDetailMatchUtil.getPackingCartonCount(detail)).isEqualTo(4);
    }

    @Test
    void infersMissingCountsFromPrefixedCartonRanges() {
        OrderPackingDetail detail = packingDetail(1L, "STYLE-A", "A001", "A010");
        assertThat(PackingDetailMatchUtil.getPackingCartonCount(detail)).isEqualTo(10);
        detail.setCartonCount(0);
        assertThat(PackingDetailMatchUtil.getPackingCartonCount(detail)).isEqualTo(10);
    }

    @Test
    void unparseableLegacyRangeFallsBackToOneCarton() {
        OrderPackingDetail detail = packingDetail(1L, "STYLE-A", null, null);
        assertThat(PackingDetailMatchUtil.getPackingCartonCount(detail)).isEqualTo(1);
    }

    @Test
    void oversizedCartonNumberDoesNotCrashMatching() {
        OrderRecordDetail orderDetail = orderDetail(1L, "STYLE-A", "1", "20");
        OrderPackingDetail packingDetail = packingDetail(1L, "STYLE-A", "999999999999999", null);
        assertThat(PackingDetailMatchUtil.isMatchingPackingDetail(orderDetail, packingDetail)).isFalse();
    }

    @Test
    void matchesACombinedPackingRangeCoveredByContinuousSourceRanges() {
        var first = orderDetail(1L, "STYLE-A", "1", "5");
        var second = orderDetail(1L, "STYLE-A", "6", "10");
        var packing = packingDetail(1L, "STYLE-A", "1", "10");
        assertThat(PackingDetailMatchUtil.isMatchingPackingDetails(java.util.List.of(first, second), packing)).isTrue();
    }

    @Test
    void cannotBridgeAGapOrUseAnotherStylesRange() {
        var first = orderDetail(1L, "STYLE-A", "1", "4");
        var second = orderDetail(1L, "STYLE-A", "6", "10");
        var packing = packingDetail(1L, "STYLE-A", "1", "10");
        assertThat(PackingDetailMatchUtil.isMatchingPackingDetails(java.util.List.of(first, second), packing)).isFalse();
        first.setCartonEnd("5"); second.setDevelopmentNo("STYLE-B");
        assertThat(PackingDetailMatchUtil.isMatchingPackingDetails(java.util.List.of(first, second), packing)).isFalse();
    }

    private OrderRecordDetail orderDetail(Long orderId, String developmentNo, String cartonStart, String cartonEnd) {
        OrderRecordDetail detail = new OrderRecordDetail();
        detail.setOrderId(orderId);
        detail.setDevelopmentNo(developmentNo);
        detail.setCartonStart(cartonStart);
        detail.setCartonEnd(cartonEnd);
        return detail;
    }

    private OrderPackingDetail packingDetail(Long orderId, String companyStyleNo, String cartonStart, String cartonEnd) {
        OrderPackingDetail detail = new OrderPackingDetail();
        detail.setOrderId(orderId);
        detail.setCompanyStyleNo(companyStyleNo);
        detail.setCartonStart(cartonStart);
        detail.setCartonEnd(cartonEnd);
        return detail;
    }
}
