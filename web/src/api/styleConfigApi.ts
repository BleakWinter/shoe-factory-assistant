import request from "../utils/request";
import type { DevelopmentNoOption } from "../types/order";
import type {
  StyleConfig,
  StyleConfigPage,
  StyleConfigQueryParams,
  StyleConfigSavePayload,
} from "../types/styleConfig";

function normalizePage<T>(
  data: StyleConfigPage | T[] | { records?: T[]; total?: number },
  page = 1,
  size = 20,
): StyleConfigPage {
  if (Array.isArray(data)) {
    return { records: data as StyleConfig[], total: data.length, page, size };
  }
  return {
    records: (data.records || []) as StyleConfig[],
    total: data.total || data.records?.length || 0,
    page,
    size,
  };
}

export async function fetchStyleConfigs(params: StyleConfigQueryParams) {
  const { data } = await request.get<StyleConfigPage | StyleConfig[] | { records?: StyleConfig[]; total?: number }>(
    "/style-configs",
    { params },
  );

  return normalizePage<StyleConfig>(data, params.page, params.size);
}

export async function fetchUnconfiguredDevelopmentNos() {
  const { data } = await request.get<string[]>("/style-configs/unconfigured-development-nos");
  return data || [];
}

export async function fetchStyleConfigDevelopmentNoOptions() {
  const { data } = await request.get<DevelopmentNoOption[]>("/style-configs/development-options");
  return data || [];
}

export async function createStyleConfig(payload: StyleConfigSavePayload) {
  const { data } = await request.post<StyleConfig>("/style-configs", payload);
  return data;
}

export async function updateStyleConfig(id: number, payload: StyleConfigSavePayload) {
  const { data } = await request.put<StyleConfig>(`/style-configs/${id}`, payload);
  return data;
}
