# data-service

鞋厂数据处理服务。首版用 FastAPI 接收 Java 整理的货期明细，用 Pillow 绘制表格 PNG。

## 本地运行

在本目录使用 Python 3.12 或以上版本。

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -e '.[test]'
python -m uvicorn shoe_factory_data.main:app --host 127.0.0.1 --port 8000
```

默认使用容器内的 Noto Sans CJK SC 或 macOS 的 STHeiti。可通过 `DATA_SERVICE_FONT_PATH` 指定支持中文的字体文件，`DATA_SERVICE_FONT_INDEX` 指定字体集合内的索引。指定路径时默认索引为 `0`。

## Docker

在本目录构建镜像。以下命令适用于本机调试，`shoe-factory-data` 是已连接到 Java 后端的自定义内网。

```bash
docker build -t shoe-factory-data-service .
docker run -d --name data-service --restart unless-stopped \
  --network bridge -p 127.0.0.1:8000:8000 shoe-factory-data-service
docker network connect shoe-factory-data data-service
```

本机可打开 `http://127.0.0.1:8000/docs` 调试，Java 通过内网调用 `http://data-service:8000/reports/delivery/render`。正式部署仅需容器互通时，创建容器时使用 `--network shoe-factory-data` 即可。

## 货期表格

```bash
curl --fail-with-body http://127.0.0.1:8000/reports/delivery/render \
  -H 'Content-Type: application/json' \
  --data '{
    "reportDate": "2026-11-13",
    "orderGroups": [
      {
        "orderNo": "E2682",
        "deliveryDate": "2026-11-09",
        "overdueDays": 4,
        "styles": [{"styleNo": "JCD-409-5-01", "pendingPairs": 16}]
      }
    ]
  }' \
  --output delivery.png
```

- Java 按订单和交期分组，组内传入已汇总的款号及待出货双数。服务按请求顺序排版，组内合并订单号和交期。逾期组以 `overdueDays` 标红。Java 可直接将 `200 image/png` 响应作为邮件附件。
- `deliveryDate` 可以是 `null`，图片显示「交期待确认」。`overdueDays` 省略时为 `0`。传入空的 `orderGroups` 时，图片显示「暂无预警订单」。
- 字段无效或图片高度超过 8192 像素时，返回 `422` JSON。单张图片绘制任务串行执行。

## 测试

```bash
python -m unittest discover -s tests -v
```
