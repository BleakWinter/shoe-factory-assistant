import type { ShippingNoteItem } from "../types/order";

const usSizes = ["5", "5.5", "6", "6.5", "7", "7.5", "8", "8.5", "9", "9.5", "10", "11", "12"];
const euGroups = [
  { label: "", span: 1 },
  { label: "35", span: 1 },
  { label: "", span: 1 },
  { label: "36", span: 1 },
  { label: "37", span: 1 },
  { label: "", span: 1 },
  { label: "38", span: 1 },
  { label: "39", span: 1 },
  { label: "", span: 1 },
  { label: "40", span: 1 },
  { label: "41", span: 1 },
  { label: "42", span: 1 },
  { label: "", span: 1 },
];
const colWidths = [
  88, // 订单号
  77, // 开发编号
  56, // 客人
  57.5, // 客人型体
  71, // 英文颜色
  70, // 英文材质
  63, // 颜色/材质
  45, // 商标

  // 尺码列
  26, 26, 26, 26, 26, 26, 26,
  26, 26, 26, 26, 26, 26,

  // 后面统计列
  35, // 双数
  35, // 件数
  35, // 合计
  52, // 开始箱号
  52, // 结束箱号
];
// A4 横向下，40px 明细行加上第一页表头、最后一页签字行后每页稳定放 15 行。
const rowsPerPage = 15;

export interface ShippingNoteSheetProps {
  recipientName?: string;
  shippingDate?: string;
  items: ShippingNoteItem[];
  totalPairs?: number;
  totalCartonCount?: number;
}

export function sumShippingNotePairs(items: ShippingNoteItem[]) {
  return getPrintableItems(items).reduce((total, item) => total + (item.totalPairs || item.pairCount || sumSizeQuantities(item.sizeQuantities)), 0);
}

export function sumShippingNoteCartons(items: ShippingNoteItem[]) {
  return getPrintableItems(items).reduce((total, item) => total + (item.cartonCount || 0), 0);
}

export function countShippingNoteRows(items: ShippingNoteItem[]) {
  return getPrintableItems(items).length;
}

function getPrintableItems(items: ShippingNoteItem[]) {
  return items.flatMap((item) => item.packingItems?.length ? item.packingItems : [item]);
}

function chunkItems(items: ShippingNoteItem[]) {
  const pages: ShippingNoteItem[][] = [];
  for (let index = 0; index < items.length; index += rowsPerPage) {
    pages.push(items.slice(index, index + rowsPerPage));
  }
  return pages.length > 0 ? pages : [[]];
}

function sumSizeQuantities(value?: Record<string, number>) {
  return Object.values(value || {}).reduce((total, count) => total + (Number(count) > 0 ? Number(count) : 0), 0);
}

function getDisplayYear(date?: string) {
  if (date) {
    return new Date(date).getFullYear();
  }
  return new Date().getFullYear();
}

function formatDate(value?: string) {
  if (!value) {
    return "";
  }
  const [year, month, day] = value.split("-");
  if (!year || !month || !day) {
    return value.replace(/-/g, "/");
  }
  return `${year}/${Number(month)}/${Number(day)}`;
}

function text(value?: string | number) {
  return value === undefined || value === null || value === "" ? "" : String(value);
}

function getSizeQuantity(item: ShippingNoteItem, size: string) {
  const entries = Object.entries(item.sizeQuantities || {});
  const exact = entries.find(([key]) => key.trim() === size);
  if (exact) {
    return exact[1] || "";
  }
  const mixed = entries.find(([key]) =>
    key
      .split(/[\\/]/)
      .map((part) => part.trim())
      .includes(size),
  );
  return mixed?.[1] || "";
}

export default function ShippingNoteSheet({
  recipientName = "达为鞋业",
  shippingDate,
  items,
  totalPairs,
  totalCartonCount,
}: ShippingNoteSheetProps) {
  const displayedItems = getPrintableItems(items);
  const pages = chunkItems(displayedItems);
  const pairs = totalPairs ?? sumShippingNotePairs(items);
  const cartons = totalCartonCount ?? sumShippingNoteCartons(items);

  return (
    <div className="shipping-note-pages">
      {pages.map((pageItems, pageIndex) => {
        const isFirstPage = pageIndex === 0;
        const isLastPage = pageIndex === pages.length - 1;

        return (
          <section className="shipping-note-sheet" key={pageIndex}>
            {isFirstPage ? (
              <div className="shipping-note-heading">
                <div className="shipping-note-heading-title">清化鞋厂{getDisplayYear(shippingDate)}年出货单</div>
                <div className="shipping-note-heading-meta">
                  <span className="shipping-note-heading-recipient">收货单位:{recipientName}</span>
                  <span className="shipping-note-heading-date">日期:{formatDate(shippingDate)}</span>
                  <span />
                </div>
              </div>
            ) : null}
            <table className="shipping-note-table">
              <colgroup>
                {colWidths.map((width, index) => (
                  <col key={index} style={{ width }} />
                ))}
              </colgroup>
              <tbody>
                {isFirstPage ? (
                  <>
                    <tr className="shipping-note-header-row shipping-note-fixed-header">
                      <td rowSpan={2}>订单号</td>
                      <td rowSpan={2}>开发编号</td>
                      <td rowSpan={2}>客人</td>
                      <td rowSpan={2}>客人型体</td>
                      <td rowSpan={2}>英文颜色</td>
                      <td rowSpan={2}>英文材质</td>
                      <td rowSpan={2}>颜色/材质</td>
                      <td rowSpan={2}>商标</td>
                      {euGroups.map((group, index) => (
                        <td className="shipping-note-size-header" colSpan={group.span} key={`${group.label}-${index}`}>
                          {group.label}
                        </td>
                      ))}
                      <td rowSpan={2}>双数</td>
                      <td className="shipping-note-size-header" rowSpan={2}>件数</td>
                      <td className="shipping-note-size-header" rowSpan={2}>合计</td>
                      <td rowSpan={2}>开始箱号</td>
                      <td rowSpan={2}>结束箱号</td>
                    </tr>
                    <tr className="shipping-note-header-row">
                      {usSizes.map((size) => (
                        <td className="shipping-note-size-header" key={size}>{size}</td>
                      ))}
                    </tr>
                  </>
                ) : null}
                {pageItems.map((item, index) => (
                  <tr className="shipping-note-detail-row" key={`${item?.sourceDetailId ?? "empty"}-${pageIndex}-${index}`}>
                    <td>{text(item?.orderNo)}</td>
                    <td>{text(item?.developmentNo)}</td>
                    <td>{text(item?.customerName)}</td>
                    <td>{text(item?.customerStyleNo)}</td>
                    <td>{text(item?.englishColor)}</td>
                    <td>{text(item?.englishMaterial)}</td>
                    <td>{text(item?.colorMaterial)}</td>
                    <td>{text(item?.trademark)}</td>
                    {usSizes.map((size) => (
                      <td key={size}>{item ? text(getSizeQuantity(item, size)) : ""}</td>
                    ))}
                    <td>{text(item?.pairCount)}</td>
                    <td>{text(item?.cartonCount)}</td>
                    <td className="shipping-note-size-header">{text(item?.totalPairs)}</td>
                    <td>{text(item?.cartonStart)}</td>
                    <td>{text(item?.cartonEnd)}</td>
                  </tr>
                ))}
                {isLastPage ? (
                  <tr className="shipping-note-footer-row">
                    <td colSpan={3}>收货人签字:</td>
                    <td colSpan={2}>验货人签字:</td>
                    <td />
                    <td colSpan={4}>司机签字:</td>
                    <td />
                    <td colSpan={10}>出货单位：清化鞋业</td>
                    <td />
                    <td>{cartons || ""}</td>
                    <td>{pairs || ""}</td>
                    <td />
                    <td />
                  </tr>
                ) : null}
              </tbody>
            </table>
          </section>
        );
      })}
    </div>
  );
}
