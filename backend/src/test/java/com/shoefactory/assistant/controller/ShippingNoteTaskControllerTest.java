package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.common.GlobalExceptionHandler;
import com.shoefactory.assistant.service.ShippingNoteTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ShippingNoteTaskControllerTest {

    private final ShippingNoteTaskService service = mock(ShippingNoteTaskService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ShippingNoteTaskController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void deleteEndpointReturnsTheStandardSuccessResponse() throws Exception {
        mvc.perform(delete("/api/shipping-note-tasks/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(service).deleteShippingNoteTask(42L);
    }

    @Test
    void rejectedDeletionReturnsTheBusinessErrorForTheConfirmationDialog() throws Exception {
        doThrow(new BusinessException("手工出货记录不能从打印任务删除"))
                .when(service).deleteShippingNoteTask(42L);

        mvc.perform(delete("/api/shipping-note-tasks/42"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("手工出货记录不能从打印任务删除"));
    }
}
