import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: process.env.SHOE_API_TARGET || "http://localhost:8081",
        changeOrigin: true,
      },
    },
  },
});
