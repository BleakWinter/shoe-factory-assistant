import request from "../utils/request";
import type {
  OrderQueryParams,
  OrderRecord,
  PageResponse,
  PrintPreview,
  PrintType,
} from "../types/order";

export async function uploadOrderSource(file: File) {
  const formData = new FormData();
  formData.append("file", file);
  const { data } = await request.post<OrderRecord>("/orders/upload", formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
  return data;
}

export async function fetchOrders(params: OrderQueryParams) {
  const { data } = await request.get<PageResponse<OrderRecord>>("/orders", {
    params,
  });
  return data;
}

export async function generatePrintPreview(orderId: number, printType: PrintType) {
  const { data } = await request.post<PrintPreview>(
    `/orders/${orderId}/print-previews`,
    { printType },
  );
  return data;
}

export function toPreviewUrl(url?: string) {
  if (!url) {
    return "";
  }
  if (/^https?:\/\//.test(url)) {
    return url;
  }
  return url.startsWith("/") ? url : `/${url}`;
}
