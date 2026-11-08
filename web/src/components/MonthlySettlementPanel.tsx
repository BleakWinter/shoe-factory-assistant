import { Alert, App, Button, Collapse, Descriptions, Input, InputNumber, Select, Space, Spin, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useRef, useState } from "react";
import { localToday } from "../api/manualOrderApi";
import { confirmMonthlySettlement, fetchMonthlySettlement, recordMonthlySettlementReceipt, saveMonthlySettlement } from "../api/reconciliationApi";
import type { MonthlySettlement, SettlementAdjustment, SettlementDraft, SettlementMaterial, SettlementShipment } from "../types/reconciliation";

const yuan = new Intl.NumberFormat("zh-CN", { style: "currency", currency: "CNY" });
const money = (value: number | null) => value === null ? "—" : yuan.format(value);
const cents = (value: number | null) => Math.round((value ?? 0) * 100);
const sumCents = <T,>(rows: T[], value: (row: T) => number | null) => rows.reduce((total, row) => total + cents(value(row)), 0);
const materialCents = (row: SettlementMaterial) => {
  const quantity = BigInt(Math.round((row.quantity ?? 0) * 10000));
  const price = BigInt(Math.round((row.unitPrice ?? 0) * 10000));
  return Number((quantity * price + 500000n) / 1000000n);
};

const statuses = { NOT_QUOTED: "未报价", DRAFT: "报价待完善", QUOTED: "已报价 · 待客人批价", CONFIRMED: "已确认 · 本期暂缓" };
const shipmentColumns: ColumnsType<SettlementShipment> = [
  { title: "出货日期", dataIndex: "shippingDate", width: 115 },
  { title: "订单号", dataIndex: "orderNo", width: 125, render: (value, row) => <Typography.Text title={row.taskNo || undefined}>{value || "—"}</Typography.Text> },
  { title: "工厂款号", dataIndex: "developmentNo", width: 150 },
  { title: "颜色 / 材料", dataIndex: "colorMaterial", width: 180, ellipsis: true },
  { title: "双数", dataIndex: "quantity", align: "right", width: 72 },
];
const priceColumns: ColumnsType<SettlementShipment> = [
  { title: "确认单价", dataIndex: "unitPrice", align: "right", width: 108, render: money },
  { title: "货款", dataIndex: "amount", align: "right", width: 116, render: money },
];
const pagination = { defaultPageSize: 10, hideOnSinglePage: true, showSizeChanger: true, showTotal: (total: number) => `共 ${total} 条` };

interface Props {
  month: string;
  customerId: number;
  onDirtyChange: (dirty: boolean) => void;
}

export default function MonthlySettlementPanel({ month, customerId, onDirtyChange }: Props) {
  const { message, modal } = App.useApp();
  const [view, setView] = useState<MonthlySettlement>();
  const [draft, setDraft] = useState<SettlementDraft>();
  const [receipt, setReceipt] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const sequence = useRef(0);
  const apply = useCallback((result: MonthlySettlement) => {
    setView(result);
    setDraft(result.draft);
    setReceipt(result.receivedOn || "");
  }, []);
  const load = useCallback(async () => {
    const request = ++sequence.current;
    setBusy(true);
    setError("");
    try {
      const result = await fetchMonthlySettlement(month, customerId);
      if (request === sequence.current) apply(result);
    } catch (failure) {
      if (request === sequence.current) setError(failure instanceof Error ? failure.message : "结算单加载失败");
    } finally {
      if (request === sequence.current) setBusy(false);
    }
  }, [apply, customerId, month]);
  useEffect(() => { void load(); return () => { sequence.current++; }; }, [load]);

  const dirty = Boolean(view && draft && (JSON.stringify(view.draft) !== JSON.stringify(draft) || receipt !== (view.receivedOn || "")));
  useEffect(() => { onDirtyChange(dirty); return () => onDirtyChange(false); }, [dirty, onDirtyChange]);

  function refresh() {
    if (dirty) modal.confirm({ title: "放弃本页未保存的修改并刷新？", onOk: load });
    else void load();
  }

  if (!view || !draft) return error ? <section className="page-panel">
    <Space direction="vertical"><Typography.Text type="danger">{error}</Typography.Text><Button onClick={refresh}>重新加载</Button></Space>
  </section> : <div className="page-panel"><Spin tip="结算单加载中"/></div>;

  const locked = Boolean(view.confirmedAt);
  const editable = !locked && !busy;
  const { statement } = view;
  const deferred = new Set(draft.deferredKeys);
  const carryKeys = new Set(draft.carryKeys);
  const currentPending = statement.pending.filter(row => row.shippingDate.slice(0, 7) === month);
  const allCurrent = [...statement.current, ...currentPending.filter(row => row.unitPrice !== null)];
  const current = locked ? statement.current : allCurrent.filter(row => !deferred.has(row.key));
  const unconfirmedCurrent = locked ? [] : currentPending.filter(row => row.unitPrice === null && !deferred.has(row.key));
  const deferredCurrent = locked ? [] : [
    ...currentPending.filter(row => row.unitPrice === null && deferred.has(row.key)),
    ...allCurrent.filter(row => deferred.has(row.key)),
  ];
  const quotedCurrent = unconfirmedCurrent.filter(row => row.quotedPrice !== null);
  const quoteEstimate = quotedCurrent.reduce((sum, row) => sum + cents(row.quotedPrice) * row.quantity, 0);
  const goods = sumCents(current, row => row.amount);
  const carryover = sumCents(statement.carryovers.filter(row => carryKeys.has(row.key)), row => row.amount);
  const currentForecast = (goods + quoteEstimate) / 100;
  const totalForecast = (goods + quoteEstimate + carryover) / 100;
  const holdBase = draft.holdBase === null ? goods : cents(draft.holdBase);
  const hold = Number((BigInt(holdBase) * BigInt(cents(draft.holdRate)) + 5000n) / 10000n);
  const materials = draft.materials.reduce((total, row) => total + materialCents(row), 0);
  const availableRefunds = new Map(statement.refunds.map(row => [row.originId, row]));
  const validRefunds = draft.refunds.filter(row => {
    const available = availableRefunds.get(row.originId);
    return available && row.amount <= available.remaining;
  });
  const refund = sumCents(validRefunds, row => row.amount);
  const credits = sumCents(draft.adjustments.filter(row => row.type === "CREDIT"), row => row.amount);
  const debits = sumCents(draft.adjustments.filter(row => row.type === "DEBIT"), row => row.amount);
  const totals = locked ? statement.totals : {
    shippedPairs: statement.totals.shippedPairs, goods: goods / 100, carryover: carryover / 100, holdBase: holdBase / 100,
    hold: hold / 100, refund: refund / 100, materials: materials / 100, credits: credits / 100, debits: debits / 100,
    receivable: (goods + carryover + refund + credits - hold - materials - debits) / 100,
  };

  function change(patch: Partial<SettlementDraft>) { setDraft(value => value && { ...value, ...patch }); }
  function toggleDeferred(key: string) {
    change({ deferredKeys: deferred.has(key) ? draft!.deferredKeys.filter(value => value !== key) : [...deferred, key] });
  }
  function deferUnconfirmed() {
    modal.confirm({ title: `暂缓本期 ${unconfirmedCurrent.length} 笔未确认出货？`, okText: "标为暂缓",
      content: "暂缓后会从本期货款预估移出。保存草稿并确认月结后，可在后续账期补结。",
      onOk: () => change({ deferredKeys: [...deferred, ...unconfirmedCurrent.map(row => row.key)] }) });
  }
  function updateMaterial(index: number, patch: Partial<SettlementMaterial>) {
    change({ materials: draft!.materials.map((row, offset) => offset === index ? { ...row, ...patch } : row) });
  }
  function updateAdjustment(index: number, patch: Partial<SettlementAdjustment>) {
    change({ adjustments: draft!.adjustments.map((row, offset) => offset === index ? { ...row, ...patch } : row) });
  }

  async function write(action: () => Promise<MonthlySettlement>, success: string) {
    const request = ++sequence.current;
    setBusy(true);
    setError("");
    try {
      const result = await action();
      if (request === sequence.current) { apply(result); message.success(success); }
    } catch (failure) {
      if (request === sequence.current) setError(failure instanceof Error ? failure.message : "结算单保存失败");
    } finally {
      if (request === sequence.current) setBusy(false);
    }
  }

  function save() {
    if (!view || !draft) return;
    if (!draft.preparedOn || draft.materials.some(row => !row.date || !row.name.trim() || !(Number(row.quantity) > 0) || row.unitPrice === null)
        || draft.adjustments.some(row => !row.reason.trim() || !(Number(row.amount) > 0))) {
      message.warning("请填完整制单日期、领料和其他补扣明细");
      return;
    }
    const availableCarry = new Set(statement.carryovers.map(row => row.key));
    const availableCurrent = new Set([...allCurrent, ...currentPending].map(row => row.key));
    const cleaned = { ...draft, carryKeys: draft.carryKeys.filter(key => availableCarry.has(key)),
      deferredKeys: draft.deferredKeys.filter(key => availableCurrent.has(key)), refunds: validRefunds };
    if (cleaned.carryKeys.length !== draft.carryKeys.length || cleaned.deferredKeys.length !== draft.deferredKeys.length
        || cleaned.refunds.length !== draft.refunds.length) {
      change(cleaned);
      message.warning("已移除失效的出货或返还选择，请重新核对后保存");
      return;
    }
    void write(() => saveMonthlySettlement(month, customerId, view.revision, cleaned), "结算草稿已保存");
  }

  function confirm() {
    if (!view?.previewToken || dirty || !view.revision || unconfirmedCurrent.length) return;
    const token = view.previewToken;
    modal.confirm({ title: `确认 ${month} 月结`, okText: "确认月结", cancelText: "继续核对",
      content: `本期应收 ${money(totals.receivable)}。确认后固定当期价格和金额，未结的出货可在后续账期补结。`,
      onOk: () => write(() => confirmMonthlySettlement(month, customerId, view.revision, token), "月结已确认") });
  }

  const currentColumns: ColumnsType<SettlementShipment> = [...shipmentColumns, ...priceColumns,
    ...(!locked ? [{ title: "操作", width: 78, render: (_: unknown, row: SettlementShipment) =>
      <Button type="link" size="small" disabled={!editable} onClick={() => toggleDeferred(row.key)}>暂缓</Button> }] : [])];
  const pendingColumns: ColumnsType<SettlementShipment> = [...shipmentColumns,
    { title: "已报单价", dataIndex: "quotedPrice", align: "right", width: 105, render: money },
    { title: "确认单价", dataIndex: "unitPrice", align: "right", width: 105, render: money },
    { title: "状态", width: 172, render: (_, row) => <Space wrap size={4}>
      <Tag color={row.unitPrice === null ? "gold" : "blue"}>{statuses[row.priceStatus]}</Tag>
      {row.unitPrice === null && deferred.has(row.key) && <Tag>已暂缓</Tag>}
    </Space> },
    { title: "操作", width: 164, render: (_, row) => <Space wrap size={4}>
      {row.quotationId && <Button type="link" size="small" href={`/price-configs/quotations/${row.quotationId}`} target="_blank" rel="noopener noreferrer">查看报价</Button>}
      {!locked && <Button type="link" size="small" disabled={!editable}
        onClick={() => toggleDeferred(row.key)}>{deferred.has(row.key) ? "取消暂缓" : "暂缓"}</Button>}
    </Space> },
  ];

  return <>
    {error && <section className="page-panel"><Typography.Text type="danger">{error}</Typography.Text></section>}
    <section className="page-panel">
      <Space wrap>
        <Tag color={locked ? "green" : "blue"}>{locked ? "已确认" : "草稿"}</Tag>
        <label>制单日期 <Input type="date" aria-label="制单日期" value={draft.preparedOn} max={localToday()} disabled={!editable}
          style={{ width: 148 }} onChange={event => change({ preparedOn: event.target.value })}/></label>
        {locked && <><label>实际到账 <Input type="date" aria-label="实际到账日期" value={receipt} max={localToday()} disabled={busy}
          style={{ width: 148 }} onChange={event => setReceipt(event.target.value)}/></label>
          <Button disabled={busy || !dirty} onClick={() => void write(() => recordMonthlySettlementReceipt(month, customerId, view.revision, receipt || null), "到账日期已保存")}>保存到账日期</Button></>}
        <Button loading={busy} onClick={refresh}>刷新</Button>
      </Space>
    </section>

    <section className="page-panel reconciliation-panel">
      <div className="reconciliation-panel-heading"><Typography.Title level={4}>本期出货</Typography.Title>
        <Typography.Text type="secondary">共 {totals.shippedPairs} 双 · 本期计价 {current.reduce((sum, row) => sum + row.quantity, 0)} 双</Typography.Text></div>
      <Table size="small" rowKey="key" columns={currentColumns} dataSource={current} pagination={pagination} scroll={{ x: 950 }}/>
    </section>
    <section className="page-panel reconciliation-panel">
      <div className="reconciliation-panel-heading"><div><Typography.Title level={4}>{locked ? "本期转待请款" : "本期待定价款号"}</Typography.Title>
        <Typography.Text type="secondary">{locked ? "本期出货中转到后续账期请款的明细" : "当前账单需要报价或确认客户单价的出货"}</Typography.Text></div>
        <Space wrap>
          {unconfirmedCurrent.length > 0 && <Button disabled={!editable} onClick={deferUnconfirmed}>暂缓本期全部未确认项</Button>}
          <Button href={`/unpriced-styles?month=${month}&customerId=${customerId}`} target="_blank" rel="noopener noreferrer">查看本月定价进度</Button>
        </Space></div>
      <Table size="small" rowKey="key" columns={pendingColumns} dataSource={locked ? currentPending : unconfirmedCurrent}
        pagination={pagination} scroll={{ x: 1180 }} locale={{ emptyText: locked ? "本期没有转待请款的明细" : "本期没有待定价出货" }}/>
      {deferredCurrent.length > 0 && <Collapse style={{ marginTop: 12 }} items={[{
        key: "deferred", label: `本期已暂缓 · ${deferredCurrent.length} 笔`,
        children: <Table size="small" rowKey="key" columns={pendingColumns} dataSource={deferredCurrent} pagination={pagination} scroll={{ x: 1180 }}/>,
      }]}/>}
    </section>
    <section className="page-panel reconciliation-panel">
      <div className="reconciliation-panel-heading"><div><Typography.Title level={4}>跨月补结</Typography.Title>
        <Typography.Text type="secondary">{locked ? "已计入本期的以前出货" : "从已确认账单带出的待请款，客人批价后可勾选"}</Typography.Text></div></div>
      <Table size="small" rowKey="key" columns={[...shipmentColumns, ...priceColumns]} dataSource={statement.carryovers}
        pagination={pagination} scroll={{ x: 950 }} rowSelection={locked ? undefined : {
          selectedRowKeys: draft.carryKeys, preserveSelectedRowKeys: true,
          onChange: keys => change({ carryKeys: keys.map(String) }), getCheckboxProps: () => ({ disabled: !editable }),
        }}/>
    </section>

    <section className="page-panel reconciliation-panel">
      <Collapse items={[
        { key: "deposit", label: "质保金扣款和返还", children: <Space direction="vertical" size="middle" style={{ width: "100%" }}>
          <Space wrap>
            <label>扣款比例 <InputNumber aria-label="质保金扣款比例" min={0} max={100} precision={2} addonAfter="%" value={draft.holdRate} disabled={!editable}
              onChange={value => change({ holdRate: value ?? 0 })}/></label>
            <label>扣款基数 <InputNumber aria-label="质保金扣款基数" min={0} max={999999999999.99} precision={2} value={draft.holdBase} disabled={!editable}
              placeholder="默认本期货款" onChange={holdBase => change({ holdBase })}/></label>
            <Typography.Text type="secondary">本期基数 {money(totals.holdBase)} · 扣款 {money(totals.hold)}</Typography.Text>
          </Space>
          <Table size="small" rowKey="originId" dataSource={statement.refunds} pagination={pagination} scroll={{ x: 550 }}
            columns={[
              { title: "原扣款月", dataIndex: "originMonth" }, { title: "应返账期", dataIndex: "dueMonth" },
              { title: locked ? "返还前余额" : "待返余额", dataIndex: "remaining", align: "right", render: money },
              { title: "本期实际返还", render: (_, row) => locked ? money(row.amount) : <InputNumber aria-label={`${row.originMonth} 质保金返还金额`}
                min={0} max={row.remaining} precision={2} disabled={!editable} placeholder="0.00"
                value={draft.refunds.find(item => item.originId === row.originId)?.amount ?? null} onChange={amount => change({ refunds: [
                  ...draft.refunds.filter(item => item.originId !== row.originId), ...(amount && amount > 0 ? [{ originId: row.originId, amount }] : []),
                ] })}/> },
            ]}/>
        </Space> },
        { key: "materials", label: `领料扣款 · ${draft.materials.length} 笔`, children: <Space direction="vertical" style={{ width: "100%" }}>
          {draft.materials.map((row, index) => <Space wrap key={index}>
            <Input type="date" aria-label={`第 ${index + 1} 笔领料日期`} value={row.date} disabled={!editable} onChange={event => updateMaterial(index, { date: event.target.value })}/>
            <Input aria-label="领料品名" placeholder="品名" maxLength={100} value={row.name} disabled={!editable} onChange={event => updateMaterial(index, { name: event.target.value })}/>
            <InputNumber aria-label="领料数量" placeholder="数量" min={0.0001} max={999999.9999} precision={4} value={row.quantity} disabled={!editable} onChange={quantity => updateMaterial(index, { quantity })}/>
            <Input aria-label="领料单位" placeholder="单位" maxLength={16} style={{ width: 70 }} value={row.unit} disabled={!editable} onChange={event => updateMaterial(index, { unit: event.target.value })}/>
            <InputNumber aria-label="领料单价" placeholder="单价" min={0} max={99999.9999} precision={4} value={row.unitPrice} disabled={!editable} onChange={unitPrice => updateMaterial(index, { unitPrice })}/>
            <Input aria-label="领料单号" placeholder="领料单号" maxLength={100} value={row.reference} disabled={!editable} onChange={event => updateMaterial(index, { reference: event.target.value })}/>
            <span>{row.quantity === null || row.unitPrice === null ? "待填写" : money(materialCents(row) / 100)}</span>
            {!locked && <Button size="small" disabled={!editable} onClick={() => change({ materials: draft.materials.filter((_, offset) => offset !== index) })}>移除</Button>}
          </Space>)}
          {!locked && <Button disabled={!editable} onClick={() => change({ materials: [...draft.materials, { date: localToday(), name: "", quantity: null, unit: "", unitPrice: null, reference: "" }] })}>添加领料扣款</Button>}
        </Space> },
        { key: "adjustments", label: `其他补扣 · ${draft.adjustments.length} 笔`, children: <Space direction="vertical" style={{ width: "100%" }}>
          {draft.adjustments.map((row, index) => <Space wrap key={index}>
            <Select aria-label="补扣方向" value={row.type} disabled={!editable} options={[{ label: "加款", value: "CREDIT" }, { label: "扣款", value: "DEBIT" }]}
              onChange={type => updateAdjustment(index, { type })}/>
            <Input aria-label="补扣说明" placeholder="原因和原账单或订单号" maxLength={200} value={row.reason} disabled={!editable}
              onChange={event => updateAdjustment(index, { reason: event.target.value })}/>
            <InputNumber aria-label="补扣金额" placeholder="金额" min={0.01} max={9999999999.99} precision={2} value={row.amount} disabled={!editable}
              onChange={amount => updateAdjustment(index, { amount })}/>
            {!locked && <Button size="small" disabled={!editable} onClick={() => change({ adjustments: draft.adjustments.filter((_, offset) => offset !== index) })}>移除</Button>}
          </Space>)}
          {!locked && <Button disabled={!editable} onClick={() => change({ adjustments: [...draft.adjustments, { type: "CREDIT", reason: "", amount: null }] })}>添加其他补扣</Button>}
        </Space> },
      ]}/>
    </section>

    <section className="page-panel reconciliation-panel">
      <div className="reconciliation-panel-heading"><Typography.Title level={4}>结算汇总</Typography.Title>
        {!locked && dirty && <Typography.Text type="secondary">未保存的预览</Typography.Text>}</div>
      <div className="settlement-summary-groups">
        <section className="settlement-summary-group" aria-label="货款">
          <Statistic title={locked ? "本期货款" : quotedCurrent.length > 0 ? "货款预估合计" : "已确认货款合计"}
            value={locked ? totals.goods : totalForecast} precision={2} prefix="¥"/>
          <Descriptions size="small" column={1} items={[
            ...(!locked ? [
              { key: "goods", label: "本期已确认货款", children: money(totals.goods) },
              { key: "estimate", label: "待批价货款预估", children: quotedCurrent.length > 0 ? money(quoteEstimate / 100) : "—" },
              { key: "subtotal", label: "本期货款小计", children: money(currentForecast) },
            ] : []),
            { key: "carry", label: locked ? "跨月补结" : "已选跨月补结", children: money(totals.carryover) },
          ]}/>
          {unconfirmedCurrent.length > quotedCurrent.length && <Typography.Text type="secondary">
            {unconfirmedCurrent.length - quotedCurrent.length} 笔暂无完整报价，暂未计入预估
          </Typography.Text>}
        </section>
        <section className="settlement-summary-group" aria-label="质保金">
          <Typography.Title level={5}>质保金</Typography.Title>
          <Descriptions size="small" column={1} items={[
            { key: "hold", label: "本期质保金扣款", children: money(0 - totals.hold) },
            { key: "refund", label: "质保金返还", children: money(totals.refund) },
          ]}/>
        </section>
        <section className="settlement-summary-group" aria-label="其他补扣">
          <Typography.Title level={5}>其他补扣</Typography.Title>
          <Descriptions size="small" column={1} items={[
            { key: "materials", label: "领料扣款", children: money(0 - totals.materials) },
            { key: "adjustment", label: "其他补扣净额", children: money(totals.credits - totals.debits) },
          ]}/>
        </section>
      </div>
      {unconfirmedCurrent.length > 0 && <Alert className="settlement-price-warning" type="warning" showIcon
        message={`本期还有 ${unconfirmedCurrent.length} 笔出货未确认单价，请先采用客户单价或标记暂缓。`}/>}
      <div className="settlement-summary-total">
        <Statistic title={unconfirmedCurrent.length ? "已确认部分应收（扣补后）" : "本期应收（扣补后）"}
          value={totals.receivable} precision={2} prefix="¥"/>
        {!locked && <Space wrap>
          <Button disabled={busy} onClick={save}>保存草稿</Button>
          <Button type="primary" disabled={busy || dirty || view.revision === 0 || unconfirmedCurrent.length > 0} onClick={confirm}>确认月结</Button>
        </Space>}
        {locked && <Typography.Text type="secondary">确认时间 {view.confirmedAt?.replace("T", " ")}</Typography.Text>}
      </div>
    </section>
  </>;
}
