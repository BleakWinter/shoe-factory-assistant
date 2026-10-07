import type { QuotationDraft } from "../types/quotation";
import { calculateQuotation, decimalText, itemAmount, priceText } from "../utils/quotation";
export default function QuotationPreview({ draft, customerName, version }: { draft: QuotationDraft; customerName?: string; version?: number }) {
  const totals = calculateQuotation(draft);
  return <article className="quotation-paper">
    <h1>鞋款成本报价单</h1>
    <div className="quotation-paper-header">
      <dl><div><dt>客户</dt><dd>{customerName || "待填写"}</dd></div><div><dt>款号</dt><dd>{draft.developmentNo || "待填写"}</dd></div>
        <div><dt>楦号</dt><dd>{draft.lastNo || "—"}</dd></div><div><dt>日期</dt><dd>{draft.quotationDate || "待填写"}</dd></div>
        <div><dt>颜色 / 材料</dt><dd>{draft.colorMaterial || "—"}</dd></div><div><dt>版本</dt><dd>{version ? `V${version}` : "未保存草稿"}</dd></div></dl>
      {draft.imageUrl && <img src={draft.imageUrl} alt="鞋款"/>}
    </div>
    <table><thead><tr>{["部位", "材料 / 工序", "用量", "单位", "单价", "金额", "备注"].map(label => <th key={label}>{label}</th>)}</tr></thead>
      <tbody>{(["MATERIAL", "FEE", "OUTSOLE"] as const).map((category, index) => {
        const labels = ["鞋面材料", "包装与辅料", "大底 / 中底 / 鞋跟"];
        const amount = [totals.materials, totals.auxiliary, totals.outsole][index];
        if (category === "OUTSOLE" && !draft.includeOutsole) return <tr key={category}><th colSpan={7}>大底 / 中底 / 鞋跟：未计入本次报价</th></tr>;
        return <QuotationPreviewGroup key={category} label={labels[index]} rows={draft.items.filter(item => item.category === category)} amount={amount}/>;
      })}<tr><th colSpan={5}>加工费</th><td>{draft.laborCost === null ? "待填写" : String(draft.laborCost)}</td><td>元 / 双</td></tr>
        <tr><th colSpan={5}>计算合计</th><td>{decimalText(totals.calculated)}</td><td>元 / 双</td></tr>
        <tr className="quotation-paper-final"><th colSpan={5}>最终报价</th><td>{priceText(totals.finalPrice)}</td><td>元 / 双</td></tr></tbody></table>
    {draft.note && <p className="quotation-paper-note">备注：{draft.note}</p>}
    <p className="quotation-paper-caption">金额单位：人民币元。空白表示待填写。{draft.includeOutsole ? "本次含底料费用。" : "本次不包含大底、中底及鞋跟费用。"}</p>
  </article>;
}
function QuotationPreviewGroup({label, rows, amount}: {label: string; rows: QuotationDraft["items"]; amount: bigint | null}) {
  return <><tr><th colSpan={7}>{label}</th></tr>{rows.map((item, index) => <tr key={index}>
    <td>{item.partName || "待填写"}</td><td>{item.materialName || "待填写"}</td><td>{item.quantity === null ? "待填写" : String(item.quantity)}</td>
    <td>{item.unit}</td><td>{item.unitPrice === null ? "待填写" : String(item.unitPrice)}</td><td>{decimalText(itemAmount(item))}</td><td>{item.remark}</td>
  </tr>)}<tr><th colSpan={5}>{label}小计</th><td>{decimalText(amount)}</td><td/></tr></>;
}
