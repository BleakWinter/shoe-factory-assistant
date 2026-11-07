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
export const quotationItemLimit = 200;
export function quotationPrintCapacityIssue(draft: QuotationDraft): string | null {
  return draft.items.length > quotationItemLimit ? `每份报价最多支持 ${quotationItemLimit} 个项目` : null;
}
// 原模板行数作为最小行数；超出的项目沿用该区域最后一条明细的样式。
export function quotationPrintRows(draft: QuotationDraft, category: QuoteCategory, profile: PrintProfile) {
  const entries = draft.items.filter(item => item.category === category);
  const count = profile.labels[category].length;
  const start = { MATERIAL: 8, FEE: 19, OUTSOLE: 29 }[category];
  return Array.from({ length: Math.max(count, entries.length) }, (_, index) => {
    const item = entries[index];
    return { label: item?.partName ?? "", item, templateRow: start + Math.min(index, count - 1) };
  });
}
export type QuotationPrintLine = {
  type: "item" | "materialTotal" | "labor" | "finalTotal" | "outsoleHeading" | "outsoleHeader" | "outsoleTotal";
  templateRow: number;
  label?: string;
  item?: QuotationItem;
  keepWithNext?: boolean;
};
export function quotationPrintPages(draft: QuotationDraft, profile: PrintProfile): QuotationPrintLine[][] {
  const items = (category: QuoteCategory): QuotationPrintLine[] => {
    const rows = quotationPrintRows(draft, category, profile);
    return rows.map((row, index) => ({ ...row, type: "item", keepWithNext: index === rows.length - 1 }));
  };
  const rows: QuotationPrintLine[] = [
    ...items("MATERIAL"), { type: "materialTotal", templateRow: 18 },
    ...items("FEE"), { type: "labor", templateRow: 25, keepWithNext: true },
    { type: "finalTotal", templateRow: 26 },
    { type: "outsoleHeading", templateRow: 27, keepWithNext: true },
    { type: "outsoleHeader", templateRow: 28, keepWithNext: true },
    ...items("OUTSOLE"), { type: "outsoleTotal", templateRow: 32 },
  ];
  const page = template.printPage;
  const headerHeight = Array.from({ length: 7 }, (_, index) => quotationRowHeight(profile, index + 1)).reduce((sum, value) => sum + value, 0);
  const capacity = (page.heightMm - page.marginVerticalMm * 2) * 72 / 25.4 / page.scale - headerHeight - 2;
  const pages: QuotationPrintLine[][] = [[]];
  let height = 0;
  rows.forEach((line, index) => {
    let required = quotationRowHeight(profile, line.templateRow);
    for (let next = index; rows[next]?.keepWithNext && rows[next + 1]; next++) {
      required += quotationRowHeight(profile, rows[next + 1].templateRow);
    }
    if (height + required > capacity && pages[pages.length - 1].length) {
      pages.push([]); height = 0;
    }
    pages[pages.length - 1].push(line);
    height += quotationRowHeight(profile, line.templateRow);
  });
  return pages;
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
