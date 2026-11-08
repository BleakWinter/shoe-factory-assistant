import request from "../utils/request";
import type { MonthlyReconciliation, MonthlySettlement, SettlementDraft, UnpricedMonthDetails, UnpricedMonthSummary } from "../types/reconciliation";

const settlementUrl = "/monthly-reconciliations/settlement";

export async function fetchMonthlySettlement(month: string, customerId: number) {
  return (await request.get<MonthlySettlement>(settlementUrl, { params: { month, customerId } })).data;
}

export async function saveMonthlySettlement(month: string, customerId: number, revision: number, draft: SettlementDraft) {
  return (await request.put<MonthlySettlement>(settlementUrl, { revision, draft }, { params: { month, customerId } })).data;
}

export async function confirmMonthlySettlement(month: string, customerId: number, revision: number, previewToken: string) {
  return (await request.post<MonthlySettlement>(`${settlementUrl}/confirm`, { revision, previewToken }, { params: { month, customerId } })).data;
}

export async function recordMonthlySettlementReceipt(month: string, customerId: number, revision: number, receivedOn: string | null) {
  return (await request.patch<MonthlySettlement>(`${settlementUrl}/receipt`, { revision, receivedOn }, { params: { month, customerId } })).data;
}

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
