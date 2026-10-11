import { useLayoutEffect, useRef, useState } from "react";
import { Alert } from "antd";
import type { CSSProperties, ReactNode } from "react";
import type { QuotationDraft, QuoteCategory } from "../types/quotation";
import { calculateQuotation, itemAmount } from "../utils/quotation";
import {
  quotationPrintTemplate as template, quotationPrintProfile, quotationCellStyle, quotationRowHeight,
  quotationPrintRows, quotationPrintAmount, quotationPrintDecimal, quotationOutsoleAmount, quotationPrintCapacityIssue,
  quotationPrintGeometry,
} from "../utils/quotationPrint";

export default function QuotationPreview({ draft, customerName }: { draft: QuotationDraft; customerName?: string }) {
  const profile = quotationPrintProfile(draft.note);
  const capacityIssue = quotationPrintCapacityIssue(draft);
  if (capacityIssue) return <Alert type="warning" showIcon message={capacityIssue}/>;
  const totals = calculateQuotation(draft);
  const { columns, width, page } = quotationPrintGeometry(profile);
  function cell(row: number, col: number, text: string, props: { colSpan?: number; rowSpan?: number; children?: ReactNode } = {}) {
    const style = quotationCellStyle(profile, row, col);
    const right = quotationCellStyle(profile, row, col + (props.colSpan ?? 1) - 1);
    return <td key={col} colSpan={props.colSpan} rowSpan={props.rowSpan} style={{
      fontSize: `${style.fontSize}pt`, fontWeight: style.bold ? "bold" : "normal", color: style.color,
      textAlign: style.align as CSSProperties["textAlign"],
      borderLeft: style.borders.includes("left") ? "0.5pt solid black" : undefined,
      borderRight: right.borders.includes("right") ? "0.5pt solid black" : undefined,
      borderTop: style.borders.includes("top") ? "0.5pt solid black" : undefined,
      borderBottom: style.borders.includes("bottom") ? "0.5pt solid black" : undefined,
    }}>{props.children ?? <FittedText text={text}/>}</td>;
  }
  function row(rowNo: number, cells: ReactNode) {
    return <tr key={rowNo} style={{ height: `${quotationRowHeight(profile, rowNo)}pt` }}>{cells}</tr>;
  }
  function items(category: QuoteCategory, start: number) {
    const rows = quotationPrintRows(draft, category, profile);
    return rows.map(({ label, item }, i) => {
      const rowNo = start + i;
      const values = [label, item?.materialName ?? "", item ? quotationPrintDecimal(item.quantity, quotationCellStyle(profile, rowNo, 2)) : "",
        item?.unit ?? "", item ? quotationPrintDecimal(item.unitPrice, quotationCellStyle(profile, rowNo, 4)) : "",
        item ? quotationPrintAmount(itemAmount(item), quotationCellStyle(profile, rowNo, 5)) : "", item?.remark ?? ""];
      return <tr key={`${category}-${i}`} style={{ height: `${quotationRowHeight(profile, rowNo)}pt` }}>
        {values.map((value, col) => cell(rowNo, col, value))}</tr>;
    });
  }
  function total(rowNo: number, label: string, amount: string) {
    return row(rowNo, Array.from({ length: 7 }, (_, col) => cell(rowNo, col, col === 0 ? label : col === 5 ? amount : "")));
  }
  const information = [["款号", draft.developmentNo], ["楦号", draft.lastNo], ["客户", customerName ?? ""],
    ["日期", draft.quotationDate?.replace(/-(0?)(\d+)/g, ".$2") ?? ""]];
  return <A4Paper>
    <div className="quotation-paper-content" style={{ width, zoom: page.scale }}>
    <table aria-label="报价明细"><colgroup>{columns.map((col, i) => <col key={i} style={{ width: `${col / width * 100}%` }}/>)}</colgroup><tbody>
      {row(1, cell(1, 0, template.title, { colSpan: 7 }))}
      {row(2, <>{cell(2, 0, template.subtitle, { colSpan: 2 })}{[2, 3, 4, 5].map(col => cell(2, col, ""))}{cell(2, 6, profile.newColor)}</>)}
      {information.map(([label, value], i) => row(3 + i, <>
        {i === 0 && <td className="quotation-paper-image" colSpan={4} rowSpan={4}>
          <div className="quotation-paper-image-box" style={{ height: [3,4,5,6].reduce((sum, index) => sum + quotationRowHeight(profile, index), 0) * 96 / 72 - 2 / 3 }}>
          {draft.imageUrl && <img src={draft.imageUrl} alt="鞋款" style={profile.imageBox}/>}</div></td>}
        {cell(3 + i, 4, label)}{cell(3 + i, 5, value, { colSpan: 2 })}</>))}
      {row(7, template.headers.map((label, col) => cell(7, col, label)))}
      {items("MATERIAL", 8)}
      {total(18, "材料成本合计", quotationPrintAmount(totals.materials, quotationCellStyle(profile, 18, 5)))}
      {items("FEE", 19)}
      {row(25, <>{cell(25, 0, "加工费")}{cell(25, 1, "")}{cell(25, 2, "")}{cell(25, 3, "", { colSpan: 2 })}
        {cell(25, 5, quotationPrintDecimal(draft.laborCost, quotationCellStyle(profile, 25, 5)))}{cell(25, 6, "")}</>)}
      {row(26, Array.from({ length: 7 }, (_, col) => cell(26, col, col === 0 ? "加工费合计" : col === 5
        ? quotationPrintDecimal(totals.finalPrice, quotationCellStyle(profile, 26, 5)) : col === 6 && draft.includeOutsole ? "含大底" : "")))}
      {row(27, Array.from({ length: 7 }, (_, col) => cell(27, col, col === 0 ? "大底报价" : "")))}
      {row(28, template.outsoleHeaders.map((label, col) => cell(28, col, label)))}
      {items("OUTSOLE", 29)}
      {total(32, "合计", quotationPrintAmount(quotationOutsoleAmount(draft), quotationCellStyle(profile, 32, 5)))}
    </tbody></table></div>
  </A4Paper>;
}

function A4Paper({ children }: { children: ReactNode }) {
  const stage = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(1);
  useLayoutEffect(() => {
    const element = stage.current!;
    const measure = () => setScale(Math.max(0.1, Math.min(1, (element.clientWidth - 32) / (template.printPage.widthMm * 96 / 25.4))));
    const observer = new ResizeObserver(measure);
    observer.observe(element); measure();
    return () => observer.disconnect();
  }, []);
  const page = template.printPage;
  return <div className="quotation-preview-scroll" ref={stage}>
    <article className="quotation-paper" aria-label="A4 清化鞋业成本分析表" style={{
      width: `${page.widthMm}mm`, height: `${page.heightMm}mm`, padding: `${page.marginVerticalMm}mm ${page.marginHorizontalMm}mm`, zoom: scale,
    }}>{children}</article>
  </div>;
}

// Excel 的“缩小字体填充”保持固定行高，避免长款号、材料名改变原表的网格。
function FittedText({ text }: { text: string }) {
  const span = useRef<HTMLSpanElement>(null);
  const [scale, setScale] = useState(1);
  useLayoutEffect(() => {
    const element = span.current;
    if (!element) return;
    const measure = () => {
      const width = element.parentElement!.clientWidth - 4;
      const natural = element.scrollWidth;
      setScale(natural > width && width > 0 ? width / natural : 1);
    };
    const observer = new ResizeObserver(measure);
    observer.observe(element.parentElement!);
    void document.fonts.ready.then(measure);
    measure();
    return () => observer.disconnect();
  }, [text]);
  return <span ref={span} className="quotation-cell-text" style={{ zoom: scale }}>{text}</span>;
}
