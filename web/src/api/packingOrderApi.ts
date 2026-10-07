import request from "../utils/request";
import type { PageResponse } from "../types/order";
import type { PackingOrderCreatePayload, PackingOrderItem, PackingOrderQueryParams, PackingOrderTask } from "../types/packingOrder";

export async function previewPackingOrder(sourceDetailIds: number[]) {
  const { data } = await request.post<PackingOrderItem[]>("/packing-order-tasks/preview", { sourceDetailIds });
  return data;
}

export async function createPackingOrderTask(payload: PackingOrderCreatePayload) {
  const { data } = await request.post<PackingOrderTask>("/packing-order-tasks", payload);
  return data;
}

export async function fetchPackingOrderTasks(params: PackingOrderQueryParams) {
  const { data } = await request.get<PageResponse<PackingOrderTask>>("/packing-order-tasks", { params });
  return data;
}

export async function fetchPackingOrderTask(id: number) {
  const { data } = await request.get<PackingOrderTask>(`/packing-order-tasks/${id}`);
  return data;
}
