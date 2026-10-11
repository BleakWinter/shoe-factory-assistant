package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.service.CustomerPriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShoePriceConfigControllerTest {
    private CustomerPriceService service;
    private MockMvc mvc;

    @BeforeEach void prepare() {
        service = mock(CustomerPriceService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ShoePriceConfigController(service)).build();
    }

    @Test void multipleStylesArePassedToPriceQuery() throws Exception {
        mvc.perform(get("/api/price-configs").param("customerId","2")
                .param("developmentNos","JCD-253-1-01,JCD-253-1-02").param("incompleteOnly","true")
                .param("page","3"))
                .andExpect(status().isOk());
        verify(service).list(2L,"JCD-253-1-01,JCD-253-1-02",true,3,20);
    }

    @Test void absentDatesKeepAllTimeApiQueriesCompatible() throws Exception {
        mvc.perform(get("/api/price-configs")).andExpect(status().isOk());
        verify(service).list(null,null,null,1,20);
    }
}
