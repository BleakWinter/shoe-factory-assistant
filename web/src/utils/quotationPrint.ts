import template from "../../../backend/src/main/resources/quotation-print-layout.json";
import type { Decimal, QuotationDraft, QuotationItem, QuoteCategory } from "../types/quotation";
import { decimalText, itemAmount } from "./quotation";

export interface PrintCellStyle {
  fontSize: number; bold: boolean; color: string; align: string; borders: string[]; numberFormat: string;
}
interface PrintProfile {
  labels: Record<QuoteCategory, string[]>;
  cellStyles: Record<string, number>;
  columns?: number[];
  rowHeights: Record<string, number>;
  scale: number;
  newColor: string;
  imageBox: { left: number; top: number; width: number; height: number };
}
export const quotationPrintTemplate = template;
const base: PrintProfile = template.base;
export function quotationPrintProfile(note: string): PrintProfile {
  const source = /Excel 导入：([^；]+)；工作表：([^；]+)/.exec(note);
  const profiles: Record<string, PrintProfile> = template.sourceProfiles;
  return (source && profiles[`${source[1]}/${source[2].trim()}`]) || base;
}
export function quotationCellStyle(profile: PrintProfile, row: number, col: number): PrintCellStyle {
  const ref = `${"ABCDEFG"[col]}${row}`;
  return template.styles[profile.cellStyles[ref] ?? base.cellStyles[ref] ?? base.cellStyles.A8];
}
export function quotationRowHeight(profile: PrintProfile, row: number): number {
  return (profile.rowHeights[String(row)] ?? base.rowHeights[String(row)] ?? 24) * template.printPage.browserRowHeightFactor;
}
// Excel 列宽不是固定的 CSS 像素；按 WPS 参考页的实际打印宽度分配各列。
export function quotationPrintGeometry(profile: PrintProfile) {
  const page = template.printPage;
  const width = page.tableWidthMm * 96 / 25.4 / page.scale;
  const columns = profile.columns ?? base.columns!;
  const units = columns.reduce((sum, value) => sum + value, 0);
  return { width, columns: columns.map(value => value / units * width), page };
}
export const quotationPrintRowCounts = { MATERIAL: base.labels.MATERIAL.length, FEE: base.labels.FEE.length, OUTSOLE: base.labels.OUTSOLE.length };
const categoryNames = { MATERIAL: "材料", FEE: "费用", OUTSOLE: "大底" };
export function quotationPrintCapacityIssue(draft: QuotationDraft): string | null {
  for (const category of Object.keys(quotationPrintRowCounts) as QuoteCategory[]) {
    const count = quotationPrintRowCounts[category];
    if (draft.items.filter(item => item.category === category).length > count) return `${categoryNames[category]}区域最多容纳 ${count} 个项目，请调整后再预览、打印或导出`;
  }
  return null;
}
// 保留固定行数，按编辑列表顺序展示；空行不填部位，也不参与金额计算。
export function quotationPrintRows(draft: QuotationDraft, category: QuoteCategory, profile: PrintProfile) {
  const entries = draft.items.filter(item => item.category === category);
  const count = profile.labels[category].length;
  if (entries.length > count) throw new Error(quotationPrintCapacityIssue(draft)!);
  return Array.from({ length: count }, (_, index): { label: string; item?: QuotationItem } => {
    const item = entries[index];
    return item ? { label: item.partName, item } : { label: "" };
  });
}
export function quotationPrintAmount(value: bigint | null, style: PrintCellStyle): string {
  if (value === null) return "";
  if (!style.numberFormat.startsWith("0.00")) return decimalText(value);
  const cents = (value + 500000n) / 1000000n;
  return `${cents / 100n}.${String(cents % 100n).padStart(2, "0")}`;
}
export function quotationPrintDecimal(value: Decimal, style: PrintCellStyle): string {
  if (value === null || value === "") return "";
  if (!style.numberFormat.startsWith("0.00")) return String(Number(value));
  const [whole, fraction = ""] = String(value).split(".");
  return quotationPrintAmount(BigInt(whole) * 100000000n + BigInt(fraction.padEnd(8, "0")), style);
}
export function quotationOutsoleAmount(draft: QuotationDraft): bigint | null {
  const rows = draft.items.filter(item => item.category === "OUTSOLE");
  if (!rows.length) return null;
  let total = 0n;
  for (const row of rows) {
    const amount = itemAmount(row);
    if (amount === null) return null;
    total += amount;
  }
  return total;
}
