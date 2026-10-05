import request from "../utils/request";
import type { OrderStatistics } from "../types/order";

export async function fetchOrderStatistics(customerId?: number) {
  const { data } = await request.get<OrderStatistics>("/orders/statistics", {params:{customerId}});
  return data;
}
