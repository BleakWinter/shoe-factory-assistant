import {
  ArrowRightOutlined, CheckCircleFilled, CheckOutlined, DeleteOutlined,
  EditOutlined, InfoCircleOutlined, PlusOutlined, SearchOutlined, TeamOutlined,
} from "@ant-design/icons";
import {
  Alert, App, AutoComplete, Avatar, Button, Drawer, Empty, Form, Input, InputNumber,
  Modal, Popover, Select, Space, Table, Tabs, Tag, Tooltip,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo, useState } from "react";
import type { ReactNode } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import {
  amountText, batchProgress, countText, departments, emptyEntry, entryCents,
  getLedger, invalidEntry, ledgerAmount, loadWorkshopPreview, newPreviewId,
  previewBatches, previewMonth, processById, processes, stageNames,
  workshopPreviewStorageKey,
} from "../utils/workshopPreview";
import type {
  BatchProgress, Department, PieceworkEntry, Stage, WorkshopBatch, WorkshopEmployee,
  WorkshopHandoff, WorkshopLedger, WorkshopPreviewState,
} from "../utils/workshopPreview";
import "./workshopPreview.css";

type EmployeeRow = WorkshopEmployee & { ledger: WorkshopLedger; hasLedger: boolean; amount: number };
type ProgressFilter = "all" | "waiting-lasting" | "waiting-bonding" | "issues";

function monthLabel(month: string) {
  const [year, value] = month.split("-");
  return `${year}年${Number(value)}月`;
}

function nextMonthLabel(month: string) {
  const [year, value] = month.split("-").map(Number);
  return `${value === 12 ? year + 1 : year}年${value === 12 ? 1 : value + 1}月发放`;
}

function DepartmentTag({ department }: { department: Department }) {
  return <Tag className={`wk-department wk-department-${departments.indexOf(department)}`}>{department}</Tag>;
}

function CompletionCell({ quantity, total, color }: { quantity: number; total: number; color: string }) {
  return <div className={`wk-completion ${quantity > total ? "wk-completion-error" : ""}`}>
    <div><strong>{countText(quantity)}</strong><span> / {countText(total)}</span></div>
    <div className="wk-meter"><span style={{ width: `${Math.min(100, quantity / total * 100)}%`, background: color }} /></div>
  </div>;
}

export default function WorkshopPreviewPage() {
  const { message, modal } = App.useApp();
  const navigate = useNavigate();
  const location = useLocation();
  const progressView = location.pathname.endsWith("/progress");
  const [state, setState] = useState<WorkshopPreviewState>(loadWorkshopPreview);
  const [month, setMonth] = useState(previewMonth);
  const [department, setDepartment] = useState<Department | "all">("all");
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("all");
  const [progressFilter, setProgressFilter] = useState<ProgressFilter>("all");
  const [selectedEmployee, setSelectedEmployee] = useState<WorkshopEmployee>();
  const [draft, setDraft] = useState<WorkshopLedger>();
  const [dirty, setDirty] = useState(false);
  const [showErrors, setShowErrors] = useState(false);
  const [expandedEntries, setExpandedEntries] = useState<string[]>([]);
  const [guaranteeOpen, setGuaranteeOpen] = useState(false);
  const [wageResultOpen, setWageResultOpen] = useState(false);
  const [employeeModal, setEmployeeModal] = useState(false);
  const [editingEmployee, setEditingEmployee] = useState<WorkshopEmployee>();
  const [employeeForm] = Form.useForm<WorkshopEmployee>();
  const formDepartment = Form.useWatch("department", employeeForm) as Department | undefined;
  const [selectedBatch, setSelectedBatch] = useState<WorkshopBatch>();
  const [handoffDraft, setHandoffDraft] = useState<WorkshopHandoff>();

  function persist(next: WorkshopPreviewState, success?: string) {
    setState(next);
    try {
      localStorage.setItem(workshopPreviewStorageKey, JSON.stringify(next));
      if (success) message.success(success);
    } catch {
      message.warning("本次修改已显示，但浏览器无法保存；刷新后可能丢失。");
    }
  }

  const employees = useMemo<EmployeeRow[]>(() => state.employees.map(employee => {
    const ledger = getLedger(state, employee, month);
    return { ...employee, ledger, hasLedger: ledger.entries.length > 0, amount: ledgerAmount(ledger) };
  }), [month, state]);
  const filteredEmployees = employees.filter(employee => {
    const matchesSearch = `${employee.name} ${employee.number}`.toLowerCase().includes(search.trim().toLowerCase());
    const matchesStatus = status === "all" || (status === "empty" ? !employee.hasLedger : employee.hasLedger && employee.ledger.status === status);
    return matchesSearch && matchesStatus && (department === "all" || employee.department === department);
  });
  const progressRows = useMemo(() => previewBatches.filter(batch => batch.createdMonth <= month)
    .map(batch => batchProgress(state, batch, month)), [month, state]);
  const filteredProgress = progressRows.filter(row => {
    const matchesSearch = `${row.batch.orderNo} ${row.batch.styleNo} ${row.batch.customer} ${row.batch.color}`.toLowerCase().includes(search.trim().toLowerCase());
    return matchesSearch && (progressFilter === "all"
      || (progressFilter === "waiting-lasting" && row.waitingLasting > 0)
      || (progressFilter === "waiting-bonding" && row.waitingBonding > 0)
      || (progressFilter === "issues" && row.issues.length > 0));
  });
  const currentBatchProgress = selectedBatch ? batchProgress(state, selectedBatch, month) : undefined;
  const draftAmount = draft ? ledgerAmount(draft) : 0;
  const waitingLasting = progressRows.reduce((total, row) => total + row.waitingLasting, 0);
  const waitingBonding = progressRows.reduce((total, row) => total + row.waitingBonding, 0);

  function openLedger(employee: WorkshopEmployee, sourceState = state) {
    const ledger = getLedger(sourceState, employee, month);
    setSelectedEmployee(employee);
    setDraft({ ...ledger, entries: ledger.entries.map(entry => ({ ...entry })) });
    setDirty(false);
    setShowErrors(false);
    setExpandedEntries([]);
    setGuaranteeOpen(false);
    setWageResultOpen(false);
  }

  function updateDraftEntry(id: string, changes: Partial<PieceworkEntry>, keepConfirmation = false) {
    setDraft(current => current && ({
      ...current,
      status: "draft",
      entries: current.entries.map(entry => entry.id === id
        ? { ...entry, ...changes, progressConfirmed: keepConfirmation ? (changes.progressConfirmed ?? entry.progressConfirmed) : false }
        : entry),
    }));
    setDirty(true);
  }

  function addEntry() {
    if (!selectedEmployee) return;
    const entry = emptyEntry(selectedEmployee);
    setDraft(current => current && ({ ...current, status: "draft", entries: [...current.entries, entry] }));
    setDirty(true);
    requestAnimationFrame(() => {
      const row = Array.from(document.querySelectorAll(`[data-row-key="${entry.id}"]`)).find(element => element.getClientRects().length > 0);
      row?.querySelector<HTMLInputElement>('input[aria-label="款号"]')?.focus();
    });
  }

  function saveLedger(checked: boolean, close = false) {
    if (!draft || !selectedEmployee) return false;
    if (checked && (!draft.entries.length || draft.entries.some(invalidEntry))) {
      setShowErrors(true);
      message.warning("请把每笔的款号、数量和单价填完整，再算工资。");
      return false;
    }
    const saved: WorkshopLedger = {
      ...draft,
      status: checked ? "checked" : "draft",
      entries: draft.entries.map(entry => ({ ...entry,
        progressConfirmed: Boolean(checked && entry.batchId && processById(entry.processId)?.stage && !invalidEntry(entry)) || entry.progressConfirmed,
      })),
    };
    const next = { ...state, ledgers: [...state.ledgers.filter(ledger => !(ledger.employeeId === saved.employeeId && ledger.month === saved.month)), saved] };
    persist(next, checked ? "计件已保存，工资已算好" : "已保存，下次可以接着录");
    setDraft(saved);
    setDirty(false);
    if (close) setSelectedEmployee(undefined);
    return true;
  }

  function closeLedger() {
    if (!dirty) { setSelectedEmployee(undefined); return; }
    modal.confirm({
      title: "保存本次录入？", content: "当前计件明细还有未保存的修改。",
      okText: "保存草稿并关闭", cancelText: "继续录入",
      onOk: () => { saveLedger(false, true); },
    });
  }

  function openEmployeeForm(employee?: WorkshopEmployee) {
    setEditingEmployee(employee);
    employeeForm.resetFields();
    employeeForm.setFieldsValue(employee ?? { name: "", department: "面部", processId: "sewing", guarantee: 4500 });
    setEmployeeModal(true);
  }

  async function saveEmployee() {
    const values = await employeeForm.validateFields();
    const nextNumber = Math.max(0, ...state.employees.map(employee => Number(employee.number.replace(/\D/g, "")) || 0)) + 1;
    const employee: WorkshopEmployee = { ...values, name: values.name.trim(), id: editingEmployee?.id ?? newPreviewId(), number: editingEmployee?.number ?? `YG${String(nextNumber).padStart(3, "0")}` };
    const next = { ...state, employees: editingEmployee ? state.employees.map(item => item.id === employee.id ? employee : item) : [...state.employees, employee] };
    persist(next, editingEmployee ? "员工资料已保存" : "员工已新增，可以开始录入计件");
    setEmployeeModal(false);
    if (editingEmployee && selectedEmployee?.id === employee.id) setSelectedEmployee(employee);
    if (!editingEmployee) {
      setDepartment("all"); setSearch(""); setStatus("all");
      openLedger(employee, next);
    }
  }

  function openBatch(batch: WorkshopBatch) {
    setSelectedBatch(batch);
    setHandoffDraft({ ...batchProgress(state, batch, month).handoff, month });
  }

  function saveHandoff() {
    if (!handoffDraft || !selectedBatch) return;
    const values = [handoffDraft.lastingReceived, handoffDraft.bondingReceived, handoffDraft.packingReceived];
    if (values.some(value => !Number.isInteger(value) || value < 0 || value > selectedBatch.quantity)) {
      message.warning("接收数量应为不超过订单数量的非负整数。"); return;
    }
    const next = { ...state, handoffs: [...state.handoffs.filter(item => !(item.batchId === handoffDraft.batchId && item.month === month)), handoffDraft] };
    persist(next, "本月累计交接数量已更新");
  }

  const employeeColumns: ColumnsType<EmployeeRow> = [
    { title: "员工", key: "employee", width: 174, render: (_, employee) => <button className="wk-person" onClick={() => openLedger(employee)}>
      <Avatar shape="square" className={`wk-avatar wk-avatar-${departments.indexOf(employee.department)}`}>{employee.name.slice(-2)}</Avatar>
      <span><strong>{employee.name}</strong><small>{employee.number}</small></span>
    </button> },
    { title: "所属部门 / 工序", key: "department", width: 186, render: (_, employee) => <div className="wk-department-cell"><DepartmentTag department={employee.department} /><span>{processById(employee.processId)?.name}</span></div> },
    { title: "保底 / 月", key: "guarantee", align: "right", width: 120, render: (_, employee) => <span className="wk-number">{amountText(employee.ledger.guarantee)}</span> },
    { title: "录了几笔", key: "entries", align: "center", width: 100, render: (_, employee) => employee.hasLedger ? <span>{employee.ledger.entries.length} 笔</span> : <span className="wk-muted">—</span> },
    { title: "本月计件金额", key: "amount", align: "right", width: 140, render: (_, employee) => employee.hasLedger ? <strong className="wk-number">{amountText(employee.amount)}</strong> : <span className="wk-muted">—</span> },
    { title: <Space size={5}>应发试算<Popover content="按计件合计与本月核定保底取较高值，最终金额需核对出勤后确认。"><InfoCircleOutlined className="wk-muted" /></Popover></Space>, key: "pay", align: "right", width: 140, render: (_, employee) => employee.hasLedger ? <span className={`wk-number ${employee.amount >= employee.ledger.guarantee ? "wk-money-positive" : ""}`}>{amountText(Math.max(employee.amount, employee.ledger.guarantee))}</span> : <span className="wk-muted">待录入</span> },
    { title: "录入情况", key: "status", align: "center", width: 110, render: (_, employee) => !employee.hasLedger ? <Tag className="wk-status-empty">还没录</Tag> : employee.ledger.status === "checked" ? <Tag color="success" icon={<CheckOutlined />}>已算过</Tag> : <Tag color="gold">录了还没算</Tag> },
    { title: "操作", key: "action", fixed: "right", align: "right", width: 134, render: (_, employee) => <Button type="link" onClick={() => openLedger(employee)}>给他录计件<ArrowRightOutlined /></Button> },
  ];

  const entryColumns: ColumnsType<PieceworkEntry> = [
    { title: "序号", key: "index", width: 48, fixed: "left", align: "center", render: (_, __, index) => <span className="wk-muted">{index + 1}</span> },
    { title: "型体 / 款号", key: "style", width: 126, fixed: "left", render: (_, entry) => <Input aria-label="款号" value={entry.styleNo} placeholder="照纸单填写" status={showErrors && !entry.styleNo.trim() ? "error" : undefined} onChange={event => updateDraftEntry(entry.id, { styleNo: event.target.value, batchId: undefined })} /> },
    { title: "指令单号 / 关联订单", key: "order", width: 188, render: (_, entry) => <AutoComplete aria-label="关联订单" allowClear value={entry.orderNo} placeholder="填写单号，或选已有订单" className="wk-full" options={previewBatches.filter(batch => batch.createdMonth <= month).map(batch => ({ value: batch.orderNo, label: `${batch.orderNo} · ${batch.styleNo}` }))} filterOption={(input, option) => String(option?.label ?? "").toLowerCase().includes(input.toLowerCase())} onChange={value => updateDraftEntry(entry.id, { orderNo: value, batchId: undefined })} onSelect={value => {
      const batch = previewBatches.find(item => item.orderNo === value);
      if (batch) updateDraftEntry(entry.id, { batchId: batch.id, orderNo: batch.orderNo, styleNo: batch.styleNo, color: batch.color, sizes: batch.sizes });
    }} /> },
    { title: "颜色 / 材料", key: "color", width: 130, render: (_, entry) => <Input aria-label="颜色材料" value={entry.color} placeholder="如：黑色羊皮" onChange={event => updateDraftEntry(entry.id, { color: event.target.value, batchId: undefined })} /> },
    { title: "码数", key: "sizes", width: 90, render: (_, entry) => <Input aria-label="码数" value={entry.sizes} placeholder="5–11" onChange={event => updateDraftEntry(entry.id, { sizes: event.target.value })} /> },
    { title: "计件工序", key: "process", width: 154, render: (_, entry) => <Select aria-label="计件工序" value={entry.processId} className="wk-full" options={processes.map(process => ({ value: process.id, label: process.name }))} onChange={value => updateDraftEntry(entry.id, { processId: value })} /> },
    { title: "数量（双）", key: "quantity", width: 110, render: (_, entry) => <InputNumber aria-label="计件数量" className="wk-full" min={1} max={999999} precision={0} value={entry.quantity} status={showErrors && (entry.quantity === null || entry.quantity <= 0) ? "error" : undefined} placeholder="数量" onChange={value => updateDraftEntry(entry.id, { quantity: value })} /> },
    { title: "单价（元）", key: "price", width: 116, render: (_, entry) => <InputNumber aria-label="计件单价" className="wk-full" min={0} max={99999.9999} precision={4} formatter={(value, info) => info.userTyping ? info.input : value === undefined || value === null ? "" : String(Number(value))} value={entry.unitPrice} status={showErrors && entry.unitPrice === null ? "error" : undefined} placeholder="单价" onChange={value => updateDraftEntry(entry.id, { unitPrice: value }, true)} onPressEnter={addEntry} /> },
    { title: "金额（元）", key: "amount", width: 116, align: "right", render: (_, entry) => <strong className="wk-number">{entry.quantity !== null && entry.unitPrice !== null ? amountText(entryCents(entry) / 100) : "—"}</strong> },
    { title: "进度", key: "progress", width: 90, align: "center", render: (_, entry) => !processById(entry.processId)?.stage ? <Tooltip title="该工序计工资，部门完工以收尾交接工序统计"><span className="wk-muted wk-small">只计工资</span></Tooltip> : entry.batchId ? <Tooltip title={entry.progressConfirmed ? "已计入对应订单的累计完工数量" : "核对完成后计入对应订单的完工数量"}><Tag color={entry.progressConfirmed ? "blue" : "default"}>{entry.progressConfirmed ? "已计入" : "待核对"}</Tag></Tooltip> : <Tag color="orange">待关联</Tag> },
    { title: "", key: "delete", width: 44, fixed: "right", render: (_, entry) => <Button type="text" danger aria-label="删除计件行" icon={<DeleteOutlined />} onClick={() => {
      setDraft(current => current && ({ ...current, status: "draft", entries: current.entries.filter(item => item.id !== entry.id) })); setDirty(true);
    }} /> },
  ];

  const progressColumns: ColumnsType<BatchProgress> = [
    { title: "订单 / 款式", key: "batch", fixed: "left", width: 204, render: (_, row) => <button className="wk-order" onClick={() => openBatch(row.batch)}><strong>{row.batch.styleNo}<span>{row.batch.color}</span></strong><small>{row.batch.orderNo} · {row.batch.customer}</small></button> },
    ...(["upper", "lasting", "bonding", "packing"] as Stage[]).map((stage, index) => ({ title: `${stageNames[stage]}完工`, key: stage, width: 126, render: (_: unknown, row: BatchProgress) => <CompletionCell quantity={row.completed[stage]} total={row.batch.quantity} color={["#527bd9", "#5e94ab", "#61a591", "#a9b1bf"][index]} /> })),
    { title: "待交接", key: "waiting", width: 166, render: (_, row) => <div className="wk-waiting">{row.waitingLasting > 0 && <span><i className="wk-dot wk-dot-blue" />待上前段 <strong>{countText(row.waitingLasting)}</strong> 双</span>}{row.waitingBonding > 0 && <span><i className="wk-dot wk-dot-orange" />待贴底 <strong>{countText(row.waitingBonding)}</strong> 双</span>}{!row.waitingLasting && !row.waitingBonding && <span className="wk-muted">已交接</span>}</div> },
    { title: "当前事项 / 交期", key: "note", width: 244, render: (_, row) => <div className="wk-batch-note"><span>{row.handoff.note}</span><small>{row.batch.deliveryDate} 交货{row.issues.length > 0 && <Tag color="error">数量待核对</Tag>}</small></div> },
    { title: "", key: "action", fixed: "right", width: 86, render: (_, row) => <Button type="link" onClick={() => openBatch(row.batch)}>查看<ArrowRightOutlined /></Button> },
  ];

  function entryControl(entry: PieceworkEntry, key: string, index: number): ReactNode {
    const column = entryColumns.find(item => item.key === key);
    return column && "render" in column ? column.render?.(undefined, entry, index) as ReactNode : null;
  }

  function toggleEntryDetails(id: string) {
    setExpandedEntries(current => current.includes(id) ? current.filter(item => item !== id) : [...current, id]);
  }

  function entryDetails(entry: PieceworkEntry, index: number) {
    return <div className="wk-extra-entry-grid">{[["order", "指令单号"], ["color", "颜色 / 材料"], ["sizes", "码数"], ["process", "做的工序"]].map(([key, label]) => <label key={key}><span>{label}</span>{entryControl(entry, key, index)}</label>)}<div className="wk-extra-progress"><span>这笔记录的进度关联：</span>{entryControl(entry, "progress", index)}</div></div>;
  }

  const simpleEntryColumns: ColumnsType<PieceworkEntry> = [
    ...entryColumns.filter(column => ["index", "style", "quantity", "price", "amount"].includes(String(column.key))),
    Table.EXPAND_COLUMN,
    ...entryColumns.filter(column => column.key === "delete"),
  ];

  return <div className="workshop-preview">
    <div className="wk-heading">
      <div><div className="wk-eyebrow">工厂管理 <span>示例页面</span></div><h1>{progressView ? "部门生产进度" : "给员工录工资"}</h1><p>{progressView ? "对照每批鞋的面部、前段与贴底数量，找到还在等下一道工序的款。" : "员工交来纸单后，点他的名字开始录入。"}</p></div>
      {!progressView && <Button type="primary" icon={<PlusOutlined />} onClick={() => openEmployeeForm()}>新增员工</Button>}
    </div>

    <div className="wk-navigation"><Tabs activeKey={progressView ? "progress" : "piecework"} onChange={key => { setSearch(""); navigate(`/workshop/${key}`); window.scrollTo(0, 0); }} items={[{ key: "piecework", label: <span><TeamOutlined />录员工工资</span> }, { key: "progress", label: <span><CheckCircleFilled />看生产进度</span> }]} /><span className="wk-preview-note">示例修改保存在本机，用于确认页面</span></div>

    <div className="wk-period-bar"><div><label htmlFor="wk-month">{progressView ? "统计截至" : "算哪个月"}</label><Input id="wk-month" type="month" value={month} onChange={event => { if (/^\d{4}-\d{2}$/.test(event.target.value)) setMonth(event.target.value); }} />{progressView ? <span className="wk-period-caption">按订单跨月累计，不受工资月份切断</span> : <span className="wk-pay-month"><span className="wk-dot wk-dot-green" />{nextMonthLabel(month)}</span>}</div><Input aria-label={progressView ? "搜索订单款号" : "搜索员工"} allowClear prefix={<SearchOutlined />} placeholder={progressView ? "搜索订单、款号、颜色" : "输入员工姓名"} value={search} onChange={event => setSearch(event.target.value)} className="wk-search" /></div>

    {progressView ? <>
      <div className="wk-stats">
        <div><span>生产中的订单款式</span><strong>{progressRows.length}<small>款</small></strong><p>截至{monthLabel(month)}的已登记记录</p></div>
        <div><span>面部做好 · 等上前段</span><strong className="wk-stat-blue">{countText(waitingLasting)}<small>双</small></strong><p>{progressRows.filter(row => row.waitingLasting > 0).length} 款尚有数量未转入前段</p></div>
        <div><span>前段做好 · 等贴底</span><strong className="wk-stat-orange">{countText(waitingBonding)}<small>双</small></strong><p>{progressRows.filter(row => row.waitingBonding > 0).length} 款尚有数量未转入贴底</p></div>
        <div><span>已包装</span><strong>{countText(progressRows.reduce((total, row) => total + row.completed.packing, 0))}<small>双</small></strong><p>按包装完工工序累计</p></div>
      </div>
      <section className="wk-table-panel"><div className="wk-panel-title"><div><h2>订单工序对比</h2><span>每道工序各自统计完成双数</span></div><Popover placement="bottomRight" title="预览中的完工统计口径" content={<div className="wk-popover-copy"><p>面部以“面部收尾交接”统计，前段、贴底、包装各取对应完工工序。</p><p>同一批鞋的车缝、画线等计件记录计入工资，不重复增加部门完工双数。</p><p>待上前段 = 面部完工 − 前段已接收；待贴底 = 前段完工 − 贴底已接收。</p><p>当前以总双数演示，正式配套还需按颜色、鞋码核对。月底录入的数据反映截至录入的进度。</p></div>}><Button type="text" size="small" icon={<InfoCircleOutlined />}>统计口径</Button></Popover></div>
        <div className="wk-progress-filters">{([
          ["all", "全部款式", progressRows.length],
          ["waiting-lasting", "面部做好待上前段", progressRows.filter(row => row.waitingLasting > 0).length],
          ["waiting-bonding", "前段做好待贴底", progressRows.filter(row => row.waitingBonding > 0).length],
          ["issues", "数量待核对", progressRows.filter(row => row.issues.length > 0).length],
        ] as const).map(([key, label, count]) => <button key={key} className={progressFilter === key ? "active" : ""} onClick={() => setProgressFilter(key)}>{label}<span>{count}</span></button>)}</div>
        <Table className="wk-desktop-table" rowKey={row => row.batch.id} columns={progressColumns} dataSource={filteredProgress} scroll={{ x: 1200 }} pagination={false} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前条件下没有订单款式" /> }} />
        <div className="wk-mobile-cards">{filteredProgress.map(row => <article className="wk-progress-card" key={row.batch.id}>
          <button className="wk-order" onClick={() => openBatch(row.batch)}><strong>{row.batch.styleNo}<span>{row.batch.color}</span></strong><small>{row.batch.orderNo} · {row.batch.customer}</small></button>
          <div className="wk-mobile-stage-grid">{(["upper", "lasting", "bonding", "packing"] as Stage[]).map((stage, index) => <div key={stage}><span>{stageNames[stage]}完工</span><CompletionCell quantity={row.completed[stage]} total={row.batch.quantity} color={["#527bd9", "#5e94ab", "#61a591", "#a9b1bf"][index]} /></div>)}</div>
          <div className="wk-card-waiting">{row.waitingLasting > 0 && <span>待上前段 <strong>{countText(row.waitingLasting)} 双</strong></span>}{row.waitingBonding > 0 && <span>待贴底 <strong>{countText(row.waitingBonding)} 双</strong></span>}{!row.waitingLasting && !row.waitingBonding && <span className="wk-muted">前段、贴底已交接</span>}</div>
          <p className="wk-card-note">{row.handoff.note}</p><div className="wk-card-footer"><span>{row.batch.deliveryDate} 交货{row.issues.length > 0 && <Tag color="error">数量待核对</Tag>}</span><Button type="link" size="small" onClick={() => openBatch(row.batch)}>查看交接<ArrowRightOutlined /></Button></div>
        </article>)}{!filteredProgress.length && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前条件下没有订单款式" />}</div>
        <div className="wk-panel-footer"><span>共 {filteredProgress.length} 款 · 数量单位：双</span><span>完工数量来自已核对且关联订单的计件明细，交接数量单独登记</span></div>
      </section>
    </> : <>
      <div className="wk-simple-steps"><span className="active"><b>1</b>选员工</span><ArrowRightOutlined /><span><b>2</b>照纸单录计件</span><ArrowRightOutlined /><span><b>3</b>看工资合计</span></div>
      <section className="wk-table-panel"><div className="wk-simple-list-title">你要给谁录工资？</div><div className="wk-employee-filters"><div className="wk-department-filters">{(["all", ...departments] as const).map(item => <button key={item} className={department === item ? "active" : ""} onClick={() => setDepartment(item)}>{item === "all" ? "全部员工" : item}<span>{item === "all" ? employees.length : employees.filter(employee => employee.department === item).length}</span></button>)}</div><Select aria-label="筛选录入状态" value={status} onChange={setStatus} options={[{ value: "all", label: "全部员工" }, { value: "empty", label: "本月还没录" }, { value: "draft", label: "录了还没算" }, { value: "checked", label: "已算过工资" }]} /></div>
        <Table className="wk-desktop-table" rowKey="id" columns={employeeColumns} dataSource={filteredEmployees} pagination={false} scroll={{ x: 1130 }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有符合条件的员工" /> }} />
        <div className="wk-mobile-cards">{filteredEmployees.map(employee => <article className="wk-employee-card" key={employee.id}>
          <div className="wk-card-person"><button className="wk-person" onClick={() => openLedger(employee)}><Avatar shape="square" className={`wk-avatar wk-avatar-${departments.indexOf(employee.department)}`}>{employee.name.slice(-2)}</Avatar><span><strong>{employee.name}</strong><small>{employee.number} · {processById(employee.processId)?.name}</small></span></button><DepartmentTag department={employee.department} /></div>
          <div className="wk-card-money wk-simple-card-money"><div><span>本月保底</span><strong>¥ {amountText(employee.ledger.guarantee)}</strong></div><div><span>已经录入的计件</span><strong>¥ {employee.hasLedger ? amountText(employee.amount) : "0.00"}</strong></div></div>
          <div className="wk-card-footer"><span>{!employee.hasLedger ? "本月还没录" : employee.ledger.status === "checked" ? <><CheckOutlined /> 已算过工资</> : `已录 ${employee.ledger.entries.length} 笔，还没算工资`}</span><Button type="primary" size="small" onClick={() => openLedger(employee)}>给他录计件<ArrowRightOutlined /></Button></div>
        </article>)}{!filteredEmployees.length && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有符合条件的员工" />}</div>
        <div className="wk-panel-footer"><span>共 {filteredEmployees.length} 位员工 · 金额单位：元</span><span>选择员工，即可照着纸质计件表录入</span></div>
      </section>
      <div className="wk-bottom-tip"><InfoCircleOutlined /><span>这里先用示例员工演示，录入内容只保存在当前浏览器。</span></div>
    </>}

    <Drawer open={Boolean(selectedEmployee)} onClose={closeLedger} width="min(1060px, calc(100vw - 24px))" rootClassName="workshop-ledger-drawer" title={selectedEmployee && <div className="wk-ledger-title"><div><strong>{selectedEmployee.name}的计件表<DepartmentTag department={selectedEmployee.department} /></strong><small>{monthLabel(month)}做的活 · {nextMonthLabel(month)}</small></div></div>} extra={<Button type="text" icon={<EditOutlined />} onClick={() => openEmployeeForm(selectedEmployee)}>员工资料</Button>} footer={<div className="wk-ledger-footer wk-simple-footer"><div><span>计件合计{dirty && <i className="wk-dot wk-dot-orange" />}</span><strong>¥ {amountText(draftAmount)}</strong></div><Space><Button onClick={() => saveLedger(false)}>先保存</Button><Button type="primary" onClick={() => { if (saveLedger(true)) setWageResultOpen(true); }}>算工资</Button></Space></div>}>
      {draft && selectedEmployee && <>
        <div className="wk-simple-intro"><strong>照纸单，填下面这三项就行</strong><p>款号、数量、单价。每笔多少钱和本月合计会自动算。</p></div>
        <div className="wk-ledger-section-title"><div><h3>这个月做了哪些鞋</h3><span>一行就是纸单上的一笔</span></div><Button icon={<PlusOutlined />} onClick={addEntry}>再加一笔</Button></div>
        <Table className="wk-ledger-table" rowKey="id" columns={simpleEntryColumns} dataSource={draft.entries} pagination={false} size="small" scroll={{ x: 730 }} rowClassName={entry => showErrors && invalidEntry(entry) ? "wk-invalid-row" : ""} expandable={{ expandedRowKeys: expandedEntries, onExpand: (_, entry) => toggleEntryDetails(entry.id), columnWidth: 100, expandIcon: ({ expanded, record }) => <Button type="link" size="small" onClick={() => toggleEntryDetails(record.id)}>{expanded ? "收起" : "补充信息"}</Button>, expandedRowRender: (entry, index) => entryDetails(entry, index) }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没开始录，点“再加一笔”就能填写" /> }} summary={() => <Table.Summary.Row><Table.Summary.Cell index={0} colSpan={4}><strong>计件合计</strong></Table.Summary.Cell><Table.Summary.Cell index={4} align="right"><strong>{amountText(draftAmount)}</strong></Table.Summary.Cell><Table.Summary.Cell index={5} colSpan={2} /></Table.Summary.Row>} />
        <div className="wk-mobile-entry-list">{draft.entries.map((entry, index) => <article key={entry.id} className={`wk-mobile-entry ${showErrors && invalidEntry(entry) ? "wk-mobile-entry-invalid" : ""}`} data-row-key={entry.id}>
          <div className="wk-mobile-entry-heading"><span>第 {index + 1} 笔</span>{entryControl(entry, "delete", index)}</div>
          <div className="wk-mobile-entry-grid"><label className="wk-entry-wide"><span>款号 · 照纸单填写</span>{entryControl(entry, "style", index)}</label><label><span>做了多少双</span>{entryControl(entry, "quantity", index)}</label><label><span>每双多少钱（元）</span>{entryControl(entry, "price", index)}</label></div>
          <div className="wk-mobile-entry-amount"><span>这笔多少钱</span><strong>¥ {entry.quantity !== null && entry.unitPrice !== null ? amountText(entryCents(entry) / 100) : "—"}</strong></div>
          <button className="wk-more-entry" onClick={() => toggleEntryDetails(entry.id)}>{expandedEntries.includes(entry.id) ? "收起补充信息 ↑" : "补充指令单号、颜色等 ↓"}</button>{expandedEntries.includes(entry.id) && entryDetails(entry, index)}
        </article>)}{!draft.entries.length && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没开始录，点“再加一笔”就能填写" />}</div>
        <button className="wk-add-row" onClick={addEntry}><PlusOutlined />再加一笔<span>纸单还有下一行，就继续加</span></button>
        <div className="wk-simple-guarantee"><span>这个月的保底：<strong>¥ {amountText(draft.guarantee)}</strong></span><Button type="link" size="small" onClick={() => setGuaranteeOpen(value => !value)}>{guaranteeOpen ? "收起" : "有请假，要调整保底"}</Button></div>
        {guaranteeOpen && <div className="wk-guarantee-edit"><label><span>核对出勤后，这个月按多少保底比较？</span><InputNumber aria-label="本月核定保底" min={0} max={999999} precision={2} prefix="¥" value={draft.guarantee} onChange={value => { setDraft(current => current && ({ ...current, guarantee: value ?? 0, status: "draft" })); setDirty(true); }} /></label><p>按天扣多少还需要确定，这里先手工填写你核对后的保底金额。</p></div>}
        <p className="wk-simple-help">录完后，点下方“算工资”，就能看到计件和保底哪个高。</p>
      </>}
    </Drawer>

    <Modal open={wageResultOpen} title={selectedEmployee && `${selectedEmployee.name} · ${monthLabel(month)}工资`} onCancel={() => setWageResultOpen(false)} cancelText="再检查一下" okText="返回员工列表" onOk={() => { setWageResultOpen(false); setSelectedEmployee(undefined); }}>
      {draft && <div className="wk-wage-result"><div><span>本月计件合计</span><strong>¥ {amountText(draftAmount)}</strong></div><div><span>本月保底</span><strong>¥ {amountText(draft.guarantee)}</strong></div><section><span>这次工资试算</span><strong>¥ {amountText(Math.max(draftAmount, draft.guarantee))}</strong><p>{draftAmount >= draft.guarantee ? "计件比保底高，按计件金额算。" : "计件没有超过保底，按保底金额算。"}</p></section><p>{nextMonthLabel(month)}。请假及其他补扣款还需核对，当前是示例试算。</p></div>}
    </Modal>

    <Modal title={editingEmployee ? "员工资料" : "新增员工"} open={employeeModal} onCancel={() => setEmployeeModal(false)} onOk={() => { void saveEmployee().catch(() => undefined); }} okText="保存员工" cancelText="取消" destroyOnHidden>
      <Form form={employeeForm} layout="vertical" preserve={false} onValuesChange={changed => { if (changed.department) employeeForm.setFieldValue("processId", processes.find(process => process.department === changed.department)?.id); }}>
        <Form.Item name="name" label="员工姓名" rules={[{ required: true, whitespace: true, message: "请填写员工姓名" }]}><Input placeholder="填写员工姓名" maxLength={30} /></Form.Item>
        <Form.Item name="department" label="所属部门" rules={[{ required: true }]}><Select options={departments.map(item => ({ value: item, label: item }))} /></Form.Item>
        <Form.Item name="processId" label="默认计件工序" rules={[{ required: true }]}><Select options={processes.filter(process => process.department === formDepartment).map(process => ({ value: process.id, label: process.name }))} /></Form.Item>
        <Form.Item name="guarantee" label="常规月保底（元）" extra="新月份默认带入；已保存月份保留各自核定的保底。" rules={[{ required: true, message: "请填写保底金额" }]}><InputNumber min={0} max={999999} precision={2} prefix="¥" style={{ width: "100%" }} /></Form.Item>
      </Form>
    </Modal>

    <Drawer open={Boolean(selectedBatch)} onClose={() => setSelectedBatch(undefined)} width="min(920px, calc(100vw - 24px))" rootClassName="workshop-batch-drawer" title={selectedBatch && <div><strong>{selectedBatch.styleNo} · 工序与交接</strong><div className="wk-drawer-subtitle">{selectedBatch.orderNo} · {selectedBatch.color} · 订单 {countText(selectedBatch.quantity)} 双</div></div>}>
      {currentBatchProgress && selectedBatch && handoffDraft && <>
        <div className="wk-batch-period"><span>累计截至{monthLabel(month)}</span><Tag>示例订单</Tag></div>
        {currentBatchProgress.issues.length > 0 && <Alert type="warning" showIcon message="数量需要核对" description={currentBatchProgress.issues.join("；")} style={{ marginBottom: 20 }} />}
        <div className="wk-batch-stages">{(["upper", "lasting", "bonding", "packing"] as Stage[]).map((stage, index) => <div key={stage}><span><i>{index + 1}</i>{stageNames[stage]}完工</span><strong>{countText(currentBatchProgress.completed[stage])}<small> / {countText(selectedBatch.quantity)}</small></strong></div>)}</div>
        <h3 className="wk-detail-heading">交接数量</h3><p className="wk-detail-description">填写截至本月底的累计接收数量，用来区分“还没转入”和“已经接收、尚未完工”。</p>
        <div className="wk-handoff-inputs">{([
          ["lastingReceived", "前段已接收", currentBatchProgress.completed.lasting],
          ["bondingReceived", "贴底已接收", currentBatchProgress.completed.bonding],
          ["packingReceived", "包装已接收", currentBatchProgress.completed.packing],
        ] as const).map(([key, label, completed]) => <label key={key}><span>{label}（双）</span><InputNumber aria-label={label} min={0} max={selectedBatch.quantity} precision={0} value={handoffDraft[key]} onChange={value => setHandoffDraft(current => current && ({ ...current, [key]: value ?? 0 }))} /><small>已接收未完工 {countText(Math.max(0, handoffDraft[key] - completed))} 双</small></label>)}</div>
        <label className="wk-note-field"><span>缺料 / 外发 / 当前事项</span><Input.TextArea aria-label="当前生产事项" value={handoffDraft.note} onChange={event => setHandoffDraft(current => current && ({ ...current, note: event.target.value }))} rows={2} maxLength={300} /></label><Button type="primary" onClick={saveHandoff}>保存交接记录</Button>
        <div className="wk-waiting-summary"><span>面部做好，待上前段<strong>{countText(currentBatchProgress.waitingLasting)} 双</strong></span><span>前段做好，待贴底<strong>{countText(currentBatchProgress.waitingBonding)} 双</strong></span></div>
        <h3 className="wk-detail-heading">完工数量来自这些计件记录</h3><Table size="small" rowKey={row => row.entry.id} pagination={false} scroll={{ x: 560 }} columns={[
          { title: "工资月份", dataIndex: "month", width: 110 },
          { title: "员工", key: "employee", render: (_, row) => row.employee.name },
          { title: "完工工序", key: "stage", render: (_, row) => processById(row.entry.processId)?.name },
          { title: "数量（双）", key: "quantity", align: "right", render: (_, row) => countText(row.entry.quantity ?? 0) },
        ]} dataSource={currentBatchProgress.sources} locale={{ emptyText: "尚未关联已核对的完工记录" }} />
        <p className="wk-detail-description">这里只列用于统计完工的工序。车缝、画线等其他计件仍保留在员工工资明细中。</p>
      </>}
    </Drawer>
  </div>;
}
