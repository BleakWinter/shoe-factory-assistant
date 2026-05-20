import request from "../utils/request";
import type { OrderStatistics } from "../types/order";

export async function fetchOrderStatistics() {
  const { data } = await request.get<OrderStatistics>("/orders/statistics");
  return data;
}
