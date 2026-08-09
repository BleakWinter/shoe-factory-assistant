import type { OrderPackingDetail, OrderRecordDetail } from "../types/order";

function normalizeMatchText(value?: string | number | null) {
  if (value === null || value === undefined) {
    return "";
  }
  return String(value).trim().replace(/\s+/g, "").toUpperCase();
}

function parseCarton(value?: string | number | null) {
  const text = String(value ?? "").trim();
  const match = text.match(/^(.*?)(\d+)(.*?)$/);
  if (!match) {
    return null;
  }
  return {
    prefix: normalizeMatchText(match[1]),
    number: Number(match[2]),
    suffix: normalizeMatchText(match[3]),
  };
}

function getCartonRange(start?: string | number | null, end?: string | number | null) {
  const startCarton = parseCarton(start);
  const endCarton = parseCarton(end) || startCarton;
  if (!startCarton || !endCarton || startCarton.prefix !== endCarton.prefix || startCarton.suffix !== endCarton.suffix) {
    return null;
  }
  return {
    prefix: startCarton.prefix,
    suffix: startCarton.suffix,
    start: Math.min(startCarton.number, endCarton.number),
    end: Math.max(startCarton.number, endCarton.number),
  };
}

function isPackingRangeInsideOrderRange(detail: OrderRecordDetail, packingDetail: OrderPackingDetail) {
  const detailRange = getCartonRange(detail.cartonStart, detail.cartonEnd);
  const packingRange = getCartonRange(packingDetail.cartonStart, packingDetail.cartonEnd);
  if (!detailRange || !packingRange) {
    return false;
  }
  return (
    detailRange.prefix === packingRange.prefix &&
    detailRange.suffix === packingRange.suffix &&
    detailRange.start <= packingRange.start &&
    packingRange.end <= detailRange.end
  );
}

function hasConflictingStyleNo(detail: OrderRecordDetail, packingDetail: OrderPackingDetail) {
  const developmentNo = normalizeMatchText(detail.developmentNo);
  const companyStyleNo = normalizeMatchText(packingDetail.companyStyleNo);

  // 旧数据可能缺少其中一个款号，此时仍允许通过箱号匹配；两边都有值时必须是同一款。
  return Boolean(developmentNo && companyStyleNo && developmentNo !== companyStyleNo);
}

export function isMatchingPackingDetail(detail: OrderRecordDetail, packingDetail: OrderPackingDetail) {
  if (detail.orderId && packingDetail.orderId && detail.orderId !== packingDetail.orderId) {
    return false;
  }
  if (hasConflictingStyleNo(detail, packingDetail)) {
    return false;
  }
  return isPackingRangeInsideOrderRange(detail, packingDetail);
}

export function getMatchingPackingDetails(detail: OrderRecordDetail, packingDetails: OrderPackingDetail[]) {
  return packingDetails.filter((packingDetail) => isMatchingPackingDetail(detail, packingDetail));
}

export function hasMatchingPackingDetails(detail: OrderRecordDetail, packingDetails: OrderPackingDetail[]) {
  return getMatchingPackingDetails(detail, packingDetails).length > 0;
}
