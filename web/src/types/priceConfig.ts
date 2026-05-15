import type { PageResponse } from "./order";

export interface ShoePriceConfig {
  id: number;
  developmentNo: string;
  upperMaterial?: string;
  shoePrice?: number;
  complete?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface ShoePriceConfigQueryParams {
  developmentNos?: string;
  incompleteOnly?: boolean;
  page?: number;
  size?: number;
}

export interface ShoePriceConfigSavePayload {
  developmentNo?: string;
  shoePrice?: number | null;
}

export type ShoePriceConfigPage = PageResponse<ShoePriceConfig>;
