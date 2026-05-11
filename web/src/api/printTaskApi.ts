import request from "../utils/request";
import type { PrintTask, PrintTaskStatus } from "../types/order";

export interface CreatePrintTaskPayload {
  previewId: number;
  copies: number;
  printerName?: string;
  priority?: number;
}

export async function createPrintTask(payload: CreatePrintTaskPayload) {
  const { data } = await request.post<PrintTask>("/print-tasks", payload);
  return data;
}

export async function fetchPendingTasks(limit = 50) {
  const { data } = await request.get<PrintTask[]>("/print-tasks/pending", {
    params: { limit },
  });
  return data;
}

export async function updatePrintTaskStatus(
  id: number,
  status: PrintTaskStatus,
  errorMessage?: string,
) {
  const { data } = await request.patch<PrintTask>(`/print-tasks/${id}/status`, {
    status,
    errorMessage,
  });
  return data;
}
