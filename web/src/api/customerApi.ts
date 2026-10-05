import request from "../utils/request";
export interface Customer {
  id: number; code: string; name: string; recipientName: string;
  inputMode: "EXCEL" | "MANUAL"; enabled: boolean; updatedAt?: string;
}
export type CustomerPayload = Omit<Customer,"id"|"updatedAt">;
export async function fetchCustomers() {
  const { data } = await request.get<Customer[]>("/customer-configs"); return data;
}
export async function saveCustomer(id: number | undefined, payload: CustomerPayload) {
  const { data } = id ? await request.put<Customer>(`/customer-configs/${id}`,payload) :
    await request.post<Customer>("/customer-configs",payload);
  return data;
}
