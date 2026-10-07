import type { Decimal, QuotationDraft, QuotationItem } from "../types/quotation";

// 金额以 10^-8 元存储；四位用量 × 四位单价，不提前舍入各行。
const SCALE = 100000000n;
function scaled4(value: Decimal): bigint | null {
  if (value === null || value === "") return null;
  const text = String(value);
  if (!/^\d+(\.\d{0,4})?$/.test(text)) return null;
  const [whole, fraction = ""] = text.split(".");
  return BigInt(whole) * 10000n + BigInt(fraction.padEnd(4, "0"));
}
export function decimalText(value: bigint | null, places = 8): string {
  if (value === null) return "待填写";
  const whole = value / SCALE;
  const fraction = String(value % SCALE).padStart(8, "0").slice(0, places).replace(/0+$/, "");
  return String(whole) + (fraction ? `.${fraction}` : "");
}
export function priceText(value: Decimal): string { return value === null || value === "" ? "待填写" : Number(value).toFixed(2); }
export function itemAmount(item: QuotationItem): bigint | null {
  const quantity = scaled4(item.quantity), price = scaled4(item.unitPrice);
  return quantity === null || price === null ? null : quantity * price;
}
export function calculateQuotation(draft: QuotationDraft) {
  function sum(category: QuotationItem["category"]): bigint | null {
    const rows = draft.items.filter(item => item.category === category);
    if (category === "OUTSOLE" && rows.length === 0) return null;
    let total = 0n;
    for (const row of rows) { const amount = itemAmount(row); if (amount === null) return null; total += amount; }
    return total;
  }
  const materials = sum("MATERIAL"), auxiliary = sum("FEE"), outsole = draft.includeOutsole ? sum("OUTSOLE") : 0n;
  const labor = scaled4(draft.laborCost);
  const calculated = materials === null || auxiliary === null || outsole === null || labor === null ? null : materials + auxiliary + outsole + labor * 10000n;
  let final: bigint | null = null;
  if (draft.manualPrice) { const value = scaled4(draft.finalPrice); final = value === null ? null : value * 10000n; }
  else if (calculated !== null) {
    const step = draft.roundingMode === "CENT" ? 1000000n : SCALE;
    const adjustment = draft.roundingMode === "CEIL" ? step - 1n : step / 2n;
    final = ((calculated + adjustment) / step) * step;
  }
  const finalPrice = final === null ? null : `${final / SCALE}.${String(final % SCALE).padStart(8, "0").slice(0, 2)}`;
  return { materials, auxiliary, outsole, calculated, finalPrice };
}
export function today() { const date = new Date(); return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,"0")}-${String(date.getDate()).padStart(2,"0")}`; }
export function newQuotation(): QuotationDraft {
  const item = (category: QuotationItem["category"], partName: string, unit = "双"): QuotationItem => ({ category, partName, materialName: "", quantity: null, unit, unitPrice: null, remark: "" });
  return { customerId: null, developmentNo: "", lastNo: "", colorMaterial: "", quotationDate: today(), imageKey: null,
    laborCost: null, includeOutsole: false, roundingMode: "CEIL", manualPrice: false, finalPrice: null, note: "",
    items: [item("MATERIAL", "鞋面", "尺"), item("MATERIAL", "内里", "码"), item("FEE", "包装"), item("FEE", "胶水 / 辅料"),
      item("OUTSOLE", "大底"), item("OUTSOLE", "中底"), item("OUTSOLE", "鞋跟")] };
}
export function quotationDraft(quote: QuotationDraft): QuotationDraft {
  return { customerId: quote.customerId, developmentNo: quote.developmentNo || "", lastNo: quote.lastNo || "", colorMaterial: quote.colorMaterial || "",
    quotationDate: quote.quotationDate, imageKey: quote.imageKey, imageUrl: quote.imageUrl, sourceDetailId: quote.sourceDetailId,
    laborCost: quote.laborCost, includeOutsole: quote.includeOutsole, roundingMode: quote.roundingMode, manualPrice: quote.manualPrice,
    finalPrice: quote.finalPrice, note: quote.note || "", items: quote.items.map(item => ({ ...item, partName: item.partName || "", materialName: item.materialName || "", unit: item.unit || "", remark: item.remark || "" })) };
}
export function adoptionIssue(draft: QuotationDraft): string | null {
  if (!draft.customerId || !draft.developmentNo.trim() || !draft.quotationDate) return "请补齐客户、款号和报价日期";
  if (calculateQuotation(draft).calculated === null || calculateQuotation(draft).finalPrice === null) return "请补齐用量、单价、加工费及最终报价";
  if (draft.items.some(item => (item.category !== "OUTSOLE" || draft.includeOutsole) && (!item.partName.trim() || !item.materialName.trim() || !item.unit.trim()))) return "请补齐报价项目的部位、材料 / 工序及单位";
  return null;
}
