export interface InnerBoxType {
  id: number;
  name: string;
  remark?: string;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface InnerBoxTypePayload {
  name: string;
  remark?: string;
  enabled: boolean;
}
