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

export function isMatchingPackingDetail(detail: OrderRecordDetail, packingDetail: OrderPackingDetail) {
  return isPackingRangeInsideOrderRange(detail, packingDetail);
}

export function getMatchingPackingDetails(detail: OrderRecordDetail, packingDetails: OrderPackingDetail[]) {
  return packingDetails.filter((packingDetail) => isMatchingPackingDetail(detail, packingDetail));
}

export function hasMatchingPackingDetails(detail: OrderRecordDetail, packingDetails: OrderPackingDetail[]) {
  return getMatchingPackingDetails(detail, packingDetails).length > 0;
}
