import request from "../utils/request";
import type { InnerBoxType, InnerBoxTypePayload } from "../types/innerBoxType";

export async function fetchInnerBoxTypes(enabled?: boolean) {
  const { data } = await request.get<InnerBoxType[]>("/inner-box-types", { params: { enabled } });
  return data;
}

export async function saveInnerBoxType(id: number | undefined, payload: InnerBoxTypePayload) {
  const { data } = id === undefined
    ? await request.post<InnerBoxType>("/inner-box-types", payload)
    : await request.put<InnerBoxType>(`/inner-box-types/${id}`, payload);
  return data;
}
