import request from "../utils/request";
import type {
  OrderLine,
  OrderLineQueryParams,
  OrderUploadResult,
  PageResponse,
} from "../types/order";

function normalizePage<T>(
  data: PageResponse<T> | T[] | { records?: T[]; total?: number },
  page = 1,
  size = 20,
): PageResponse<T> {
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

export async function fetchOrderLines(params: OrderLineQueryParams) {
  const { data } = await request.get<
    PageResponse<OrderLine> | OrderLine[] | { records?: OrderLine[]; total?: number }
  >("/orders/lines", { params });

  return normalizePage(data, params.page, params.size);
}

export function toAssetUrl(url?: string) {
  if (!url) {
    return "";
  }
  if (/^https?:\/\//.test(url)) {
    return url;
  }
  return url.startsWith("/") ? url : `/${url}`;
}
