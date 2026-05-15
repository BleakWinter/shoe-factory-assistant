import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  PrinterOutlined,
  ReloadOutlined,
} from "@ant-design/icons";
import { App, Button, Select, Space, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { Key } from "react";
import { fetchOrderDetails, fetchOrders } from "../api/orderApi";
import type { OrderRecord, OrderRecordDetail } from "../types/order";
import { formatEmpty } from "../utils/format";

interface PrintSelectionPageProps {
  title: string;
}

function renderSizeQuantities(value?: Record<string, number>) {
  const entries = Object.entries(value || {})
    .filter(([, count]) => Number(count) > 0)
    .sort(([left], [right]) => left.localeCompare(right, "zh-CN", { numeric: true }));
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

function buildOrderLabel(order: OrderRecord) {
  const orderNo = order.orderNo || `订单 ${order.id}`;
  const customer = order.customerName ? ` / ${order.customerName}` : "";
  return `${orderNo}${customer}`;
}

export default function PrintSelectionPage({ title }: PrintSelectionPageProps) {
  const { message } = App.useApp();
  const printTargetTitle = title.replace(/^打印/, "");
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [selectedOrderId, setSelectedOrderId] = useState<number>();
  const [details, setDetails] = useState<OrderRecordDetail[]>([]);
  const [selectedDetailIds, setSelectedDetailIds] = useState<Key[]>([]);
  const [selectedPrintIds, setSelectedPrintIds] = useState<Key[]>([]);
  const [printItems, setPrintItems] = useState<OrderRecordDetail[]>([]);
  const [orderLoading, setOrderLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);

  const loadOrders = useCallback(async () => {
    setOrderLoading(true);
    try {
      const page = await fetchOrders({ page: 1, size: 100 });
      setOrders(page.records);
      setSelectedOrderId((current) => current || page.records[0]?.id);
    } catch (error) {
      setOrders([]);
      message.error(error instanceof Error ? error.message : "订单加载失败");
    } finally {
      setOrderLoading(false);
    }
  }, [message]);

  const loadDetails = useCallback(async () => {
    if (!selectedOrderId) {
      setDetails([]);
      return;
    }
    setDetailLoading(true);
    try {
      const data = await fetchOrderDetails(selectedOrderId);
      setDetails(data);
      setSelectedDetailIds([]);
      setSelectedPrintIds([]);
      setPrintItems([]);
    } catch (error) {
      setDetails([]);
      message.error(error instanceof Error ? error.message : "订单明细加载失败");
    } finally {
      setDetailLoading(false);
    }
  }, [message, selectedOrderId]);

  useEffect(() => {
    void loadOrders();
  }, [loadOrders]);

  useEffect(() => {
    void loadDetails();
  }, [loadDetails]);

  const availableDetails = useMemo(() => {
    const printIdSet = new Set(printItems.map((item) => item.id));
    return details.filter((item) => !printIdSet.has(item.id));
  }, [details, printItems]);

  const columns = useMemo<ColumnsType<OrderRecordDetail>>(
    () => [
      { title: "开发编号", dataIndex: "developmentNo", width: 140, render: formatEmpty },
      { title: "客人订单号", dataIndex: "customerOrderNo", width: 150, render: formatEmpty },
      { title: "客人型体号", dataIndex: "customerStyleNo", width: 130, render: formatEmpty },
      { title: "英文颜色", dataIndex: "englishColor", width: 140, render: formatEmpty },
      { title: "尺码数量", dataIndex: "sizeQuantities", width: 220, render: renderSizeQuantities },
      { title: "双数", dataIndex: "quantity", width: 80, align: "right", render: formatEmpty },
      { title: "箱数", dataIndex: "cartonCount", width: 80, align: "right", render: formatEmpty },
    ],
    [],
  );

  const addPrintItems = () => {
    const selectedSet = new Set(selectedDetailIds);
    setPrintItems((current) => [
      ...current,
      ...availableDetails.filter((item) => selectedSet.has(item.id)),
    ]);
    setSelectedDetailIds([]);
  };

  const removePrintItems = () => {
    const selectedSet = new Set(selectedPrintIds);
    setPrintItems((current) => current.filter((item) => !selectedSet.has(item.id)));
    setSelectedPrintIds([]);
  };

  return (
    <div className="workspace">
      <div className="toolbar-band">
        <div>
          <Typography.Title level={3}>{title}</Typography.Title>
          <Typography.Text type="secondary">
            从左侧订单明细中选择要打印的数据，移动到右侧待打印列表。
          </Typography.Text>
        </div>
        <Space wrap>
          <Select
            className="print-order-select"
            placeholder="选择订单"
            loading={orderLoading}
            value={selectedOrderId}
            options={orders.map((order) => ({ value: order.id, label: buildOrderLabel(order) }))}
            onChange={(value) => setSelectedOrderId(value)}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void loadDetails()}>
            刷新明细
          </Button>
          <Button type="primary" disabled={!printItems.length} icon={<PrinterOutlined />}>
            打印预览
          </Button>
        </Space>
      </div>

      <div className="print-transfer-layout">
        <div className="page-panel print-transfer-panel">
          <div className="print-transfer-heading">
            <Typography.Title level={4}>订单明细表数据</Typography.Title>
            <Typography.Text type="secondary">{availableDetails.length} 条可选</Typography.Text>
          </div>
          <Table
            rowKey="id"
            loading={detailLoading}
            columns={columns}
            dataSource={availableDetails}
            pagination={{ pageSize: 10, showSizeChanger: false }}
            scroll={{ x: 940 }}
            className="data-table"
            rowSelection={{
              selectedRowKeys: selectedDetailIds,
              onChange: setSelectedDetailIds,
            }}
          />
        </div>

        <div className="print-transfer-actions">
          <Button
            type="primary"
            icon={<ArrowRightOutlined />}
            disabled={!selectedDetailIds.length}
            onClick={addPrintItems}
          />
          <Button
            icon={<ArrowLeftOutlined />}
            disabled={!selectedPrintIds.length}
            onClick={removePrintItems}
          />
        </div>

        <div className="page-panel print-transfer-panel">
          <div className="print-transfer-heading">
            <Typography.Title level={4}>待打印{printTargetTitle}</Typography.Title>
            <Typography.Text type="secondary">{printItems.length} 条待打印</Typography.Text>
          </div>
          <Table
            rowKey="id"
            columns={columns}
            dataSource={printItems}
            pagination={{ pageSize: 10, showSizeChanger: false }}
            scroll={{ x: 940 }}
            className="data-table"
            rowSelection={{
              selectedRowKeys: selectedPrintIds,
              onChange: setSelectedPrintIds,
            }}
          />
        </div>
      </div>
    </div>
  );
}
