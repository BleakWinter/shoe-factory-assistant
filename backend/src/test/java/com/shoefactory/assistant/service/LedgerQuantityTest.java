package com.shoefactory.assistant.service;
import com.shoefactory.assistant.entity.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LedgerQuantityTest {
    @Test void usesTotalQuantityBeforePerCartonSizeMapForLegacyExcelOrders() {
        OrderRecordDetail detail=new OrderRecordDetail();
        detail.setQuantity(240); detail.setCartonCount(20); detail.setSizeQuantitiesJson("{\"36\":6,\"37\":6}");
        assertEquals(240,LedgerDataService.quantity(detail));
    }
    @Test void expandsPerCartonSizesWhenLegacyTotalQuantityIsMissing() {
        OrderRecordDetail detail=new OrderRecordDetail();
        detail.setCartonCount(20); detail.setSizeQuantitiesJson("{\"36\":6,\"37\":6}");
        assertEquals(240,LedgerDataService.quantity(detail));
        detail.setCartonCount(null);
        assertEquals(12,LedgerDataService.quantity(detail));
    }
    @Test void manualLineUsesItsEntireOrderQuantity() {
        OrderRecordDetail detail=new OrderRecordDetail();detail.setQuantity(100);
        assertEquals(100,LedgerDataService.quantity(detail));
        assertEquals(0,LedgerDataService.quantity(null));
    }
    @Test void malformedLegacySizesDoNotBreakTheLedger() {
        OrderRecordDetail detail=new OrderRecordDetail();detail.setSizeQuantitiesJson("{broken");
        assertEquals(0,LedgerDataService.quantity(detail));
        detail.setQuantity(30);
        assertEquals(30,LedgerDataService.quantity(detail));
    }
}
