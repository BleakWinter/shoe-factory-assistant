# shoe-factory-assistant-web

React + Vite + TypeScript + Ant Design 前端项目。

## 启动

```bash
npm install
npm run dev
```

默认地址：

- 本机访问: `http://localhost:5173/`
- API 代理: `http://localhost:5173/api` -> `http://localhost:8080/api`

## 构建

```bash
npm run build
```

## 页面

- `/orders`: 订单工作台，包含原稿上传、订单过滤、打印类型选择、PDF 预览和确认打印。
- `/tasks`: 待打印任务列表。

## 接口

- `POST /api/orders/upload`
- `GET /api/orders`
- `POST /api/orders/{id}/print-previews`
- `GET /api/print-previews/{id}/preview`
- `POST /api/print-tasks`
- `GET /api/print-tasks/pending`
- `PATCH /api/print-tasks/{id}/status`
