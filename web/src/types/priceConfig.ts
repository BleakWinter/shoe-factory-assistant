import type { PageResponse } from "./order";

export interface ShoePriceConfig {
  id: number;
  customerId: number;
  customerName: string;
  developmentNo: string;
  imageUrl?: string;
  upperMaterial?: string;
  shoePrice?: number | null;
  complete?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface ShoePriceConfigQueryParams {
  customerId?: number;
  developmentNos?: string;
  incompleteOnly?: boolean;
  page?: number;
  size?: number;
}

export interface ShoePriceConfigSavePayload {
  customerId: number;
  developmentNo: string;
  shoePrice?: number | null;
}

export type ShoePriceConfigPage = PageResponse<ShoePriceConfig>;
