export type Decimal = number | string | null;
export type QuoteCategory = "MATERIAL" | "FEE" | "OUTSOLE";
export interface QuotationItem {
  category: QuoteCategory;
  partName: string;
  materialName: string;
  quantity: Decimal;
  unit: string;
  unitPrice: Decimal;
  remark: string;
}
export interface QuotationDraft {
  customerId: number | null;
  developmentNo: string;
  lastNo: string;
  colorMaterial: string;
  quotationDate: string | null;
  imageKey: string | null;
  imageUrl?: string | null;
  sourceDetailId?: number | null;
  laborCost: Decimal;
  includeOutsole: boolean;
  roundingMode: "CEIL" | "CENT" | "ROUND";
  manualPrice: boolean;
  finalPrice: Decimal;
  note: string;
  items: QuotationItem[];
}
export interface QuotationVersion {
  id: number; versionNo: number; finalPrice: Decimal; createdAt: string; adoptedAt: string | null;
}
export interface Quotation extends QuotationDraft {
  id: number; versionGroup: string; versionNo: number; latestVersionNo: number;
  customerName: string | null; materialAmount: Decimal; auxiliaryAmount: Decimal;
  outsoleAmount: Decimal; calculatedAmount: Decimal; currentPrice: Decimal;
  createdAt: string; adoptedAt: string | null; signedImageUrl: string | null; versions: QuotationVersion[];
}
export interface QuotationPage { records: Quotation[]; total: number; page: number; size: number }
