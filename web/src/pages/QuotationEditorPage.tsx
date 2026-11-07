import { ArrowLeftOutlined, CheckOutlined, CopyOutlined, DeleteOutlined, DownloadOutlined, EyeOutlined, HolderOutlined, PlusOutlined, PrinterOutlined, SaveOutlined, UploadOutlined } from "@ant-design/icons";
import { Alert, App, Button, Checkbox, Dropdown, Image, Input, InputNumber, Modal, Select, Space, Spin, Tag, Typography } from "antd";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useBeforeUnload, useBlocker, useLocation, useNavigate, useParams } from "react-router-dom";
import { adoptQuotation, exportQuotationExcel, fetchQuotation, fetchQuotationReference, saveQuotation, uploadQuotationImage } from "../api/quotationApi";
import QuotationPreview from "../components/QuotationPreview";
import { useCustomers } from "../hooks/useCustomers";
import type { Decimal, Quotation, QuotationDraft, QuotationItem, QuoteCategory } from "../types/quotation";
import { formatDateTime } from "../utils/format";
import { adoptionIssue, calculateQuotation, decimalText, itemAmount, newQuotation, priceText, quotationDraft, today } from "../utils/quotation";
import { quotationPrintCapacityIssue, quotationPrintRowCounts } from "../utils/quotationPrint";

type EditorDraft = Omit<QuotationDraft, "items"> & { items: (QuotationItem & { rowKey: number })[] };

export default function QuotationEditorPage() {
  const { id } = useParams(), location = useLocation(), navigate = useNavigate(), customers = useCustomers();
  const { message, modal } = App.useApp();
  const [draft, setDraft] = useState<EditorDraft>(() => ({ ...newQuotation(), items: [] })), [saved, setSaved] = useState<Quotation>();
  const [loading, setLoading] = useState(false), [loadError, setLoadError] = useState("");
  const [busy, setBusy] = useState(false), [dirty, setDirty] = useState(false), [currentPrice, setCurrentPrice] = useState<Decimal>(null);
  const [priceStatus, setPriceStatus] = useState<"loading" | "ready" | "error">("ready"), [priceRefresh, setPriceRefresh] = useState(0);
  const [preview, setPreview] = useState(false), [printing, setPrinting] = useState(false);
  const [rowDrag, setRowDrag] = useState<{ sourceKey: number; targetKey?: number }>();
  const nextRowKey = useRef(0);
  const dirtyRef = useRef(false), busyRef = useRef(false), allowNavigation = useRef(false), fileInput = useRef<HTMLInputElement>(null);
  const savedRef = useRef<Quotation>();
  const justSaved = useRef<Quotation>();
  const routeSequence = useRef(0), referenceSequence = useRef(0);
  const totals = calculateQuotation(draft), customerName = customers.find(c => c.id === draft.customerId)?.name;
  function markDirty(value: boolean) { dirtyRef.current = value; setDirty(value); }
  function markBusy(value: boolean) { busyRef.current = value; setBusy(value); }
  function replaceSaved(quote?: Quotation) { savedRef.current = quote; setSaved(quote); }
  function replaceDraft(quote: QuotationDraft) {
    const content = quotationDraft(quote);
    setDraft({ ...content, items: content.items.map(item => ({ ...item, rowKey: nextRowKey.current++ })) });
    setRowDrag(undefined);
  }
  function draftContent(): QuotationDraft {
    return { ...draft, items: draft.items.map(({ rowKey, ...item }) => item) };
  }
  const blocker = useBlocker(() => !allowNavigation.current && (dirtyRef.current || busyRef.current));
  useBeforeUnload(event => { if (dirtyRef.current || busyRef.current) { event.preventDefault(); event.returnValue = ""; } });
  useEffect(() => {
    if (blocker.state !== "blocked") return;
    if (busyRef.current) { message.info("正在保存，请稍等完成后再离开"); blocker.reset(); return; }
    const dialog = modal.confirm({ title: "离开当前报价？", content: "当前修改尚未保存，离开后这些修改会丢失。", okText: "放弃修改并离开", cancelText: "继续编辑",
      onOk: () => { markDirty(false); blocker.proceed(); }, onCancel: () => blocker.reset() });
    return () => dialog.destroy();
  }, [blocker, message, modal]);
  useEffect(() => {
    const sequence = ++routeSequence.current; allowNavigation.current = false;
    setLoadError(""); replaceSaved(undefined); markDirty(false); setPreview(false);
    if (!id) {
      const copy = (location.state as { copy?: QuotationDraft } | null)?.copy;
      const params = new URLSearchParams(location.search), customer = Number(params.get("customerId"));
      replaceDraft(copy ?? { ...newQuotation(), customerId: customer > 0 ? customer : null, developmentNo: params.get("developmentNo") || "" });
      markDirty(Boolean(copy)); setCurrentPrice(null); setLoading(false); return;
    }
    if (justSaved.current?.id === Number(id)) {
      const quote = justSaved.current; justSaved.current = undefined;
      replaceSaved(quote); replaceDraft(quote); setCurrentPrice(quote.currentPrice); setLoading(false); return;
    }
    setLoading(true);
    fetchQuotation(Number(id)).then(quote => {
      if (sequence !== routeSequence.current) return;
      replaceSaved(quote); replaceDraft(quote); setCurrentPrice(quote.currentPrice);
    }).catch(error => { if (sequence === routeSequence.current) setLoadError(error instanceof Error ? error.message : "报价加载失败"); })
      .finally(() => { if (sequence === routeSequence.current) setLoading(false); });
    return () => { routeSequence.current++; };
  }, [id, location.key, location.search, location.state]);
  useEffect(() => {
    const sequence = ++referenceSequence.current;
    setCurrentPrice(null);
    if (!draft.customerId || !draft.developmentNo.trim()) { setPriceStatus("ready"); return; }
    setPriceStatus("loading");
    const timer = window.setTimeout(() => {
      fetchQuotationReference(draft.customerId!, draft.developmentNo.trim()).then(reference => {
        if (sequence === referenceSequence.current) { setCurrentPrice(reference.currentPrice); setPriceStatus("ready"); }
      }).catch(error => { if (sequence === referenceSequence.current) { setPriceStatus("error"); message.error(error instanceof Error ? error.message : "客户单价加载失败"); } });
    }, 350);
    return () => { window.clearTimeout(timer); referenceSequence.current++; };
  }, [draft.customerId, draft.developmentNo, priceRefresh, message]);
  useEffect(() => {
    const finish = () => { document.body.classList.remove("quotation-printing"); setPrinting(false); };
    window.addEventListener("afterprint", finish);
    return () => { window.removeEventListener("afterprint", finish); document.body.classList.remove("quotation-printing"); };
  }, []);
  function update(patch: Partial<EditorDraft>) {
    if ("customerId" in patch || "developmentNo" in patch) setPriceStatus("loading");
    setDraft(previous => {
      const next = { ...previous, ...patch };
      if (("customerId" in patch || "developmentNo" in patch) && previous.sourceDetailId && !previous.imageKey) { next.sourceDetailId = null; next.imageUrl = null; }
      return next;
    }); markDirty(true);
  }
  function updateItem(index: number, patch: Partial<QuotationItem>) { update({ items: draft.items.map((item, i) => i === index ? { ...item, ...patch } : item) }); }
  function moveItem(from: number, to: number) {
    const item = draft.items[from];
    if (busyRef.current || !item || from === to || draft.items[to]?.category !== item.category) return;
    const items = [...draft.items];
    items.splice(from, 1);
    items.splice(to, 0, item);
    update({ items });
  }
  function addItem(category: QuoteCategory) {
    const limit = quotationPrintRowCounts[category];
    if (draft.items.filter(item => item.category === category).length >= limit) { message.info(`该区域已填满 ${limit} 行，请编辑或删除现有项目`); return; }
    update({ items: [...draft.items, { rowKey: nextRowKey.current++, category, partName: "", materialName: "", quantity: null, unit: "双", unitPrice: null, remark: "" }] });
  }
  async function persist(): Promise<Quotation> {
    const source = savedRef.current;
    if (source && !dirtyRef.current) return source;
    const quote = await saveQuotation({ ...draftContent(), finalPrice: draft.manualPrice ? draft.finalPrice : totals.finalPrice }, source);
    replaceSaved(quote); replaceDraft(quote); setCurrentPrice(quote.currentPrice); markDirty(false);
    return quote;
  }
  function showSaved(quote: Quotation) {
    if (!id || Number(id) !== quote.id) { justSaved.current = quote; allowNavigation.current = true; navigate(`/price-configs/quotations/${quote.id}`, { replace: true }); }
  }
  async function save() {
    markBusy(true);
    try { const quote = await persist(); showSaved(quote); message.success(`已保存 V${quote.versionNo}，客户单价未改变`); }
    catch(error) { message.error(error instanceof Error ? error.message : "保存失败"); }
    finally { markBusy(false); }
  }
  function adopt() {
    const issue = adoptionIssue(draft); if (issue) { message.warning(issue); return; }
    if (priceStatus !== "ready") { message.warning(priceStatus === "loading" ? "正在读取当前客户单价，请稍等" : "当前客户单价读取失败，请刷新后再采用"); return; }
    if (saved && !dirty && saved.versionNo !== saved.latestVersionNo) { message.warning("历史报价请先另存为新版本，再采用价格"); return; }
    const confirmation = modal.confirm({ title: "采用为客户单价", okText: "确认采用", cancelText: "继续编辑", okButtonProps: { "aria-label": "确认采用" }, content: <div className="quotation-adopt-confirm">
      <p>{customerName} · {draft.developmentNo}</p><p>当前单价：{currentPrice === null ? "未配置" : `¥ ${priceText(currentPrice)}`} → <strong>¥ {priceText(totals.finalPrice)} / 双</strong></p>
      <p>确认后，该客户此款号的历史统计和月度对账都会按新单价重新计算。{dirty || !saved ? "当前报价会先保存为新版本。" : ""}</p></div>,
      onOk: async () => {
        markBusy(true);
        try { const quote = await persist(); const adopted = await adoptQuotation(quote.id); replaceSaved(adopted); replaceDraft(adopted); setCurrentPrice(adopted.currentPrice); showSaved(adopted); message.success("已采用为客户单价"); }
        catch(error) { message.error(error instanceof Error ? error.message : "采用失败"); throw error; }
        finally { markBusy(false); }
      } });
    // 使用 hook 的 Promise 模式，失败时保留确认框供重试，错误由上方提示处理。
    void confirmation.then(() => undefined, () => undefined);
  }
  async function referenceImage() {
    if (!draft.customerId || !draft.developmentNo.trim()) { message.warning("请先选择客户并填写款号"); return; }
    markBusy(true);
    try { const reference = await fetchQuotationReference(draft.customerId, draft.developmentNo.trim());
      if (!reference.imageUrl) { message.info("该客户此款号暂无订单鞋图，可直接上传"); return; }
      update({ sourceDetailId: reference.sourceDetailId, imageKey: null, imageUrl: reference.imageUrl, colorMaterial: draft.colorMaterial || reference.colorMaterial || "" });
      message.success("已带入订单鞋图，保存时会留存独立副本");
    } catch(error) { message.error(error instanceof Error ? error.message : "带入失败"); }
    finally { markBusy(false); }
  }
  async function upload(file?: File) {
    if (!file) return;
    if (!/image\/(png|jpeg)/.test(file.type) || file.size > 10 * 1024 * 1024) { message.warning("请选择 10MB 以内的 PNG 或 JPG 鞋图"); return; }
    markBusy(true);
    try { const image = await uploadQuotationImage(file); update({ ...image, sourceDetailId: null }); message.success("鞋图已上传，保存报价后留存"); }
    catch(error) { message.error(error instanceof Error ? error.message : "上传失败"); }
    finally { markBusy(false); }
  }
  async function copy() {
    markBusy(true);
    try {
      // 尚未保存的订单图先留存副本，复制后的新款也能独立使用鞋图。
      const content = draft.sourceDetailId && !draft.imageKey ? quotationDraft(await persist()) : quotationDraft(draftContent());
      allowNavigation.current = true;
      navigate("/price-configs/quotations/new", { state: { copy: { ...content, developmentNo: "", quotationDate: today(), sourceDetailId: null } } });
    } catch(error) { message.error(error instanceof Error ? error.message : "复制失败"); }
    finally { markBusy(false); }
  }
  async function exportExcel() {
    const issue = quotationPrintCapacityIssue(draft); if (issue) { message.warning(issue); return; }
    markBusy(true);
    try { const quote = await persist(), blob = await exportQuotationExcel(quote.id);
      const url = URL.createObjectURL(blob), anchor = document.createElement("a"); anchor.href = url; anchor.download = `报价-${quote.developmentNo || "未填款号"}-V${quote.versionNo}.xlsx`;
      anchor.click(); window.setTimeout(() => URL.revokeObjectURL(url), 1000); showSaved(quote); message.success(`已导出 V${quote.versionNo} 报价单`);
    } catch(error) { message.error(error instanceof Error ? error.message : "导出失败"); }
    finally { markBusy(false); }
  }
  async function print() {
    const issue = quotationPrintCapacityIssue(draft); if (issue) { message.warning(issue); return; }
    setPrinting(true); document.body.classList.add("quotation-printing");
    // 等待打印副本和图片就绪，浏览器打印可选择“另存为 PDF”。
    await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));
    const images = Array.from(document.querySelectorAll<HTMLImageElement>(".quotation-print-root img"));
    await Promise.all(images.map(image => image.decode().catch(() => undefined)));
    await document.fonts.ready;
    await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));
    window.print(); document.body.classList.remove("quotation-printing"); setPrinting(false);
  }
  if (loading) return <div className="page-panel quotation-loading"><Spin tip="加载报价…"/></div>;
  if (loadError) return <div className="workspace"><Alert type="error" showIcon message={loadError}/><Button onClick={() => navigate("/price-configs/quotations")}>返回报价列表</Button></div>;
  const history = saved && saved.versionNo !== saved.latestVersionNo;
  const draggedIndex = rowDrag ? draft.items.findIndex(item => item.rowKey === rowDrag.sourceKey) : -1;
  function section(category: QuoteCategory, title: string, amount: bigint | null, help: string) {
    const rows = draft.items.map((item, index) => ({ item, index })).filter(({ item }) => item.category === category);
    return <section className="page-panel quotation-section">
      <div className="quotation-section-heading"><div><h3>{title}</h3><span>{help}</span></div><Space><span className="quotation-subtotal">小计 <strong>{decimalText(amount)}</strong> 元</span>
        <Button size="small" icon={<PlusOutlined/>} disabled={busy} onClick={() => addItem(category)}>添加项目</Button></Space></div>
      <div className="quotation-table-scroll"><table className="quotation-edit-table"><thead><tr><th aria-label="移动顺序"/>{["部位", "材料 / 工序", "用量", "单位", "单价", "金额", "备注", ""].map((name, index) => <th key={index}>{name}</th>)}</tr></thead>
        <tbody>{rows.map(({ item, index }, position) => <tr key={item.rowKey}
          className={draggedIndex === index ? "quotation-row-dragging" : undefined}
          data-drop-position={rowDrag?.targetKey === item.rowKey ? (draggedIndex < index ? "after" : "before") : undefined}
          onDragOver={event => {
            if (!rowDrag || busy || draggedIndex === index || draft.items[draggedIndex]?.category !== category) return;
            event.preventDefault();
            event.dataTransfer.dropEffect = "move";
            if (rowDrag.targetKey !== item.rowKey) setRowDrag({ ...rowDrag, targetKey: item.rowKey });
          }}
          onDragLeave={event => {
            if (event.relatedTarget instanceof Node && event.currentTarget.contains(event.relatedTarget)) return;
            setRowDrag(drag => drag && drag.targetKey === item.rowKey ? { sourceKey: drag.sourceKey } : drag);
          }}
          onDrop={event => {
            if (!rowDrag || draft.items[draggedIndex]?.category !== category) return;
            event.preventDefault();
            moveItem(draggedIndex, index);
            setRowDrag(undefined);
          }}>
          <td className="quotation-row-order">
            <Dropdown trigger={["click"]} menu={{
              items: [
                { key: "up", label: "上移一行", disabled: position === 0 },
                { key: "down", label: "下移一行", disabled: position === rows.length - 1 },
              ],
              onClick: ({ key }) => {
                const neighbor = rows[position + (key === "up" ? -1 : 1)];
                if (neighbor) moveItem(index, neighbor.index);
              },
            }}>
              <Button type="text" size="small" className="quotation-row-handle" icon={<HolderOutlined/>}
                aria-label={`移动${title}第${position + 1}行`} title="拖动排序，或点击选择上移／下移"
                disabled={busy || rows.length < 2} draggable={!busy && rows.length > 1}
                onDragStart={event => {
                  event.dataTransfer.setData("application/x-quotation-row", String(item.rowKey));
                  event.dataTransfer.effectAllowed = "move";
                  setRowDrag({ sourceKey: item.rowKey });
                }}
                onDragEnd={() => setRowDrag(undefined)}/>
            </Dropdown>
          </td>
          <td><Input aria-label={`${title}部位${index+1}`} value={item.partName} maxLength={100} disabled={busy} onChange={event => updateItem(index, { partName: event.target.value })}/></td>
          <td><Input aria-label={`${title}材料${index+1}`} placeholder="材料或工序名称" value={item.materialName} maxLength={255} disabled={busy} onChange={event => updateItem(index, { materialName: event.target.value })}/></td>
          <td><InputNumber aria-label={`${title}用量${index+1}`} stringMode min="0" max="99999999.9999" precision={4} value={item.quantity} disabled={busy} onChange={value => updateItem(index, { quantity: value })}/></td>
          <td><Input aria-label={`${title}单位${index+1}`} value={item.unit} maxLength={16} disabled={busy} onChange={event => updateItem(index, { unit: event.target.value })}/></td>
          <td><InputNumber aria-label={`${title}单价${index+1}`} stringMode min="0" max="99999999.9999" precision={4} value={item.unitPrice} disabled={busy} onChange={value => updateItem(index, { unitPrice: value })}/></td>
          <td className="quotation-amount">{decimalText(itemAmount(item))}</td>
          <td><Input aria-label={`${title}备注${index+1}`} value={item.remark} maxLength={1000} disabled={busy} onChange={event => updateItem(index, { remark: event.target.value })}/></td>
          <td><Button type="text" danger icon={<DeleteOutlined/>} aria-label={`删除${title}项目${index+1}`} disabled={busy} onClick={() => update({ items: draft.items.filter((_, i) => i !== index) })}/></td>
        </tr>)}</tbody></table></div>
    </section>;
  }
  return <div className="workspace quotation-workspace">
    <div className="toolbar-band quotation-toolbar"><div><Space><Button aria-label="报价列表" type="text" icon={<ArrowLeftOutlined/>} disabled={busy} onClick={() => navigate("/price-configs/quotations")}>报价列表</Button><Typography.Title level={3}>{saved ? "编辑报价" : "新建报价"}</Typography.Title>
      <Tag color={dirty ? "gold" : "blue"}>{dirty ? "有未保存修改" : saved ? `已保存 V${saved.versionNo}` : "草稿"}</Tag></Space><div className="quotation-toolbar-help">从材料到最终报价，在一页完成。用量和单价最多四位小数。</div></div>
      <Space wrap><Button aria-label="复制为新款" icon={<CopyOutlined/>} disabled={busy} onClick={() => void copy()}>复制为新款</Button><Button aria-label="预览" icon={<EyeOutlined/>} disabled={busy} onClick={() => setPreview(true)}>预览</Button><Button aria-label="导出 Excel" icon={<DownloadOutlined/>} disabled={busy} onClick={() => void exportExcel()}>导出 Excel</Button></Space></div>
    {history && <Alert type="info" showIcon message={`正在查看历史版本 V${saved.versionNo}，最新为 V${saved.latestVersionNo}。修改后保存会创建新版本。`}/>}
    <section className="page-panel quotation-basics">
      <div className="quotation-photo">{draft.imageUrl ? <Image src={draft.imageUrl} height={138} style={{ objectFit: "contain", maxWidth: "100%" }}/> : <div className="quotation-photo-empty"><UploadOutlined/><span>添加鞋款图片</span></div>}
        <Space size={5} wrap><Button size="small" icon={<UploadOutlined/>} disabled={busy} onClick={() => fileInput.current?.click()}>上传鞋图</Button><Button size="small" disabled={busy} onClick={() => void referenceImage()}>带入订单图</Button>
          {draft.imageUrl && <Button size="small" type="text" disabled={busy} onClick={() => update({ imageKey: null, imageUrl: null, sourceDetailId: null })}>移除</Button>}</Space>
        <input type="file" ref={fileInput} hidden accept="image/png,image/jpeg" onChange={event => { void upload(event.target.files?.[0]); event.target.value = ""; }}/></div>
      <div className="quotation-fields">
        <label><span>客户</span><Select aria-label="报价客户" allowClear placeholder="选择客户" value={draft.customerId ?? undefined} disabled={busy || Boolean(saved?.customerId)} options={customers.map(c => ({ label: c.name, value: c.id }))} onChange={value => update({ customerId: value ?? null })}/></label>
        <label><span>款号</span><Input aria-label="报价款号" placeholder="输入开发款号" value={draft.developmentNo} maxLength={128} disabled={busy || Boolean(saved?.developmentNo)} onChange={event => update({ developmentNo: event.target.value })}/></label>
        <label><span>楦号</span><Input aria-label="楦号" value={draft.lastNo} maxLength={64} disabled={busy} onChange={event => update({ lastNo: event.target.value })}/></label>
        <label><span>报价日期</span><Input aria-label="报价日期" type="date" value={draft.quotationDate || ""} disabled={busy} onChange={event => update({ quotationDate: event.target.value || null })}/></label>
        <label className="quotation-field-wide"><span>颜色 / 材料</span><Input aria-label="颜色 / 材料" placeholder="例如：杏色超纤" value={draft.colorMaterial} maxLength={255} disabled={busy} onChange={event => update({ colorMaterial: event.target.value })}/></label>
        <label><span>报价版本</span>{saved ? <Select aria-label="报价版本" value={saved.id} disabled={busy} options={saved.versions.map(version => ({ value: version.id, label: `V${version.versionNo} · ¥ ${priceText(version.finalPrice)}${version.adoptedAt ? " · 曾采用" : ""}` }))} onChange={value => navigate(`/price-configs/quotations/${value}`)}/> : <div className="quotation-version-empty">首次保存后生成 V1</div>}</label>
      </div>
    </section>
    <div className="quotation-editor-grid"><div className="quotation-editor-main">
      {section("MATERIAL", "鞋面材料", totals.materials, "金额 = 用量 × 单价")}
      {section("FEE", "包装与辅料", totals.auxiliary, "填写实际包装、胶水和其他辅料费用")}
      <section className="page-panel quotation-labor"><div><h3>加工费</h3><span>每双鞋的加工费用</span></div><Space><InputNumber aria-label="加工费" stringMode min="0" max="99999999.9999" precision={4} value={draft.laborCost} disabled={busy} placeholder="待填写" onChange={value => update({ laborCost: value })}/><span>元 / 双</span></Space></section>
      <section className="page-panel quotation-outsole-toggle"><Checkbox disabled={busy} checked={draft.includeOutsole} onChange={event => update({ includeOutsole: event.target.checked })}>计入大底 / 中底 / 鞋跟费用</Checkbox><span>{draft.includeOutsole ? "将底料明细计入总价" : "本次报价不包含底料费用"}</span></section>
      {draft.includeOutsole && section("OUTSOLE", "大底 / 中底 / 鞋跟", totals.outsole, "底料单独计算，纳入本次最终报价")}
      <section className="page-panel quotation-note"><label><span>报价备注</span><Input.TextArea aria-label="报价备注" rows={3} maxLength={2000} showCount value={draft.note} disabled={busy} placeholder="填写特殊工艺、费用说明或报价条件" onChange={event => update({ note: event.target.value })}/></label></section>
    </div><aside className="page-panel quotation-summary">
      <h3>本款报价</h3><dl><div><dt>鞋面材料</dt><dd>{decimalText(totals.materials)}</dd></div><div><dt>包装与辅料</dt><dd>{decimalText(totals.auxiliary)}</dd></div><div><dt>加工费</dt><dd>{draft.laborCost === null ? "待填写" : String(draft.laborCost)}</dd></div><div><dt>底料费用</dt><dd>{draft.includeOutsole ? decimalText(totals.outsole) : "未计入"}</dd></div><div className="quotation-calculated"><dt>计算合计</dt><dd data-testid="calculated-total">{decimalText(totals.calculated)}</dd></div></dl>
      <label className="quotation-rounding"><span>取整方式</span><Select aria-label="取整方式" value={draft.roundingMode} disabled={busy || draft.manualPrice} options={[{ value: "CEIL", label: "向上取整元" }, { value: "CENT", label: "保留两位小数" }, { value: "ROUND", label: "四舍五入整元" }]} onChange={value => update({ roundingMode: value })}/></label>
      <div className="quotation-final"><span>最终报价 · 元 / 双</span>{draft.manualPrice ? <InputNumber aria-label="手动最终报价" stringMode min="0" max="99999999.99" precision={2} value={draft.finalPrice} disabled={busy} onChange={value => update({ finalPrice: value })}/> : <strong data-testid="final-price">{priceText(totals.finalPrice)}</strong>}</div>
      <Checkbox checked={draft.manualPrice} disabled={busy} onChange={event => update({ manualPrice: event.target.checked, finalPrice: totals.finalPrice })}>手动调整最终报价</Checkbox>
      <div className="quotation-current-price"><span>当前客户单价</span><strong>{priceStatus === "loading" ? "读取中…" : priceStatus === "error" ? "读取失败" : currentPrice === null ? "未配置" : `¥ ${priceText(currentPrice)}`}</strong></div>
      {priceStatus === "error" && <Button size="small" type="link" disabled={busy} onClick={() => setPriceRefresh(value => value + 1)}>刷新客户单价</Button>}
      <p className="quotation-save-hint">保存报价只留存成本与版本。确认采用后，客户单价和历史对账会同步更新。</p>
      {saved?.adoptedAt && <p className="quotation-adopted">V{saved.versionNo} 曾采用于 {formatDateTime(saved.adoptedAt)}</p>}
    </aside></div>
    <footer className="page-panel quotation-footer"><span>{dirty ? "当前修改尚未保存" : saved ? `V${saved.versionNo} · ${formatDateTime(saved.createdAt)}` : "可先保存草稿，稍后继续补齐"}</span>
      <Space wrap><Button aria-label="打印 / PDF" icon={<PrinterOutlined/>} disabled={busy} onClick={() => void print()}>打印 / PDF</Button><Button aria-label={saved ? "保存新版本" : "保存报价"} icon={<SaveOutlined/>} loading={busy} disabled={busy} onClick={() => void save()}>{saved ? "保存新版本" : "保存报价"}</Button><Button aria-label="采用为客户单价" type="primary" icon={<CheckOutlined/>} disabled={busy} onClick={adopt}>采用为客户单价</Button></Space></footer>
    <Modal open={preview} title="报价单预览" width={980} onCancel={() => setPreview(false)} footer={<Space><Button aria-label="关闭" onClick={() => setPreview(false)}>关闭</Button><Button type="primary" icon={<PrinterOutlined/>} onClick={() => void print()}>打印 / 另存 PDF</Button></Space>}>
      {dirty && <Alert type="info" message="预览包含当前未保存的修改。" style={{ marginBottom: 16 }}/>}<QuotationPreview draft={draft} customerName={customerName}/></Modal>
    {printing && createPortal(<div className="quotation-print-root"><QuotationPreview draft={draft} customerName={customerName}/></div>, document.body)}
  </div>;
}
