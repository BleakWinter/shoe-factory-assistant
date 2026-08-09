package com.shoefactory.assistant.service;

import com.shoefactory.assistant.dto.MonthlyReconciliationResponse;

import java.util.List;

public interface MonthlyReconciliationService {

    MonthlyReconciliationResponse getMonthlyReconciliation(String month, String customerName);

    List<String> listCustomers();
}
