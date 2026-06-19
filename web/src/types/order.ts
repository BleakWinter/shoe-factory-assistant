export interface ApiResponse<T> {
  // 后端统一响应外壳，axios 拦截器会把 data 拆出来。
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export interface PageResponse<T> {
  // Ant Design 表格分页需要 records 和 total。
  records: T[];
  total: number;
  page: number;
  size: number;
}

export const PRINT_TYPES = {
  ORDER: "ORDER",
  PACKING: "PACKING",
} as const;
export type PrintType = (typeof PRINT_TYPES)[keyof typeof PRINT_TYPES];

// 打印任务状态和后端 PrintTaskStatus 枚举保持一致。
export type PrintTaskStatus =
  | "PENDING"
  | "PRINTED"
  | "FAILED"
  | "INVALID";

export interface OrderUploadResult {
  // 上传成功后后端返回的摘要，用于提示“解析了哪条订单”。
  orderId: number;
  orderNo?: string;
  customerName?: string;
  lineCount?: number;
  totalPairs?: number;
  printTaskId?: number;
  printTaskNo?: string;
}

export interface OrderRecord {
  // 对应 order_record 主表，订单列表和打印列表都展示它。
  id: number;
  orderNo?: string;
  customerName?: string;
  boxImageUrl?: string;
  developmentNos?: string;
  developmentNoList?: string[];
  totalQuantity?: number;
  totalCartonCount?: number;
  sourceType?: number;
  sourceTypeText?: string;
  orderRecognitionStatus?: number;
  orderRecognitionStatusText?: string;
  packingRecognitionStatus?: number;
  packingRecognitionStatusText?: string;
  remark?: string;
  errorMessage?: string;
  orderErrorMessage?: string;
  packingErrorMessage?: string;
  createdAt?: string;
}

export interface OrderRecordQueryParams {
  // 订单主表筛选参数，字段名和后端 /api/orders 对齐。
  orderNo?: string;
  developmentNos?: string;
  recognitionStatus?: string;
  unfinishedProcessType?: number;
  page?: number;
  size?: number;
}

export interface DevelopmentNoOption {
  value: string;
  label: string;
  children?: DevelopmentNoOption[];
}

export interface DevelopmentNoOrderReference {
  orderId?: number;
  invoiceNo?: string;
  orderNo?: string;
  pairCount?: number;
  shippedPairCount?: number;
  unshippedPairCount?: number;
  detailCount?: number;
}

export interface DevelopmentNoStatisticNode {
  key: string;
  label: string;
  fullDevelopmentNo?: string;
  level: number;
  pairCount: number;
  shippedPairCount?: number;
  unshippedPairCount?: number;
  detailCount: number;
  styleCount: number;
  orderReferences?: DevelopmentNoOrderReference[];
  children?: DevelopmentNoStatisticNode[];
}

export interface StatisticsTimePoint {
  date: string;
  value: number;
}

export interface UnshippedInvoiceStatistic {
  orderId?: number;
  invoiceNo?: string;
  createdAt?: string;
  pairCount?: number;
  shippedPairCount?: number;
  unshippedPairCount?: number;
  detailCount?: number;
}

export interface OrderStatistics {
  totalPairs: number;
  shippedPairs: number;
  unshippedPairs: number;
  styleCount: number;
  detailCount: number;
  orderCreatedTrend?: StatisticsTimePoint[];
  shippedPairsTrend?: StatisticsTimePoint[];
  unshippedInvoiceStatistics?: UnshippedInvoiceStatistic[];
  developmentNoTree: DevelopmentNoStatisticNode[];
  topDevelopmentNos: DevelopmentNoStatisticNode[];
}

export interface OrderDetailProcess {
  // 对应 order_detail_process。
  id: number;
  orderId: number;
  orderDetailId: number;
  processType?: number;
  processTypeText?: string;
  processStatus?: number;
  processStatusText?: string;
  processCount?: number;
  lastProcessAt?: string;
  remark?: string;
  createdAt?: string;
}

export interface OrderRecordDetail {
  // 对应 order_record_detail。
  id: number;
  orderId: number;
  orderNo?: string;
  lineNo?: number;
  lastNo?: string;
  developmentNo?: string;
  customerName?: string;
  customerOrderNo?: string;
  warehouseStoreNo?: string;
  deliveryDate?: string;
  poNo?: string;
  customerStyleNo?: string;
  imageUrl?: string;
  englishColor?: string;
  englishMaterial?: string;
  upperMaterial?: string;
  liningMaterial?: string;
  accessory?: string;
  insolePlatform?: string;
  outsole?: string;
  trademark?: string;
  sizeQuantities?: Record<string, number>;
  quantity?: number;
  cartonCount?: number;
  cartonStart?: string;
  cartonEnd?: string;
  boxSpec?: string;
  sourceSheetName?: string;
  rowIndex?: number;
  remark?: string;
  processes?: OrderDetailProcess[];
  createdAt?: string;
}

export interface OrderPackingDetail {
  // 对应 order_packing_detail。
  id: number;
  orderId: number;
  lineNo?: number;
  imageUrl?: string;
  companyStyleNo?: string;
  customerName?: string;
  customerOrderNo?: string;
  warehouseStoreNo?: string;
  poNo?: string;
  customerStyleNo?: string;
  customerColor?: string;
  material?: string;
  itemNumber?: string;
  trademark?: string;
  sizeQuantities?: Record<string, number>;
  cartonCount?: number;
  totalPairs?: number;
  cartonStart?: string;
  cartonEnd?: string;
  sourceSheetName?: string;
  rowIndex?: number;
  remark?: string;
  createdAt?: string;
}

export interface PrintTask {
  // 一行就是一个可打印项，例如订单 sheet 或装箱单 sheet。
  id: number;
  taskNo?: string;
  orderId: number;
  orderNo?: string;
  customerName?: string;
  originalFileName?: string;
  styleNos?: string[];
  totalPairs?: number;
  printType: PrintType;
  printTypeText?: string;
  status: PrintTaskStatus;
  statusText?: string;
  previewUrl?: string;
  printCount?: number;
  pdfGeneratedAt?: string;
  lastPrintTime?: string;
  errorMessage?: string;
  createdAt?: string;
}

export interface PrintPreview {
  // PDF 预览信息，previewUrl 可直接放进 iframe。
  id: number;
  previewNo: string;
  orderId: number;
  orderNo?: string;
  printType: PrintType;
  previewUrl: string;
  status: "READY" | "FAILED";
  errorMessage?: string;
  createdAt?: string;
}

export interface ShippingNoteItem {
  sourceDetailId: number;
  orderId?: number;
  orderNo?: string;
  developmentNo?: string;
  customerName?: string;
  customerStyleNo?: string;
  englishColor?: string;
  englishMaterial?: string;
  colorMaterial?: string;
  trademark?: string;
  sizeQuantities?: Record<string, number>;
  pairCount?: number;
  cartonCount?: number;
  totalPairs?: number;
  cartonStart?: string;
  cartonEnd?: string;
  packingItems?: ShippingNoteItem[];
}

export interface ShippingNoteTask {
  id: number;
  taskNo?: string;
  printNo: string;
  recipientName?: string;
  shippingDate?: string;
  invoiceNos?: string;
  developmentNos?: string;
  itemCount?: number;
  totalPairs?: number;
  totalCartonCount?: number;
  items: ShippingNoteItem[];
  createdAt?: string;
}

export type ShippingNoteRecord = ShippingNoteTask;

export interface ShippingNoteCreatePayload {
  recipientName?: string;
  shippingDate?: string;
  items: ShippingNoteItem[];
}

export interface ShippingNoteQueryParams {
  orderNo?: string;
  developmentNo?: string;
  page?: number;
  size?: number;
}

export type ShippingNoteTaskQueryParams = ShippingNoteQueryParams;
