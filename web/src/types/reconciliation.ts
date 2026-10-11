export interface MonthlyReconciliationDetail {
  taskItemId: number;
  shippingTaskId: number;
  taskNo?: string;
  shippingDate?: string;
  orderNo?: string;
  developmentNo?: string;
  colorMaterial?: string;
  orderQuantity?: number;
  shippingQuantity?: number;
  productType?: string;
  unitPrice?: number;
  amount?: number;
  remark?: string;
  missingPrice?: boolean;
}

export interface MonthlyReconciliation {
  month: string;
  customerName: string;
  totalOrderQuantity: number;
  totalShippingQuantity: number;
  goodsAmount: number;
  missingPriceCount: number;
  details: MonthlyReconciliationDetail[];
}

export interface UnpricedQuotationCounts {
  unquotedCount: number;
  draftCount: number;
  quotedCount: number;
}

export interface UnpricedQuotationSummary {
  id: number;
  versionNo: number;
  finalPrice: number | string | null;
  quotationDate: string | null;
  createdAt: string | null;
  adoptedAt: string | null;
}

export interface UnpricedMonthSummary {
  month: string;
  missingPriceCount: number;
  mainStyleCount: number;
  shippingQuantity: number;
  quotationCounts: UnpricedQuotationCounts;
}

export interface UnpricedShipmentDetail {
  taskItemId: number;
  shippingTaskId: number;
  taskNo?: string;
  shippingDate: string;
  customerId?: number;
  customerName: string;
  orderId?: number;
  orderNo?: string;
  developmentNo: string;
  colorMaterial?: string;
  shippingQuantity: number;
  remark?: string;
}

export interface UnpricedStyleGroup {
  mainStyle: string;
  missingPriceCount: number;
  shippingQuantity: number;
  styles: UnpricedStyleSummary[];
  quotationCounts: UnpricedQuotationCounts;
}

export interface UnpricedStyleSummary {
  customerId?: number;
  customerName: string;
  developmentNo: string;
  imageUrl?: string | null;
  shippingQuantity: number;
  details: UnpricedShipmentDetail[];
  quotationStatus: "NOT_QUOTED" | "DRAFT" | "QUOTED";
  latestQuotation: UnpricedQuotationSummary | null;
  latestPricedQuotation: UnpricedQuotationSummary | null;
}

export interface UnpricedMonthDetails {
  month: string;
  missingPriceCount: number;
  shippingQuantity: number;
  groups: UnpricedStyleGroup[];
  quotationCounts: UnpricedQuotationCounts;
}
