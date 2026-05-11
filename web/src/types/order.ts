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

export type RecognitionStatus = "RECOGNIZED" | "PENDING_MANUAL" | "FAILED";
export type SourceFileType = "EXCEL" | "IMAGE";
export type PrintType = "ORDER" | "PACKING";
export type PrintTaskStatus =
  | "PENDING"
  | "PRINTING"
  | "SUCCESS"
  | "FAILED"
  | "CANCELED";

export interface OrderRecord {
  id: number;
  orderNo?: string;
  customerName?: string;
  styleNo?: string;
  color?: string;
  quantity?: number;
  cartonCount?: number;
  deliveryDate?: string;
  recognitionStatus: RecognitionStatus;
  errorMessage?: string;
  sourceFileId: number;
  sourceFileName?: string;
  sourceFileType: SourceFileType;
  sourceSheetName?: string;
  createdAt?: string;
}

export interface OrderQueryParams {
  orderNo?: string;
  styleNo?: string;
  customerName?: string;
  deliveryDate?: string;
  recognitionStatus?: RecognitionStatus;
  page?: number;
  size?: number;
}

export interface PrintPreview {
  id: number;
  previewNo: string;
  orderId: number;
  orderNo?: string;
  printType: PrintType;
  previewUrl: string;
  pdfSize?: number;
  status: "READY" | "FAILED";
  errorMessage?: string;
  createdAt?: string;
}

export interface PrintTask {
  id: number;
  taskNo: string;
  orderId: number;
  orderNo?: string;
  previewId: number;
  previewUrl?: string;
  printType: PrintType;
  printerName?: string;
  copies: number;
  status: PrintTaskStatus;
  priority: number;
  errorMessage?: string;
  pickedAt?: string;
  printedAt?: string;
  createdAt?: string;
}
