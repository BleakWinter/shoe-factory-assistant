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

export interface SettlementShipment {
  key: string;
  taskNo: string | null;
  shippingDate: string;
  orderNo: string | null;
  developmentNo: string | null;
  colorMaterial: string | null;
  quantity: number;
  unitPrice: number | null;
  amount: number | null;
  priceStatus: "NOT_QUOTED" | "DRAFT" | "QUOTED" | "CONFIRMED";
  quotationId: number | null;
  quotedPrice: number | null;
}

export interface SettlementMaterial {
  date: string;
  name: string;
  quantity: number | null;
  unit: string;
  unitPrice: number | null;
  reference: string;
}

export interface SettlementAdjustment {
  type: "CREDIT" | "DEBIT";
  reason: string;
  amount: number | null;
}

export interface SettlementDraft {
  preparedOn: string;
  holdRate: number;
  holdBase: number | null;
  deferredKeys: string[];
  carryKeys: string[];
  materials: SettlementMaterial[];
  adjustments: SettlementAdjustment[];
  refunds: { originId: number; amount: number }[];
  omittedRefundIds: number[];
}

export interface SettlementRefund {
  originId: number;
  originMonth: string;
  dueMonth: string;
  remaining: number;
  amount: number;
}

export interface SettlementTotals {
  shippedPairs: number;
  goods: number;
  carryover: number;
  holdBase: number;
  hold: number;
  refund: number;
  materials: number;
  credits: number;
  debits: number;
  receivable: number;
}

export interface MonthlySettlement {
  month: string;
  revision: number;
  confirmedAt: string | null;
  receivedOn: string | null;
  draft: SettlementDraft;
  statement: {
    current: SettlementShipment[];
    pending: SettlementShipment[];
    carryovers: SettlementShipment[];
    refunds: SettlementRefund[];
    totals: SettlementTotals;
  };
  previewToken: string | null;
}
