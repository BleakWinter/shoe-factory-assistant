export interface PackingOrderItem {
  id?: number;
  key: string;
  orderId: number;
  orderNo?: string;
  developmentNo: string;
  boxSpec?: string;
  innerBoxTypeId?: number;
  innerBoxTypeName?: string;
  remark?: string;
  quantity: number;
  cartonCount: number;
  packingCapacities: Record<string, number>;
  sourceDetailIds: number[];
  sourcePackingDetailIds: number[];
  packingProblems: string[];
  imageUrl?: string;
}

export interface PackingOrderTask {
  id: number;
  taskNo: string;
  orderDate: string;
  orderNos?: string;
  developmentNos?: string;
  itemCount: number;
  totalPairs: number;
  totalCartonCount: number;
  items: PackingOrderItem[];
  createdAt?: string;
}

export interface PackingOrderCreatePayload {
  orderDate: string;
  items: Array<{
    sourceDetailIds: number[];
    boxSpec: string;
    innerBoxTypeId: number;
    remark?: string;
    expectedPackingCapacities: Record<string, number>;
    expectedPackingDetailIds: number[];
  }>;
}

export interface PackingOrderQueryParams {
  orderNo?: string;
  developmentNo?: string;
  page?: number;
  size?: number;
}
