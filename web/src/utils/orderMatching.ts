import type { OrderPackingDetail, OrderRecordDetail } from "../types/order";

function normalizeMatchText(value?: string | number | null) {
  if (value === null || value === undefined) {
    return "";
  }
  return String(value).trim().replace(/\s+/g, "").toUpperCase();
}

function sameRequired(left?: string | number | null, right?: string | number | null) {
  const leftValue = normalizeMatchText(left);
  const rightValue = normalizeMatchText(right);
  return Boolean(leftValue && rightValue && leftValue === rightValue);
}

function sameOptional(left?: string | number | null, right?: string | number | null) {
  const leftValue = normalizeMatchText(left);
  const rightValue = normalizeMatchText(right);
  return !leftValue || !rightValue || leftValue === rightValue;
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

function hasCartonRange(detail: OrderRecordDetail) {
  return Boolean(getCartonRange(detail.cartonStart, detail.cartonEnd));
}

function sameProductWhenPresent(detail: OrderRecordDetail, packingDetail: OrderPackingDetail) {
  return (
    sameOptional(detail.developmentNo, packingDetail.companyStyleNo) &&
    sameOptional(detail.customerStyleNo, packingDetail.customerStyleNo) &&
    sameOptional(detail.customerOrderNo, packingDetail.customerOrderNo) &&
    sameOptional(detail.poNo, packingDetail.poNo) &&
    sameOptional(detail.englishColor, packingDetail.customerColor)
  );
}

export function isMatchingPackingDetail(detail: OrderRecordDetail, packingDetail: OrderPackingDetail) {
  if (isPackingRangeInsideOrderRange(detail, packingDetail)) {
    return sameProductWhenPresent(detail, packingDetail);
  }

  if (hasCartonRange(detail)) {
    return false;
  }

  if (sameRequired(detail.cartonStart, packingDetail.cartonStart) && sameRequired(detail.cartonEnd, packingDetail.cartonEnd)) {
    return sameProductWhenPresent(detail, packingDetail);
  }

  if (sameRequired(detail.developmentNo, packingDetail.companyStyleNo)) {
    return sameProductWhenPresent(detail, packingDetail);
  }

  if (sameRequired(detail.customerStyleNo, packingDetail.customerStyleNo)) {
    if (sameRequired(detail.customerOrderNo, packingDetail.customerOrderNo)) {
      return sameOptional(detail.poNo, packingDetail.poNo) && sameOptional(detail.englishColor, packingDetail.customerColor);
    }
    if (sameRequired(detail.poNo, packingDetail.poNo)) {
      return sameOptional(detail.englishColor, packingDetail.customerColor);
    }
  }

  return false;
}

export function getMatchingPackingDetails(detail: OrderRecordDetail, packingDetails: OrderPackingDetail[]) {
  return packingDetails.filter((packingDetail) => isMatchingPackingDetail(detail, packingDetail));
}
