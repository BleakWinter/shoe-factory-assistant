import type { ShippingNoteItem } from "../types/order";

const usSizes = ["5", "5.5", "6", "6.5", "7", "7.5", "8", "8.5", "9", "9.5", "10", "11", "12"];
const euGroups = [
  { label: "35", span: 2 },
  { label: "36", span: 2 },
  { label: "37", span: 2 },
  { label: "38", span: 2 },
  { label: "39", span: 2 },
  { label: "40", span: 1 },
  { label: "41", span: 1 },
  { label: "42", span: 1 },
];
const colWidths = [74, 92, 70, 70, 106, 100, 76, 48, 31, 31, 31, 31, 31, 31, 31, 31, 31, 31, 31, 31, 31, 46, 42, 44, 58, 64];

export interface ShippingNoteSheetProps {
  recipientName?: string;
  shippingDate?: string;
  items: ShippingNoteItem[];
  totalPairs?: number;
  totalCartonCount?: number;
}

export function sumShippingNotePairs(items: ShippingNoteItem[]) {
  return items.reduce((total, item) => total + (item.totalPairs || item.pairCount || sumSizeQuantities(item.sizeQuantities)), 0);
}

export function sumShippingNoteCartons(items: ShippingNoteItem[]) {
  return items.reduce((total, item) => total + (item.cartonCount || 0), 0);
}

function sumSizeQuantities(value?: Record<string, number>) {
  return Object.values(value || {}).reduce((total, count) => total + (Number(count) > 0 ? Number(count) : 0), 0);
}

function formatDate(value?: string) {
  if (!value) {
    return "";
  }
  return value.replace(/-/g, "/");
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
  const displayedItems = items.length >= 3
    ? items
    : [...items, ...Array.from({ length: 3 - items.length }, () => null)];
  const pairs = totalPairs ?? sumShippingNotePairs(items);
  const cartons = totalCartonCount ?? sumShippingNoteCartons(items);

  return (
    <section className="shipping-note-sheet">
      <table className="shipping-note-table">
        <colgroup>
          {colWidths.map((width, index) => (
            <col key={index} style={{ width }} />
          ))}
        </colgroup>
        <tbody>
          <tr className="shipping-note-title-row">
            <td colSpan={26}>清化鞋厂2026年出货单</td>
          </tr>
          <tr className="shipping-note-meta-row">
            <td colSpan={4}>收货单位：{recipientName}</td>
            <td colSpan={4}>日期：{formatDate(shippingDate)}</td>
            <td colSpan={18} />
          </tr>
          <tr className="shipping-note-header-row shipping-note-fixed-header">
            <td rowSpan={2}>订单号</td>
            <td rowSpan={2}>开发编号</td>
            <td rowSpan={2}>客人</td>
            <td rowSpan={2}>客人型体</td>
            <td rowSpan={2}>英文颜色</td>
            <td rowSpan={2}>英文材质</td>
            <td rowSpan={2}>颜色/材质</td>
            <td rowSpan={2}>商标</td>
            {euGroups.map((group) => (
              <td className="shipping-note-size-header" colSpan={group.span} key={group.label}>
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
          {displayedItems.map((item, index) => (
            <tr className="shipping-note-detail-row" key={item?.sourceDetailId ?? `empty-${index}`}>
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
          <tr className="shipping-note-footer-row">
            <td colSpan={3}>收货人签字：</td>
            <td colSpan={3}>验货人签字：</td>
            <td colSpan={5}>司机签字：</td>
            <td colSpan={10}>出货单位：清化鞋业</td>
            <td />
            <td>{cartons || ""}</td>
            <td>{pairs || ""}</td>
            <td />
            <td />
          </tr>
        </tbody>
      </table>
    </section>
  );
}
