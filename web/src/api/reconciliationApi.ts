import request from "../utils/request";
import type { MonthlyReconciliation } from "../types/reconciliation";

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
