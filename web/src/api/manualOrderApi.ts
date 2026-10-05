import request from "../utils/request";
import type { OrderRecord } from "../types/order";
export interface ManualItem {
  id?: number; developmentNo: string; colorMaterial?: string; quantity: number; remark?: string;
}
export interface ManualOrderPayload {
  customerId: number; orderNo: string; orderDate: string; deliveryDate?: string | null; remark?: string; items: ManualItem[];
}
export interface ManualDetail {
  detail: { id: number; developmentNo: string; upperMaterial?: string; quantity: number; deliveryDate?: string; remark?: string };
  shippedQuantity: number; remainingQuantity: number;
}
export interface ManualOrderView {
  order: OrderRecord & { customerId: number; orderDate: string };
  items: ManualDetail[];
  shipments: { shipment: { id: number; taskNo: string; shippingDate: string; totalPairs: number }; items: { sourceDetailId: number }[] }[];
}
export async function fetchManualOrder(id: number) {
  const { data }=await request.get<ManualOrderView>(`/manual-orders/${id}`); return data;
}
export async function saveManualOrder(id: number | undefined, payload: ManualOrderPayload) {
  const { data }= id ? await request.put<ManualOrderView>(`/manual-orders/${id}`,payload) :
    await request.post<ManualOrderView>("/manual-orders",payload);
  return data;
}
export async function confirmManualShipment(id: number, payload: { requestId: string; shippingDate: string; detailIds: number[] }) {
  const { data }=await request.post<ManualOrderView>(`/manual-orders/${id}/shipments`,payload); return data;
}
export function localToday() {
  // 订单日期按工厂所在时区生成，避免其他时区浏览器跨天。
  const parts=new Intl.DateTimeFormat("en-CA",{timeZone:"Asia/Shanghai",year:"numeric",month:"2-digit",day:"2-digit"}).formatToParts(new Date());
  return ["year","month","day"].map(type=>parts.find(p=>p.type===type)?.value).join("-");
}
