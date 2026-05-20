import { DownOutlined, EyeOutlined, FileSearchOutlined, ReloadOutlined, SearchOutlined } from "@ant-design/icons";
import {
  App,
  Button,
  Cascader,
  Collapse,
  Dropdown,
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
import type { MenuProps } from "antd";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  fetchOrderDetails,
  fetchDevelopmentNoOptions,
  fetchOrderPackingDetails,
  fetchOrders,
  recognizeOrder,
  recognizePacking,
  toAssetUrl,
} from "../api/orderApi";
import type {
  DevelopmentNoOption,
  OrderDetailProcess,
  OrderPackingDetail,
  OrderRecord,
  OrderRecordDetail,
  OrderRecordQueryParams,
} from "../types/order";
import { formatDateTime, formatEmpty } from "../utils/format";
import { getPackingTotalPairs } from "../utils/packingTotals";

interface FilterValues {
  orderNo?: string;
  developmentNoPaths?: string[][];
  recognitionStatuses?: string[];
}

const FAILED_STATUS = 3;

const recognitionOptions = [
  { label: "待识别订单", value: "ORDER_PENDING" },
  { label: "待识别装箱单", value: "PACKING_PENDING" },
  { label: "已识别订单", value: "ORDER_RECOGNIZED" },
  { label: "已识别装箱单", value: "PACKING_RECOGNIZED" },
  { label: "识别失败", value: "FAILED" },
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

function getRecognitionStatusColor(statusText?: string, errorMessage?: string) {
  if (errorMessage || statusText === "识别失败") {
    return "red";
  }
  if (statusText === "已识别") {
    return "green";
  }
  if (statusText === "待人工处理") {
    return "orange";
  }
  return "gold";
}

function renderRecognitionFlags(record: OrderRecord) {
  const orderStatusText = record.orderRecognitionStatusText || "待识别";
  const packingStatusText = record.packingRecognitionStatusText || "待识别";
  return (
    <Space size={[4, 4]} wrap>
      <Tag
        color={getRecognitionStatusColor(orderStatusText, record.orderErrorMessage)}
        title={record.orderErrorMessage || undefined}
      >
        订单：{orderStatusText}
      </Tag>
      <Tag
        color={getRecognitionStatusColor(packingStatusText, record.packingErrorMessage)}
        title={record.packingErrorMessage || undefined}
      >
        装箱单：{packingStatusText}
      </Tag>
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
  const navigate = useNavigate();
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
  const [developmentNoOptions, setDevelopmentNoOptions] = useState<DevelopmentNoOption[]>([]);
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

  useEffect(() => {
    fetchDevelopmentNoOptions()
      .then(setDevelopmentNoOptions)
      .catch(() => setDevelopmentNoOptions([]));
  }, []);

  const submitFilters = (values: FilterValues) => {
    const developmentNos = (values.developmentNoPaths || [])
      .map((path) => path[path.length - 1])
      .filter(Boolean)
      .join(",");
    setQuery({
      orderNo: values.orderNo,
      developmentNos,
      recognitionStatus: values.recognitionStatuses?.join(","),
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
    navigate(`/orders/${order.id}/details`, { state: { order } });
  }, [navigate]);

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
        title: "识别状态",
        key: "recognitionStatus",
        width: 220,
        render: (_, record) => renderRecognitionFlags(record),
      },
      { title: "上传时间", dataIndex: "createdAt", width: 170, render: formatDateTime },
      {
        title: "操作",
        key: "actions",
        width: 240,
        fixed: "right",
        render: (_, record) => {
          const recognitionLoading =
            recognizingKey === `${record.id}-ORDER` || recognizingKey === `${record.id}-PACKING`;
          const recognitionDisabled = Boolean(recognizingKey) && !recognitionLoading;
          const recognitionMenu: MenuProps = {
            items: [
              { key: "ORDER", icon: <FileSearchOutlined />, label: "订单" },
              { key: "PACKING", icon: <FileSearchOutlined />, label: "装箱单" },
            ],
            onClick: ({ key }) => {
              void runRecognition(record, key === "PACKING" ? "PACKING" : "ORDER");
            },
          };
          return (
            <Space wrap>
              <Dropdown menu={recognitionMenu} trigger={["click"]} disabled={recognitionDisabled}>
                <Button icon={<FileSearchOutlined />} loading={recognitionLoading} disabled={recognitionDisabled}>
                  识别 <DownOutlined />
                </Button>
              </Dropdown>
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
      { title: "商标", dataIndex: "trademark", width: 120, render: formatEmpty },
      { title: "尺码数量", dataIndex: "sizeQuantities", width: 260, render: renderSizeQuantities },
      { title: "CTNS", dataIndex: "cartonCount", width: 90, align: "right", render: formatEmpty },
      { title: "TTL PRS", key: "totalPairs", width: 100, align: "right", render: (_, record) => formatEmpty(getPackingTotalPairs(record)) },
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
          <Form.Item name="developmentNoPaths" label="开发编号">
            <Cascader
              allowClear
              className="style-cascader"
              displayRender={(labels) => labels.join("-")}
              maxTagCount="responsive"
              multiple
              options={developmentNoOptions}
              placeholder="开发编号：253 / 1 / 20"
              showCheckedStrategy={Cascader.SHOW_CHILD}
              showSearch
            />
          </Form.Item>
          <Form.Item name="recognitionStatuses" label="识别状态">
            <Select
              allowClear
              className="status-select"
              maxTagCount="responsive"
              mode="multiple"
              options={recognitionOptions}
              placeholder="全部"
            />
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
          scroll={{ x: 1490 }}
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
