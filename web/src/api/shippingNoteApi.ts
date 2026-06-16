import request from "../utils/request";
import type {
  PageResponse,
  ShippingNoteCreatePayload,
  ShippingNoteQueryParams,
  ShippingNoteTask,
  ShippingNoteTaskQueryParams,
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

export async function createShippingNoteTask(payload: ShippingNoteCreatePayload) {
  const { data } = await request.post<ShippingNoteTask>("/shipping-note-tasks", payload);
  return data;
}

export async function fetchShippingNoteTasks(params: ShippingNoteTaskQueryParams) {
  const { data } = await request.get<
    PageResponse<ShippingNoteTask> | ShippingNoteTask[] | { records?: ShippingNoteTask[]; total?: number }
  >("/shipping-note-tasks", { params });

  return normalizePage(data, params.page, params.size);
}

export async function fetchShippingNoteTask(id: number) {
  const { data } = await request.get<ShippingNoteTask>(`/shipping-note-tasks/${id}`);
  return data;
}

export async function updateShippingNoteTask(id: number, payload: { recipientName?: string; shippingDate?: string }) {
  const { data } = await request.put<ShippingNoteTask>(`/shipping-note-tasks/${id}`, payload);
  return data;
}

export const createShippingNote = createShippingNoteTask;
export const fetchShippingNotes = fetchShippingNoteTasks as (params: ShippingNoteQueryParams) => Promise<PageResponse<ShippingNoteTask>>;
