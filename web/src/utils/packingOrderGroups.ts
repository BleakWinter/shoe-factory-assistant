import type { OrderRecordDetail } from "../types/order";

export interface PackingSourceGroup {
  key: string;
  developmentNo: string;
  sourceDetailIds: number[];
  lastNo?: string;
  boxSpec?: string;
  quantity: number;
  cartonCount: number;
  cartonRanges: string;
}

function normalizedStyle(value?: string) {
  const clean = (value || "").replace(/[‐‑‒–—−－]/g, "-").replace(/\s*-\s*/g, "-").trim();
  const candidates = clean.match(/[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*/g) || [];
  return (candidates.find((candidate) => /[A-Za-z]/.test(candidate) && /\d/.test(candidate)) || clean).toUpperCase();
}

export function groupPackingSourceDetails(orderId: number | undefined, details: OrderRecordDetail[]): PackingSourceGroup[] {
  if (!orderId) return [];
  const groups = new Map<string, PackingSourceGroup>();
  for (const detail of details) {
    const developmentNo = normalizedStyle(detail.developmentNo);
    const key = `${orderId}:${developmentNo}`;
    const range = `${detail.cartonStart || "-"} ～ ${detail.cartonEnd || "-"}`;
    const group = groups.get(key);
    if (group) {
      group.sourceDetailIds.push(detail.id);
      group.quantity += detail.quantity || 0;
      group.cartonCount += detail.cartonCount || 0;
      group.cartonRanges += `；${range}`;
      if (!group.boxSpec) group.boxSpec = detail.boxSpec;
    } else {
      groups.set(key, { key, developmentNo, sourceDetailIds: [detail.id], lastNo: detail.lastNo,
        boxSpec: detail.boxSpec, quantity: detail.quantity || 0, cartonCount: detail.cartonCount || 0, cartonRanges: range });
    }
  }
  return [...groups.values()];
}
