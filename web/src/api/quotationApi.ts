import request from "../utils/request";
import type { DevelopmentNoOption } from "../types/order";
import type { Decimal, Quotation, QuotationDraft, QuotationPage } from "../types/quotation";
export async function fetchQuotations(params: { customerId?: number; developmentNos?: string; startDate?: string; endDate?: string; page: number; size: number }) {
  return (await request.get<QuotationPage>("/quotations", { params })).data;
}
export async function fetchQuotationDevelopmentNoOptions(customerId?: number) {
  return (await request.get<DevelopmentNoOption[]>("/quotations/development-options", { params: { customerId } })).data || [];
}
export async function fetchQuotation(id: number) { return (await request.get<Quotation>(`/quotations/${id}`)).data; }
export async function fetchQuotationForPriceConfig(customerId: number, developmentNo: string) {
  return (await request.get<number | null>("/quotations/for-price-config", { params: { customerId, developmentNo } })).data ?? null;
}
export async function saveQuotation(draft: QuotationDraft, source?: Quotation) {
  const { imageUrl: _imageUrl, ...payload } = draft;
  return (await request.post<Quotation>(source ? `/quotations/${source.id}/versions` : "/quotations",
    { ...payload, expectedVersion: source?.latestVersionNo })).data;
}
export async function adoptQuotation(id: number) { return (await request.post<Quotation>(`/quotations/${id}/adopt`)).data; }
export async function fetchQuotationReference(customerId: number, developmentNo: string) {
  return (await request.get<{ sourceDetailId: number | null; imageUrl: string | null; colorMaterial: string | null; currentPrice: Decimal }>(
    "/quotations/reference", { params: { customerId, developmentNo } })).data;
}
export async function uploadQuotationImage(file: File) {
  const data = new FormData(); data.append("file", file);
  return (await request.post<{ imageKey: string; imageUrl: string }>("/quotations/images", data)).data;
}
export async function exportQuotationExcel(id: number) { return (await request.get<Blob>(`/quotations/${id}/excel`, { responseType: "blob" })).data; }
