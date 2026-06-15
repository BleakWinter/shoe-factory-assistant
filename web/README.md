# shoe-factory-assistant-web

React + Vite + TypeScript + Ant Design 前端项目。

## 启动

```bash
npm install
npm run dev
```

默认地址：

- 本机访问：`http://localhost:5173/`
- API 代理：`http://localhost:5173/api` -> `http://localhost:8081/api`

## 构建

```bash
npm run build
```

## 页面

- `/orders`：订单主表。展示 `order_record`，点详情后查看 `order_record_detail` 和 `order_detail_process`。
- `/tasks`：打印列表。展示 `order_record`，上传 Excel 后可生成订单或装箱单 PDF 预览。

## 接口约定

- `POST /api/orders/upload`
- `GET /api/orders`
- `GET /api/orders/{id}/details`
- `GET /api/orders/{id}/pdf/{printType}`
- `GET /api/print-tasks`
- `PATCH /api/print-tasks/{id}/status`
