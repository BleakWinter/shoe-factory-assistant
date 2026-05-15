import type { PageResponse } from "./order";

export interface StyleConfig {
  id: number;
  developmentNo: string;
  boxSpec?: string;
  netWeightPerPair?: number;
  grossWeightPerPair?: number;
  complete?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface StyleConfigQueryParams {
  developmentNo?: string;
  incompleteOnly?: boolean;
  page?: number;
  size?: number;
}

export interface StyleConfigSavePayload {
  developmentNo?: string;
  boxSpec?: string | null;
  netWeightPerPair?: number | null;
  grossWeightPerPair?: number | null;
}

export type StyleConfigPage = PageResponse<StyleConfig>;
