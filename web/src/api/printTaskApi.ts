import request from "../utils/request";
import type { PrintPreview, PrintTask, PrintTaskStatus, PrintType } from "../types/order";

function normalizeList<T>(data: T[] | { records?: T[]; list?: T[] }): T[] {
  // 兼容数组、分页 records、普通 list 三种返回形态。
  if (Array.isArray(data)) {
    return data;
  }
  return data.records || data.list || [];
}

export async function fetchPrintTasks() {
  // 打印列表页面加载所有任务。
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
  // 预留给后续本地打印代理或页面操作更新任务状态。
  const { data } = await request.patch<PrintTask>(`/print-tasks/${id}/status`, {
    status,
    errorMessage,
  });
  return data;
}

export async function generatePrintTaskPreview(
  id: number,
  printType: PrintType,
) {
  // 点“订单/装箱单”时才调用，后端会按 printType 生成 PDF。
  const { data } = await request.post<PrintPreview>(`/print-tasks/${id}/preview`, {
    printType,
  });
  return data;
}

export async function regeneratePrintTaskPreview(
  id: number,
  printType: PrintType,
) {
  // 强制重新生成：后端会先删除旧 PDF 并清空订单上的 PDF 路径。
  const { data } = await request.post<PrintPreview>(
    `/print-tasks/${id}/preview/regenerate`,
    { printType },
  );
  return data;
}

export async function markPrintTaskPrinted(
  id: number,
  printType: PrintType,
) {
  // 页面手动确认某一种单据已经打印。
  const { data } = await request.patch<PrintTask>(`/print-tasks/${id}/printed`, {
    printType,
  });
  return data;
}
