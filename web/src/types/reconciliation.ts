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
