# shoe-factory-assistant-web

React + Vite + TypeScript + Ant Design 前端项目。

## 启动

```bash
npm install
npm run dev
```

默认地址：

- 本机访问：`http://localhost:5173/`
- API 代理：`http://localhost:5173/api` -> `http://localhost:8080/api`

## 构建

```bash
npm run build
```

## 页面

- `/orders`：订单表。上传 Excel 订单文件后，把文件里的每一行订单明细列出来，包含图片、款号、楦头号、材料、大底、尺码数量等。
- `/tasks`：打印列表。上传订单后按订单生成一条打印任务，只显示订单号、客户、订单里的款号、总双数和状态。

## 接口约定

- `POST /api/orders/upload`
- `GET /api/orders/lines`
- `GET /api/print-tasks`
- `PATCH /api/print-tasks/{id}/status`
