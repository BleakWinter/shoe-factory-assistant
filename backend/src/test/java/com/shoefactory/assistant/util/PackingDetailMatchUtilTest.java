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
