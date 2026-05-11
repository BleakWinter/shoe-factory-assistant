import axios, { AxiosError } from "axios";
import type { ApiResponse } from "../types/order";

const request = axios.create({
  baseURL: "/api",
  timeout: 180_000,
});

request.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown> | unknown;
    if (body && typeof body === "object" && "success" in body) {
      const apiBody = body as ApiResponse<unknown>;
      if (!apiBody.success) {
        return Promise.reject(new Error(apiBody.message || "请求失败"));
      }
      response.data = apiBody.data;
    }
    return response;
  },
  (error: AxiosError<{ message?: string }>) => {
    const errorMessage =
      error.response?.data?.message || error.message || "网络请求失败";
    return Promise.reject(new Error(errorMessage));
  },
);

export default request;
