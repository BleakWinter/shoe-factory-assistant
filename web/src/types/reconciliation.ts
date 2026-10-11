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

export interface UnpricedMonthSummary {
  month: string;
  missingPriceCount: number;
  mainStyleCount: number;
  shippingQuantity: number;
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
}

export interface UnpricedStyleSummary {
  customerId?: number;
  customerName: string;
  developmentNo: string;
  imageUrl?: string | null;
  shippingQuantity: number;
  details: UnpricedShipmentDetail[];
}

export interface UnpricedMonthDetails {
  month: string;
  missingPriceCount: number;
  shippingQuantity: number;
  groups: UnpricedStyleGroup[];
}
