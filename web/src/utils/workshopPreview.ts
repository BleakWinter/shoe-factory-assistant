// 独立的交互预览数据，不读取或修改正式订单、工资和报价。
export type Department = "面部" | "底部" | "开料" | "包装";
export type Stage = "upper" | "lasting" | "bonding" | "packing";
export interface WorkshopProcess {
  id: string;
  name: string;
  department: Department;
  stage?: Stage;
}

export const processes: WorkshopProcess[] = [
  { id: "cutting", name: "开料", department: "开料" },
  { id: "preparation", name: "铲皮 / 画线 / 打码", department: "面部" },
  { id: "sewing", name: "鞋面车缝", department: "面部" },
  { id: "upper-finish", name: "面部收尾交接", department: "面部", stage: "upper" },
  { id: "lasting", name: "前段完工", department: "底部", stage: "lasting" },
  { id: "bonding", name: "贴底完工", department: "底部", stage: "bonding" },
  { id: "packing", name: "包装完工", department: "包装", stage: "packing" },
];

export const departments: Department[] = ["面部", "底部", "开料", "包装"];
export const stageNames: Record<Stage, string> = {
  upper: "面部", lasting: "前段", bonding: "贴底", packing: "包装",
};

export interface WorkshopEmployee {
  id: string;
  number: string;
  name: string;
  department: Department;
  processId: string;
  guarantee: number;
}

export interface PieceworkEntry {
  id: string;
  batchId?: string;
  orderNo: string;
  styleNo: string;
  color: string;
  sizes: string;
  processId: string;
  quantity: number | null;
  unitPrice: number | null;
  progressConfirmed: boolean;
}

export interface WorkshopLedger {
  employeeId: string;
  month: string;
  guarantee: number;
  status: "draft" | "checked";
  entries: PieceworkEntry[];
}

export interface WorkshopBatch {
  id: string;
  customer: string;
  orderNo: string;
  styleNo: string;
  color: string;
  sizes: string;
  quantity: number;
  createdMonth: string;
  deliveryDate: string;
  note: string;
}

export const previewBatches: WorkshopBatch[] = [
  { id: "batch-a", customer: "示例客户 A", orderNo: "205-9-11", styleNo: "26137", color: "黑色网鞋", sizes: "5–11", quantity: 1000, createdMonth: "2026-09", deliveryDate: "2026-11-08", note: "中底尚缺 200 双，按码数配套后上前段" },
  { id: "batch-b", customer: "示例客户 A", orderNo: "253-1-67", styleNo: "26145", color: "枪色羊皮", sizes: "6–10", quantity: 720, createdMonth: "2026-10", deliveryDate: "2026-11-06", note: "前段已完成，大底和鞋跟待到厂" },
  { id: "batch-c", customer: "示例客户 B", orderNo: "253-1-64", styleNo: "26144", color: "黑色羊皮", sizes: "6–10", quantity: 480, createdMonth: "2026-10", deliveryDate: "2026-11-10", note: "面部已完成，中底包边外发尚未回厂" },
  { id: "batch-d", customer: "示例客户 B", orderNo: "391-2-02", styleNo: "B2658", color: "杏色", sizes: "7–11", quantity: 600, createdMonth: "2026-09", deliveryDate: "2026-11-03", note: "内盒与贴标待到，已包装 360 双" },
  { id: "batch-e", customer: "示例客户 C", orderNo: "379-1-12", styleNo: "26170", color: "灰色", sizes: "5–10", quantity: 360, createdMonth: "2026-10", deliveryDate: "2026-11-12", note: "面部继续生产，已交接部分先做" },
];

export interface WorkshopHandoff {
  batchId: string;
  month: string;
  lastingReceived: number;
  bondingReceived: number;
  packingReceived: number;
  note: string;
}

export interface WorkshopPreviewState {
  version: 1;
  employees: WorkshopEmployee[];
  ledgers: WorkshopLedger[];
  handoffs: WorkshopHandoff[];
}

export const workshopPreviewStorageKey = "shoe-factory.workshop-preview.v1";
export const previewMonth = "2026-10";
export const processById = (id: string) => processes.find(process => process.id === id);
export const ledgerKey = (employeeId: string, month: string) => `${employeeId}:${month}`;
export const countText = (value: number) => value.toLocaleString("zh-CN");
export const amountText = (value: number) => value.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
export const newPreviewId = () => crypto.randomUUID();

// 单价保留四位，每一行四舍五入至分，再按分累加。
export function entryCents(entry: PieceworkEntry): number {
  if (entry.quantity === null || entry.unitPrice === null) return 0;
  return Math.round(entry.quantity * Math.round(entry.unitPrice * 10000) / 100);
}

export function ledgerAmount(ledger: WorkshopLedger): number {
  return ledger.entries.reduce((total, entry) => total + entryCents(entry), 0) / 100;
}

export function getLedger(state: WorkshopPreviewState, employee: WorkshopEmployee, month: string): WorkshopLedger {
  return state.ledgers.find(ledger => ledger.employeeId === employee.id && ledger.month === month)
    ?? { employeeId: employee.id, month, guarantee: employee.guarantee, status: "draft", entries: [] };
}

export function invalidEntry(entry: PieceworkEntry): boolean {
  return !entry.styleNo.trim() || !processById(entry.processId)
    || entry.quantity === null || !Number.isInteger(entry.quantity) || entry.quantity <= 0
    || entry.unitPrice === null || !Number.isFinite(entry.unitPrice) || entry.unitPrice < 0;
}

export function emptyEntry(employee: WorkshopEmployee): PieceworkEntry {
  return { id: newPreviewId(), orderNo: "", styleNo: "", color: "", sizes: "", processId: employee.processId, quantity: null, unitPrice: null, progressConfirmed: false };
}

export function getHandoff(state: WorkshopPreviewState, batch: WorkshopBatch, month: string): WorkshopHandoff {
  return state.handoffs.filter(item => item.batchId === batch.id && item.month <= month)
    .sort((left, right) => right.month.localeCompare(left.month))[0]
    ?? { batchId: batch.id, month, lastingReceived: 0, bondingReceived: 0, packingReceived: 0, note: "尚未登记交接" };
}

export function batchProgress(state: WorkshopPreviewState, batch: WorkshopBatch, month: string) {
  const completed: Record<Stage, number> = { upper: 0, lasting: 0, bonding: 0, packing: 0 };
  const sources: { entry: PieceworkEntry; employee: WorkshopEmployee; month: string; stage: Stage }[] = [];
  for (const ledger of state.ledgers) {
    if (ledger.month > month) continue;
    const employee = state.employees.find(item => item.id === ledger.employeeId);
    if (!employee) continue;
    for (const entry of ledger.entries) {
      const stage = processById(entry.processId)?.stage;
      if (entry.batchId !== batch.id || !stage || !entry.progressConfirmed || invalidEntry(entry)) continue;
      completed[stage] += entry.quantity!;
      sources.push({ entry, employee, month: ledger.month, stage });
    }
  }
  const handoff = getHandoff(state, batch, month);
  const waitingLasting = Math.max(0, completed.upper - handoff.lastingReceived);
  const waitingBonding = Math.max(0, completed.lasting - handoff.bondingReceived);
  const issues = [
    ...Object.entries(completed).filter(([, quantity]) => quantity > batch.quantity).map(([stage]) => `${stageNames[stage as Stage]}数量超过订单`),
    handoff.lastingReceived > completed.upper ? "前段接收数超过面部已登记完工数" : "",
    handoff.bondingReceived > completed.lasting ? "贴底接收数超过前段已登记完工数" : "",
    handoff.packingReceived > completed.bonding ? "包装接收数超过贴底已登记完工数" : "",
    completed.lasting > handoff.lastingReceived ? "前段完工数超过接收数" : "",
    completed.bonding > handoff.bondingReceived ? "贴底完工数超过接收数" : "",
    completed.packing > handoff.packingReceived ? "包装完工数超过接收数" : "",
  ].filter(Boolean);
  return { batch, completed, handoff, waitingLasting, waitingBonding, sources, issues };
}

export type BatchProgress = ReturnType<typeof batchProgress>;

export function createWorkshopPreview(): WorkshopPreviewState {
  const employees: WorkshopEmployee[] = [
    { id: "emp-1", number: "YG001", name: "陈小梅", department: "面部", processId: "sewing", guarantee: 4500 },
    { id: "emp-2", number: "YG002", name: "林秀英", department: "面部", processId: "upper-finish", guarantee: 4800 },
    { id: "emp-3", number: "YG003", name: "黄晓红", department: "面部", processId: "preparation", guarantee: 4200 },
    { id: "emp-4", number: "YG004", name: "王建国", department: "底部", processId: "lasting", guarantee: 5500 },
    { id: "emp-5", number: "YG005", name: "李志强", department: "底部", processId: "bonding", guarantee: 6000 },
    { id: "emp-6", number: "YG006", name: "周海平", department: "底部", processId: "lasting", guarantee: 5200 },
    { id: "emp-7", number: "YG007", name: "赵海", department: "开料", processId: "cutting", guarantee: 4800 },
    { id: "emp-8", number: "YG008", name: "吴春兰", department: "包装", processId: "packing", guarantee: 3800 },
  ];
  let entrySequence = 0;
  function entry(batchId: string, processId: string, quantity: number, unitPrice: number): PieceworkEntry {
    const batch = previewBatches.find(item => item.id === batchId)!;
    return { id: `seed-entry-${++entrySequence}`, batchId, orderNo: batch.orderNo, styleNo: batch.styleNo, color: batch.color, sizes: batch.sizes, processId, quantity, unitPrice, progressConfirmed: Boolean(processById(processId)?.stage) };
  }
  function ledger(employeeId: string, month: string, entries: PieceworkEntry[], checked = false): WorkshopLedger {
    return { employeeId, month, entries, guarantee: employees.find(employee => employee.id === employeeId)!.guarantee, status: checked ? "checked" : "draft" };
  }
  return {
    version: 1,
    employees,
    ledgers: [
      ledger("emp-1", previewMonth, [entry("batch-a", "sewing", 100, 14), entry("batch-b", "sewing", 148, 15), entry("batch-c", "sewing", 44, 9), entry("batch-e", "sewing", 120, 10)], true),
      ledger("emp-2", "2026-09", [entry("batch-a", "upper-finish", 400, 3.5), entry("batch-d", "upper-finish", 600, 3.2)], true),
      ledger("emp-2", previewMonth, [entry("batch-a", "upper-finish", 600, 3.5), entry("batch-b", "upper-finish", 720, 3.2), entry("batch-c", "upper-finish", 480, 3.5), entry("batch-e", "upper-finish", 240, 3.2)], true),
      ledger("emp-3", previewMonth, [entry("batch-a", "preparation", 600, 1.5), entry("batch-b", "preparation", 720, 1.8)]),
      ledger("emp-4", "2026-09", [entry("batch-a", "lasting", 200, 5), entry("batch-d", "lasting", 600, 5.5)], true),
      ledger("emp-4", previewMonth, [entry("batch-a", "lasting", 400, 5), entry("batch-b", "lasting", 720, 5.5)], true),
      ledger("emp-5", "2026-09", [entry("batch-d", "bonding", 600, 6)], true),
      ledger("emp-5", previewMonth, [entry("batch-a", "bonding", 400, 6), entry("batch-e", "bonding", 120, 5.5)]),
      ledger("emp-6", previewMonth, [entry("batch-e", "lasting", 120, 5)]),
      ledger("emp-8", "2026-09", [entry("batch-d", "packing", 120, 1.2)], true),
      ledger("emp-8", previewMonth, [entry("batch-d", "packing", 240, 1.2)]),
    ],
    handoffs: [
      { batchId: "batch-a", month: "2026-09", lastingReceived: 300, bondingReceived: 0, packingReceived: 0, note: "等待余下中底配套" },
      { batchId: "batch-d", month: "2026-09", lastingReceived: 600, bondingReceived: 600, packingReceived: 600, note: "包装分批完成" },
      ...previewBatches.map((batch, index) => ({ batchId: batch.id, month: previewMonth, lastingReceived: [800, 720, 0, 600, 120][index], bondingReceived: [600, 0, 0, 600, 120][index], packingReceived: [0, 0, 0, 600, 0][index], note: batch.note })),
    ],
  };
}

export function loadWorkshopPreview(): WorkshopPreviewState {
  try {
    const raw = localStorage.getItem(workshopPreviewStorageKey);
    if (raw) {
      const value = JSON.parse(raw) as WorkshopPreviewState;
      if (value.version === 1 && Array.isArray(value.employees) && Array.isArray(value.ledgers) && Array.isArray(value.handoffs)) return value;
    }
  } catch {
    // 浏览器不允许存储时仍可查看并操作本次预览。
  }
  return createWorkshopPreview();
}
