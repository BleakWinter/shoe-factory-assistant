import request from "../utils/request";
import type {
  DevelopmentNoOption,
  OrderPackingDetail,
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

export async function fetchDevelopmentNoOptions() {
  try {
    const { data } = await request.get<DevelopmentNoOption[]>("/orders/development-options");
    return data || [];
  } catch {
    const page = await fetchOrders({ page: 1, size: 100 });
    return buildDevelopmentNoOptions(page.records);
  }
}

function parseDevelopmentNoParts(value: string) {
  const parts = value
    .trim()
    .split("-")
    .map((part) => part.trim())
    .filter(Boolean);
  if (parts.length >= 3) {
    return parts.slice(-3);
  }
  return parts;
}

function appendDevelopmentNoOption(nodes: DevelopmentNoOption[], parts: string[], path: string[] = []) {
  if (parts.length === 0) {
    return;
  }
  const [part, ...rest] = parts;
  const nextPath = [...path, part];
  let node = nodes.find((item) => item.label === part);
  if (!node) {
    node = {
      value: nextPath.join("-"),
      label: part,
      children: rest.length > 0 ? [] : undefined,
    };
    nodes.push(node);
    nodes.sort(sortDevelopmentNoOptions);
  }
  if (rest.length > 0) {
    if (!node.children) {
      node.children = [];
    }
    appendDevelopmentNoOption(node.children, rest, nextPath);
  }
}

function sortDevelopmentNoOptions(left: DevelopmentNoOption, right: DevelopmentNoOption) {
  return left.label.localeCompare(right.label, "zh-CN", { numeric: true });
}

function buildDevelopmentNoOptions(records: OrderRecord[]) {
  const options: DevelopmentNoOption[] = [];
  records.forEach((record) => {
    record.developmentNoList
      ?.filter((item) => item && item.trim())
      .forEach((developmentNo) => appendDevelopmentNoOption(options, parseDevelopmentNoParts(developmentNo)));
  });
  return options;
}

export async function fetchOrderDetails(orderId: number) {
  // 明细接口会连同 order_detail_process 一起返回。
  const { data } = await request.get<OrderRecordDetail[]>(`/orders/${orderId}/details`);
  return data || [];
}

export async function fetchOrderPackingDetails(orderId: number) {
  const { data } = await request.get<OrderPackingDetail[]>(`/orders/${orderId}/packing-details`);
  return data || [];
}

export async function recognizeOrder(orderId: number) {
  const { data } = await request.post<OrderRecord>(`/orders/${orderId}/recognize-order`);
  return data;
}

export async function recognizePacking(orderId: number) {
  const { data } = await request.post<OrderRecord>(`/orders/${orderId}/recognize-packing`);
  return data;
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
