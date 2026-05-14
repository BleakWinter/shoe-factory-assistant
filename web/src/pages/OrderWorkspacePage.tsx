import { EyeOutlined, FileSearchOutlined, ReloadOutlined, SearchOutlined } from "@ant-design/icons";
import {
  App,
  Button,
  Collapse,
  Form,
  Image,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from "antd";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  fetchOrderDetails,
  fetchOrderPackingDetails,
  fetchOrders,
  recognizeOrder,
  recognizePacking,
  toAssetUrl,
} from "../api/orderApi";
import type {
  OrderDetailProcess,
  OrderPackingDetail,
  OrderRecord,
  OrderRecordDetail,
  OrderRecordQueryParams,
} from "../types/order";
import { formatDateTime, formatEmpty } from "../utils/format";

interface FilterValues {
  orderNo?: string;
  customerName?: string;
  developmentNo?: string;
  orderRecognitionStatus?: string;
  packingRecognitionStatus?: string;
}

const RECOGNIZED_STATUS = 1;
const FAILED_STATUS = 3;

const recognitionOptions = [
  { label: "待识别", value: "0" },
  { label: "已识别", value: "1" },
  { label: "待人工处理", value: "2" },
  { label: "识别失败", value: "3" },
];

function renderDevelopmentNos(values?: string[]) {
  if (!values || values.length === 0) {
    return "-";
  }
  return (
    <Space size={[4, 4]} wrap>
      {values.map((value) => (
        <Tag key={value}>{value}</Tag>
      ))}
    </Space>
  );
}

function renderRecognitionStatus(statusText?: string, errorMessage?: string) {
  if (errorMessage) {
    return <Tag color="red">{statusText || "识别失败"}</Tag>;
  }
  const color = statusText === "已识别" ? "green" : statusText === "识别失败" ? "red" : "gold";
  return <Tag color={color}>{statusText || "待识别"}</Tag>;
}

function renderPrintFlags(record: OrderRecord) {
  return (
    <Space size={[4, 4]} wrap>
      <Tag color={record.orderPrinted ? "green" : "default"}>订单</Tag>
      <Tag color={record.packingPrinted ? "green" : "default"}>装箱单</Tag>
    </Space>
  );
}

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

export default function OrderWorkspacePage() {
  const { message } = App.useApp();
  const [form] = Form.useForm<FilterValues>();
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [details, setDetails] = useState<OrderRecordDetail[]>([]);
  const [packingDetails, setPackingDetails] = useState<OrderPackingDetail[]>([]);
  const [loading, setLoading] = useState(false);
  const [orderDetailLoading, setOrderDetailLoading] = useState(false);
  const [packingDetailLoading, setPackingDetailLoading] = useState(false);
  const [activeOrder, setActiveOrder] = useState<OrderRecord | null>(null);
  const [activePanelKeys, setActivePanelKeys] = useState<string[]>([]);
  const [recognizingKey, setRecognizingKey] = useState("");
  const [query, setQuery] = useState<OrderRecordQueryParams>({ page: 1, size: 20 });
  const [total, setTotal] = useState(0);

  const loadOrders = useCallback(async (params: OrderRecordQueryParams) => {
    setLoading(true);
    try {
      const page = await fetchOrders(params);
      setOrders(page.records);
      setTotal(page.total);
    } catch (error) {
      setOrders([]);
      setTotal(0);
      message.error(error instanceof Error ? error.message : "订单列表加载失败");
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void loadOrders(query);
  }, [loadOrders, query]);

  const submitFilters = (values: FilterValues) => {
    setQuery({
      orderNo: values.orderNo,
      customerName: values.customerName,
      developmentNo: values.developmentNo,
      orderRecognitionStatus: values.orderRecognitionStatus,
      packingRecognitionStatus: values.packingRecognitionStatus,
      page: 1,
      size: query.size ?? 20,
    });
  };

  const resetFilters = () => {
    form.resetFields();
    setQuery({ page: 1, size: query.size ?? 20 });
  };

  const updateOrderInList = useCallback((next: OrderRecord) => {
    setOrders((prev) => prev.map((item) => (item.id === next.id ? { ...item, ...next } : item)));
    setActiveOrder((prev) => (prev && prev.id === next.id ? { ...prev, ...next } : prev));
  }, []);

  const runRecognition = useCallback(async (order: OrderRecord, type: "ORDER" | "PACKING") => {
    const key = `${order.id}-${type}`;
    setRecognizingKey(key);
    try {
      const next = type === "ORDER" ? await recognizeOrder(order.id) : await recognizePacking(order.id);
      updateOrderInList(next);
      if (type === "ORDER") {
        setDetails([]);
      } else {
        setPackingDetails([]);
      }
      const failed = type === "ORDER"
        ? next.orderRecognitionStatus === FAILED_STATUS
        : next.packingRecognitionStatus === FAILED_STATUS;
      const errorMessage = type === "ORDER" ? next.orderErrorMessage : next.packingErrorMessage;
      if (failed) {
        message.error(errorMessage || `${type === "ORDER" ? "订单" : "装箱单"}识别失败`);
      } else {
        message.success(`${type === "ORDER" ? "订单" : "装箱单"}识别完成`);
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : "识别失败");
    } finally {
      setRecognizingKey("");
    }
  }, [message, updateOrderInList]);

  const openDetails = useCallback((order: OrderRecord) => {
    setActiveOrder(order);
    setDetails([]);
    setPackingDetails([]);
    setActivePanelKeys([]);
  }, []);

  const loadOrderDetails = useCallback(async (order: OrderRecord) => {
    setOrderDetailLoading(true);
    try {
      setDetails(await fetchOrderDetails(order.id));
    } catch (error) {
      setDetails([]);
      message.error(error instanceof Error ? error.message : "订单明细加载失败");
    } finally {
      setOrderDetailLoading(false);
    }
  }, [message]);

  const loadPackingDetails = useCallback(async (order: OrderRecord) => {
    setPackingDetailLoading(true);
    try {
      setPackingDetails(await fetchOrderPackingDetails(order.id));
    } catch (error) {
      setPackingDetails([]);
      message.error(error instanceof Error ? error.message : "装箱单明细加载失败");
    } finally {
      setPackingDetailLoading(false);
    }
  }, [message]);

  const handleDetailPanelsChange = useCallback((keys: string | string[]) => {
    const nextKeys = Array.isArray(keys) ? keys : [keys];
    setActivePanelKeys(nextKeys);
    if (!activeOrder) {
      return;
    }
    if (nextKeys.includes("ORDER") && details.length === 0 && !orderDetailLoading) {
      void loadOrderDetails(activeOrder);
    }
    if (nextKeys.includes("PACKING") && packingDetails.length === 0 && !packingDetailLoading) {
      void loadPackingDetails(activeOrder);
    }
  }, [
    activeOrder,
    details.length,
    loadOrderDetails,
    loadPackingDetails,
    orderDetailLoading,
    packingDetailLoading,
    packingDetails.length,
  ]);

  const columns = useMemo<ColumnsType<OrderRecord>>(
    () => [
      { title: "订单流水号", dataIndex: "orderNo", width: 170, fixed: "left", render: formatEmpty },
      { title: "客户", dataIndex: "customerName", width: 140, render: formatEmpty },
      { title: "开发编号", dataIndex: "developmentNoList", minWidth: 220, render: renderDevelopmentNos },
      { title: "订单总双数", dataIndex: "totalQuantity", width: 120, align: "right", render: formatEmpty },
      { title: "总箱数", dataIndex: "totalCartonCount", width: 100, align: "right", render: formatEmpty },
      {
        title: "订单识别",
        dataIndex: "orderRecognitionStatusText",
        width: 120,
        render: (value: string, record) => renderRecognitionStatus(value, record.orderErrorMessage),
      },
      {
        title: "装箱单识别",
        dataIndex: "packingRecognitionStatusText",
        width: 130,
        render: (value: string, record) => renderRecognitionStatus(value, record.packingErrorMessage),
      },
      { title: "打印", key: "printed", width: 140, render: (_, record) => renderPrintFlags(record) },
      { title: "上传时间", dataIndex: "createdAt", width: 170, render: formatDateTime },
      {
        title: "操作",
        key: "actions",
        width: 430,
        fixed: "right",
        render: (_, record) => {
          const orderRecognized = record.orderRecognitionStatus === RECOGNIZED_STATUS;
          const packingRecognized = record.packingRecognitionStatus === RECOGNIZED_STATUS;
          return (
            <Space wrap>
              <Button
                icon={<FileSearchOutlined />}
                loading={recognizingKey === `${record.id}-ORDER`}
                disabled={Boolean(recognizingKey) && recognizingKey !== `${record.id}-ORDER`}
                onClick={() => void runRecognition(record, "ORDER")}
              >
                {orderRecognized ? "重新识别订单" : "识别订单"}
              </Button>
              <Button
                icon={<FileSearchOutlined />}
                loading={recognizingKey === `${record.id}-PACKING`}
                disabled={Boolean(recognizingKey) && recognizingKey !== `${record.id}-PACKING`}
                onClick={() => void runRecognition(record, "PACKING")}
              >
                {packingRecognized ? "重新识别装箱单" : "识别装箱单"}
              </Button>
              <Button icon={<EyeOutlined />} onClick={() => openDetails(record)}>
                查看明细
              </Button>
            </Space>
          );
        },
      },
    ],
    [openDetails, recognizingKey, runRecognition],
  );

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

  const pagination: TablePaginationConfig = {
    current: query.page,
    pageSize: query.size,
    total,
    showSizeChanger: true,
    showTotal: (count) => `共 ${count} 条`,
  };

  return (
    <div className="workspace">
      <div className="toolbar-band">
        <div>
          <Typography.Title level={3}>订单列表</Typography.Title>
          <Typography.Text type="secondary">
            上传后先进入待识别状态，按需要分别识别订单明细和装箱单明细。
          </Typography.Text>
        </div>
      </div>

      <div className="page-panel">
        <Form form={form} layout="inline" className="filter-form" onFinish={submitFilters}>
          <Form.Item name="orderNo" label="订单流水号">
            <Input allowClear placeholder="订单流水号" />
          </Form.Item>
          <Form.Item name="customerName" label="客户">
            <Input allowClear placeholder="客户" />
          </Form.Item>
          <Form.Item name="developmentNo" label="开发编号">
            <Input allowClear placeholder="开发编号" />
          </Form.Item>
          <Form.Item name="orderRecognitionStatus" label="订单识别">
            <Select allowClear className="status-select" placeholder="全部" options={recognitionOptions} />
          </Form.Item>
          <Form.Item name="packingRecognitionStatus" label="装箱单识别">
            <Select allowClear className="status-select" placeholder="全部" options={recognitionOptions} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                查询
              </Button>
              <Button onClick={resetFilters}>重置</Button>
              <Button icon={<ReloadOutlined />} onClick={() => void loadOrders(query)}>
                刷新
              </Button>
            </Space>
          </Form.Item>
        </Form>

        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={orders}
          pagination={pagination}
          scroll={{ x: 1710 }}
          className="data-table"
          onChange={(nextPagination) => {
            setQuery((prev) => ({
              ...prev,
              page: nextPagination.current ?? 1,
              size: nextPagination.pageSize ?? 20,
            }));
          }}
        />
      </div>

      <Modal
        open={Boolean(activeOrder)}
        title={activeOrder ? `订单明细：${activeOrder.orderNo || activeOrder.id}` : "订单明细"}
        onCancel={() => {
          setActiveOrder(null);
          setDetails([]);
          setPackingDetails([]);
          setActivePanelKeys([]);
        }}
        width="84vw"
        footer={null}
        className="order-detail-modal"
        destroyOnClose
      >
        <Collapse
          activeKey={activePanelKeys}
          onChange={handleDetailPanelsChange}
          items={[
            {
              key: "ORDER",
              label: "订单明细",
              children: (
                <Table
                  rowKey="id"
                  loading={orderDetailLoading}
                  columns={detailColumns}
                  dataSource={details}
                  pagination={{ pageSize: 5, showSizeChanger: false }}
                  scroll={{ x: 3090 }}
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
                  loading={packingDetailLoading}
                  columns={packingDetailColumns}
                  dataSource={packingDetails}
                  pagination={{ pageSize: 5, showSizeChanger: false }}
                  scroll={{ x: 2140 }}
                  className="data-table"
                />
              ),
            },
          ]}
        />
      </Modal>
    </div>
  );
}
