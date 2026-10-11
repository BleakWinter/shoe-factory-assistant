import request from "../utils/request";
import type { MonthlyReconciliation, UnpricedMonthDetails, UnpricedMonthSummary } from "../types/reconciliation";

export async function fetchMonthlyReconciliation(month: string, customerId: number) {
  const { data } = await request.get<MonthlyReconciliation>("/monthly-reconciliations", {
    params: { month, customerId },
  });
  return data;
}

export async function fetchReconciliationCustomers() {
  const { data } = await request.get<string[]>("/monthly-reconciliations/customers");
  return data || [];
}

export async function fetchUnpricedMonths(customerId?: number) {
  const { data } = await request.get<UnpricedMonthSummary[]>("/monthly-reconciliations/unpriced-months", {
    params: { customerId },
  });
  return data;
}

export async function fetchUnpricedMonthDetails(month: string, customerId?: number) {
  const { data } = await request.get<UnpricedMonthDetails>("/monthly-reconciliations/unpriced-details", {
    params: { month, customerId },
  });
  return data;
}
