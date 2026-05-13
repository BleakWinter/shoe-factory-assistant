import axios, { AxiosError } from "axios";
import type { ApiResponse } from "../types/order";

const request = axios.create({
  // Vite 会把 /api 代理到后端 http://localhost:8080。
  baseURL: "/api",
  // Excel 转 PDF 可能比较慢，超时时间故意放宽。
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
      // 后端统一包一层 ApiResponse，这里拆掉外壳，让页面直接拿 data。
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
