import request from "../utils/request";
import type {
  ComponentOrderCreatePayload,
  ComponentOrderTask,
  ComponentOrderTaskQueryParams,
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

export async function createComponentOrderTask(payload: ComponentOrderCreatePayload) {
  const { data } = await request.post<ComponentOrderTask>("/component-order-tasks", payload);
  return data;
}

export async function fetchComponentOrderTasks(params: ComponentOrderTaskQueryParams) {
  const { data } = await request.get<
    PageResponse<ComponentOrderTask> | ComponentOrderTask[] | { records?: ComponentOrderTask[]; total?: number }
  >("/component-order-tasks", { params });
  return normalizePage(data, params.page, params.size);
}

export async function fetchComponentOrderTask(id: number) {
  const { data } = await request.get<ComponentOrderTask>(`/component-order-tasks/${id}`);
  return data;
}
