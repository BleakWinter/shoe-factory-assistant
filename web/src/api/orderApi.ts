import request from "../utils/request";
import type {
  OrderRecord,
  OrderRecordDetail,
  OrderRecordQueryParams,
  OrderUploadResult,
  PageResponse,
} from "../types/order";

function normalizePage<T>(
  data: PageResponse<T> | T[] | { records?: T[]; total?: number },
  page = 1,
  size = 20,
): PageResponse<T> {
  // 后端有些历史接口可能返回数组，有些返回分页对象；前端统一整理成表格可用的分页结构。
  if (Array.isArray(data)) {
    return { records: data, total: data.length, page, size };
  }
  return {
    records: data.records || [],
    total: data.total || data.records?.length || 0,
    page,
    size,
  };
}

export async function uploadOrderFile(file: File) {
  // 上传字段名必须和后端 @RequestPart("file") 对齐。
  const formData = new FormData();
  formData.append("file", file);

  const { data } = await request.post<OrderUploadResult>(
    "/orders/upload",
    formData,
    {
      headers: { "Content-Type": "multipart/form-data" },
    },
  );

  return data;
}

export async function fetchOrders(params: OrderRecordQueryParams) {
  // /orders 返回 order_record 主表分页数据。
  const { data } = await request.get<
    PageResponse<OrderRecord> | OrderRecord[] | { records?: OrderRecord[]; total?: number }
  >("/orders", { params });

  return normalizePage(data, params.page, params.size);
}

export async function fetchOrderDetails(orderId: number) {
  // 明细接口会连同 order_detail_process 一起返回。
  const { data } = await request.get<OrderRecordDetail[]>(`/orders/${orderId}/details`);
  return data || [];
}

export function toAssetUrl(url?: string) {
  if (!url) {
    return "";
  }
  if (/^https?:\/\//.test(url)) {
    return url;
  }
  // 后端返回 /api/orders/details/{id}/image 这类相对地址时，保持由 Vite 代理转发。
  return url.startsWith("/") ? url : `/${url}`;
}
