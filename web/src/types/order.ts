export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export interface PageResponse<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

export type OrderImportStatus = "IMPORTED" | "PARTIAL" | "FAILED";
export type ShipmentStatus = "NOT_SHIPPED" | "SHIPPED";
export type PrintTaskStatus =
  | "PENDING"
  | "PRINTING"
  | "SUCCESS"
  | "FAILED"
  | "CANCELED";

export interface OrderUploadResult {
  orderId: number;
  orderNo?: string;
  customerName?: string;
  lineCount?: number;
  totalPairs?: number;
  printTaskId?: number;
  printTaskNo?: string;
}

export interface OrderLine {
  id: number;
  orderId: number;
  orderNo?: string;
  invoiceNo?: string;
  customerName?: string;
  orderDate?: string;
  deliveryDate?: string;
  imageUrl?: string;
  lastNo?: string;
  styleNo?: string;
  developmentNo?: string;
  customerOrderNo?: string;
  warehouseNo?: string;
  poNo?: string;
  customerStyleNo?: string;
  englishColor?: string;
  englishMaterial?: string;
  upperMaterial?: string;
  liningMaterial?: string;
  accessory?: string;
  insolePlatform?: string;
  outsole?: string;
  trademark?: string;
  quantity?: number;
  cartonCount?: number;
  totalQuantity?: number;
  sizeQuantities?: Record<string, number>;
  shipmentStatus?: ShipmentStatus;
  remark?: string;
  importStatus?: OrderImportStatus;
  errorMessage?: string;
  sourceSheetName?: string;
  rowIndex?: number;
  createdAt?: string;
}

export interface OrderLineQueryParams {
  orderNo?: string;
  styleNo?: string;
  lastNo?: string;
  shipmentStatus?: ShipmentStatus;
  deliveryDate?: string;
  page?: number;
  size?: number;
}

export interface PrintTask {
  id: number;
  taskNo: string;
  orderId: number;
  orderNo?: string;
  customerName?: string;
  styleNos?: string[];
  totalPairs?: number;
  status: PrintTaskStatus;
  previewUrl?: string;
  errorMessage?: string;
  createdAt?: string;
}

export interface PrintPreview {
  id: number;
  previewNo: string;
  orderId: number;
  orderNo?: string;
  printType: "ORDER" | "PACKING";
  previewUrl: string;
  status: "READY" | "FAILED";
  errorMessage?: string;
  createdAt?: string;
}
