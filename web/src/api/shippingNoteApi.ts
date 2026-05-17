import request from "../utils/request";
import type {
  PageResponse,
  ShippingNoteCreatePayload,
  ShippingNoteQueryParams,
  ShippingNoteRecord,
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

export async function createShippingNote(payload: ShippingNoteCreatePayload) {
  const { data } = await request.post<ShippingNoteRecord>("/shipping-notes", payload);
  return data;
}

export async function fetchShippingNotes(params: ShippingNoteQueryParams) {
  const { data } = await request.get<
    PageResponse<ShippingNoteRecord> | ShippingNoteRecord[] | { records?: ShippingNoteRecord[]; total?: number }
  >("/shipping-notes", { params });

  return normalizePage(data, params.page, params.size);
}
