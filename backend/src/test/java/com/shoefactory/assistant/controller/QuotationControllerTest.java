package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.dto.QuotationSaveRequest;
import com.shoefactory.assistant.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QuotationControllerTest {
    private QuotationService service;
    private MockMvc mvc;
    @BeforeEach void prepare() {
        service=mock(QuotationService.class);
        mvc=MockMvcBuilders.standaloneSetup(new QuotationController(service,mock(QuotationImageStore.class),mock(QuotationExcelService.class))).build();
    }
    private String payload(String quantity) {
        return """
            {"includeOutsole":false,"roundingMode":"CEIL","manualPrice":false,
             "items":[{"category":"MATERIAL","quantity":"%s","unitPrice":"0.5678"}]}
            """.formatted(quantity);
    }
    @Test void fourDecimalStringsAreAcceptedWithoutFloatConversion() throws Exception {
        mvc.perform(post("/api/quotations").contentType(MediaType.APPLICATION_JSON).content(payload("0.1234"))).andExpect(status().isOk());
        var capture=ArgumentCaptor.forClass(QuotationSaveRequest.class); verify(service).create(capture.capture());
        assertEquals(new BigDecimal("0.1234"),capture.getValue().items().get(0).quantity());
        assertEquals(new BigDecimal("0.5678"),capture.getValue().items().get(0).unitPrice());
    }
    @Test void nestedItemValidationRejectsNegativeQuantity() throws Exception {
        mvc.perform(post("/api/quotations").contentType(MediaType.APPLICATION_JSON).content(payload("-1"))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void nestedItemValidationRejectsExcessDecimalPrecision() throws Exception {
        mvc.perform(post("/api/quotations").contentType(MediaType.APPLICATION_JSON).content(payload("0.12345"))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void referenceRouteIsDistinctFromVersionIdRoute() throws Exception {
        mvc.perform(get("/api/quotations/reference").param("customerId","2").param("developmentNo","195-13-23")).andExpect(status().isOk());
        verify(service).reference(2L,"195-13-23"); verifyNoMoreInteractions(service);
    }
}
