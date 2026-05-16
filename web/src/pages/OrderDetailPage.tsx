import { ArrowLeftOutlined } from "@ant-design/icons";
import { App, Button, Image, Space, Table, Tabs, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { fetchOrderDetails, fetchOrderPackingDetails, toAssetUrl } from "../api/orderApi";
import type { OrderDetailProcess, OrderPackingDetail, OrderRecord, OrderRecordDetail } from "../types/order";
import { formatDateTime, formatEmpty } from "../utils/format";

function renderSizeQuantities(value?: Record<string, number>) {
  const entries = Object.entries(value || {})
    .filter(([, count]) => Number(count) > 0)
    .sort(([left], [right]) => compareSizes(left, right));
  if (entries.length === 0) {
    return "-";
  }

  return (
    <div className="size-grid">
      {entries.map(([size, count]) => (
        <span key={size}>
          {size}: {count}
        </span>
      ))}
    </div>
  );
}

function compareSizes(left: string, right: string) {
  const leftValue = parseSizeValue(left);
  const rightValue = parseSizeValue(right);
  if (leftValue !== null && rightValue !== null && leftValue !== rightValue) {
    return leftValue - rightValue;
  }
  return left.localeCompare(right, "zh-CN", { numeric: true });
}

function parseSizeValue(value: string) {
  const normalized = value.trim().replace("陆", ".5");
  const match = normalized.match(/\d+(?:\.\d+)?/);
  return match ? Number(match[0]) : null;
}

function renderProcesses(processes?: OrderDetailProcess[]) {
  if (!processes || processes.length === 0) {
    return <Typography.Text type="secondary">暂无处理记录</Typography.Text>;
  }
  return (
    <Space size={[6, 6]} wrap>
      {processes.map((process) => (
        <Tag key={process.id} color="blue">
          {process.processTypeText || process.processType}: {process.processStatusText || "已处理"}
          {process.processCount ? ` x${process.processCount}` : ""}
        </Tag>
      ))}
    </Space>
  );
}

export default function OrderDetailPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const location = useLocation();
  const { id } = useParams();
  const orderId = Number(id);
  const routeState = location.state as { order?: OrderRecord } | null;
  const order = routeState?.order || null;
  const [details, setDetails] = useState<OrderRecordDetail[]>([]);
  const [packingDetails, setPackingDetails] = useState<OrderPackingDetail[]>([]);
  const [loading, setLoading] = useState(false);

  const loadDetailPage = useCallback(async () => {
    if (!Number.isFinite(orderId)) {
      message.error("订单 ID 不正确");
      return;
    }
    setLoading(true);
    try {
      const [nextDetails, nextPackingDetails] = await Promise.all([
        fetchOrderDetails(orderId),
        fetchOrderPackingDetails(orderId),
      ]);
      setDetails(nextDetails);
      setPackingDetails(nextPackingDetails);
    } catch (error) {
      setDetails([]);
      setPackingDetails([]);
      message.error(error instanceof Error ? error.message : "订单明细加载失败");
    } finally {
      setLoading(false);
    }
  }, [message, orderId]);

  useEffect(() => {
    void loadDetailPage();
  }, [loadDetailPage]);

  const detailColumns = useMemo<ColumnsType<OrderRecordDetail>>(
    () => [
      {
        title: "图片",
        dataIndex: "imageUrl",
        width: 86,
        fixed: "left",
        render: (value: string) =>
          value ? (
            <Image src={toAssetUrl(value)} width={58} height={44} className="order-image" preview={{ mask: "查看" }} />
          ) : (
            <Tag>无图</Tag>
          ),
      },
      { title: "开发编号", dataIndex: "developmentNo", width: 150, fixed: "left", render: formatEmpty },
      { title: "楦头", dataIndex: "lastNo", width: 110, render: formatEmpty },
      { title: "客人", dataIndex: "customerName", width: 150, render: formatEmpty },
      { title: "客人订单号", dataIndex: "customerOrderNo", width: 170, render: formatEmpty },
      { title: "出货时间", dataIndex: "deliveryDate", width: 120, render: formatEmpty },
      { title: "PO", dataIndex: "poNo", width: 110, render: formatEmpty },
      { title: "客人型体号", dataIndex: "customerStyleNo", width: 130, render: formatEmpty },
      { title: "英文颜色", dataIndex: "englishColor", width: 160, render: formatEmpty },
      { title: "英文材质", dataIndex: "englishMaterial", width: 130, render: formatEmpty },
      { title: "面料", dataIndex: "upperMaterial", width: 260, render: formatEmpty },
      { title: "里料/垫脚", dataIndex: "liningMaterial", width: 220, render: formatEmpty },
      { title: "饰扣/鞋带", dataIndex: "accessory", width: 140, render: formatEmpty },
      { title: "中底/包中底", dataIndex: "insolePlatform", width: 150, render: formatEmpty },
      { title: "大底", dataIndex: "outsole", width: 260, render: formatEmpty },
      { title: "商标", dataIndex: "trademark", width: 150, render: formatEmpty },
      { title: "尺码数量", dataIndex: "sizeQuantities", width: 240, render: renderSizeQuantities },
      { title: "双数", dataIndex: "quantity", width: 90, align: "right", render: formatEmpty },
      { title: "箱数", dataIndex: "cartonCount", width: 90, align: "right", render: formatEmpty },
      { title: "开始箱号", dataIndex: "cartonStart", width: 120, render: formatEmpty },
      { title: "结束箱号", dataIndex: "cartonEnd", width: 120, render: formatEmpty },
      { title: "盒规", dataIndex: "boxSpec", width: 120, render: formatEmpty },
    ],
    [],
  );

  const packingDetailColumns = useMemo<ColumnsType<OrderPackingDetail>>(
    () => [
      {
        title: "图片",
        dataIndex: "imageUrl",
        width: 86,
        fixed: "left",
        render: (value: string) =>
          value ? (
            <Image src={toAssetUrl(value)} width={58} height={44} className="order-image" preview={{ mask: "查看" }} />
          ) : (
            <Tag>无图</Tag>
          ),
      },
      { title: "公司款号", dataIndex: "companyStyleNo", width: 150, fixed: "left", render: formatEmpty },
      { title: "客人", dataIndex: "customerName", width: 130, render: formatEmpty },
      { title: "客人订单号", dataIndex: "customerOrderNo", width: 170, render: formatEmpty },
      { title: "仓库号/店铺号", dataIndex: "warehouseStoreNo", width: 150, render: formatEmpty },
      { title: "PO", dataIndex: "poNo", width: 120, render: formatEmpty },
      { title: "客人款号", dataIndex: "customerStyleNo", width: 140, render: formatEmpty },
      { title: "客人颜色", dataIndex: "customerColor", width: 170, render: formatEmpty },
      { title: "面料材质", dataIndex: "material", width: 150, render: formatEmpty },
      { title: "项目编号", dataIndex: "itemNumber", width: 130, render: formatEmpty },
      { title: "商标", dataIndex: "trademark", width: 120, render: formatEmpty },
      { title: "尺码数量", dataIndex: "sizeQuantities", width: 260, render: renderSizeQuantities },
      { title: "CTNS", dataIndex: "cartonCount", width: 90, align: "right", render: formatEmpty },
      { title: "TTL PRS", dataIndex: "totalPairs", width: 100, align: "right", render: formatEmpty },
      { title: "开始箱号", dataIndex: "cartonStart", width: 120, render: formatEmpty },
      { title: "结束箱号", dataIndex: "cartonEnd", width: 120, render: formatEmpty },
    ],
    [],
  );

  return (
    <div className="workspace">
      <div className="toolbar-band">
        <div>
          <Typography.Title level={3}>订单明细</Typography.Title>
          <Typography.Text type="secondary">
            {order ? `${order.orderNo || order.id} / ${order.customerName || "未填写客户"} / ${formatDateTime(order.createdAt)}` : `订单 ID：${orderId}`}
          </Typography.Text>
        </div>
        <Space wrap>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate("/orders")}>
            返回订单列表
          </Button>
          <Button onClick={() => void loadDetailPage()}>
            刷新
          </Button>
        </Space>
      </div>

      <div className="page-panel">
        <Tabs
          items={[
            {
              key: "ORDER",
              label: "订单明细",
              children: (
                <Table
                  rowKey="id"
                  loading={loading}
                  columns={detailColumns}
                  dataSource={details}
                  pagination={{ pageSize: 20, showSizeChanger: true }}
                  scroll={{ x: 3330 }}
                  className="data-table"
                  expandable={{
                    expandedRowRender: (record) => renderProcesses(record.processes),
                    rowExpandable: () => true,
                  }}
                />
              ),
            },
            {
              key: "PACKING",
              label: "装箱单明细",
              children: (
                <Table
                  rowKey="id"
                  loading={loading}
                  columns={packingDetailColumns}
                  dataSource={packingDetails}
                  pagination={{ pageSize: 20, showSizeChanger: true }}
                  scroll={{ x: 2140 }}
                  className="data-table"
                />
              ),
            },
          ]}
        />
      </div>
    </div>
  );
}
