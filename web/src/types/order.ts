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
  | "PRINTING"
  | "SUCCESS"
  | "FAILED"
  | "CANCELED";

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
  originalFileName?: string;
  boxImageUrl?: string;
  developmentNos?: string;
  developmentNoList?: string[];
  orderPrinted?: boolean;
  packingPrinted?: boolean;
  orderPdfPath?: string;
  packingPdfPath?: string;
  orderPdfGeneratedAt?: string;
  packingPdfGeneratedAt?: string;
  totalQuantity?: number;
  totalCartonCount?: number;
  sourceType?: number;
  sourceTypeText?: string;
  recognitionStatus?: number;
  recognitionStatusText?: string;
  remark?: string;
  errorMessage?: string;
  createdAt?: string;
}

export interface OrderRecordQueryParams {
  // 订单主表筛选参数，字段名和后端 /api/orders 对齐。
  orderNo?: string;
  customerName?: string;
  developmentNo?: string;
  recognitionStatus?: string;
  page?: number;
  size?: number;
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
  pairs?: number;
  cartonCount?: number;
  totalPairs?: number;
  cartonStart?: string;
  cartonEnd?: string;
  lengthValue?: string;
  widthValue?: string;
  heightValue?: string;
  netWeight?: string;
  grossWeight?: string;
  measurement?: string;
  totalNetWeight?: string;
  totalGrossWeight?: string;
  totalCbm?: string;
  gender?: string;
  productType?: string;
  upperMaterial?: string;
  soleMaterial?: string;
  sourceSheetName?: string;
  rowIndex?: number;
  remark?: string;
  createdAt?: string;
}

export interface PrintTask {
  // 兼容原打印列表类型；现在每一行实际来自 order_record。
  id: number;
  taskNo?: string;
  orderId: number;
  orderNo?: string;
  customerName?: string;
  styleNos?: string[];
  totalPairs?: number;
  orderPrinted?: boolean;
  packingPrinted?: boolean;
  status: PrintTaskStatus;
  previewUrl?: string;
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
