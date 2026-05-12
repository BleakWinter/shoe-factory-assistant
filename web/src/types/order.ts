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
  sizeQuantities?: Record<string, number>;
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
  customerName?: string;
  lastNo?: string;
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
