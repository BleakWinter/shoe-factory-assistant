import type { OrderPackingDetail } from "../types/order";

export function sumSizeQuantities(value?: Record<string, number>) {
  return Object.values(value || {})
    .map(Number)
    .filter((count) => Number.isFinite(count) && count > 0)
    .reduce((total, count) => total + count, 0);
}

export function getPackingTotalPairs(detail: Pick<OrderPackingDetail, "sizeQuantities" | "cartonCount" | "totalPairs">) {
  const totalPairs = Number(detail.totalPairs);
  if (Number.isFinite(totalPairs) && totalPairs > 0) {
    return totalPairs;
  }
  const perCartonPairs = sumSizeQuantities(detail.sizeQuantities);
  const cartonCount = Number(detail.cartonCount);
  if (perCartonPairs > 0 && Number.isFinite(cartonCount) && cartonCount > 0) {
    return perCartonPairs * cartonCount;
  }
  return detail.totalPairs;
}
