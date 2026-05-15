import request from "../utils/request";
import type { DevelopmentNoOption } from "../types/order";
import type {
  ShoePriceConfig,
  ShoePriceConfigPage,
  ShoePriceConfigQueryParams,
  ShoePriceConfigSavePayload,
} from "../types/priceConfig";

function normalizePage<T>(
  data: ShoePriceConfigPage | T[] | { records?: T[]; total?: number },
  page = 1,
  size = 20,
): ShoePriceConfigPage {
  if (Array.isArray(data)) {
    return { records: data as ShoePriceConfig[], total: data.length, page, size };
  }
  return {
    records: (data.records || []) as ShoePriceConfig[],
    total: data.total || data.records?.length || 0,
    page,
    size,
  };
}

export async function fetchShoePriceConfigs(params: ShoePriceConfigQueryParams) {
  const { data } = await request.get<
    ShoePriceConfigPage | ShoePriceConfig[] | { records?: ShoePriceConfig[]; total?: number }
  >("/price-configs", { params });

  return normalizePage<ShoePriceConfig>(data, params.page, params.size);
}

export async function fetchUnpricedDevelopmentNos() {
  const { data } = await request.get<string[]>("/price-configs/unconfigured-development-nos");
  return data || [];
}

export async function fetchShoePriceConfigDevelopmentNoOptions() {
  const { data } = await request.get<DevelopmentNoOption[]>("/price-configs/development-options");
  return data || [];
}

export async function createShoePriceConfig(payload: ShoePriceConfigSavePayload) {
  const { data } = await request.post<ShoePriceConfig>("/price-configs", payload);
  return data;
}

export async function updateShoePriceConfig(id: number, payload: ShoePriceConfigSavePayload) {
  const { data } = await request.put<ShoePriceConfig>(`/price-configs/${id}`, payload);
  return data;
}
