import request from "../utils/request";
import type { PrintPreview, PrintTask, PrintTaskStatus } from "../types/order";

function normalizeList<T>(data: T[] | { records?: T[]; list?: T[] }): T[] {
  if (Array.isArray(data)) {
    return data;
  }
  return data.records || data.list || [];
}

export async function fetchPrintTasks() {
  const { data } = await request.get<
    PrintTask[] | { records?: PrintTask[]; list?: PrintTask[] }
  >("/print-tasks");
  return normalizeList(data);
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

export async function generatePrintTaskPreview(
  id: number,
  printType: "ORDER" | "PACKING",
) {
  const { data } = await request.post<PrintPreview>(`/print-tasks/${id}/preview`, {
    printType,
  });
  return data;
}
