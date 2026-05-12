import { ReloadOutlined, SearchOutlined } from "@ant-design/icons";
import {
  App,
  Button,
  DatePicker,
  Form,
  Image,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from "antd";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import type { Dayjs } from "dayjs";
import { useCallback, useEffect, useMemo, useState } from "react";
import { fetchOrderLines, toAssetUrl } from "../api/orderApi";
import type { OrderImportStatus, OrderLine, OrderLineQueryParams, ShipmentStatus } from "../types/order";
import { formatDateTime, formatEmpty } from "../utils/format";

interface FilterValues {
  orderNo?: string;
  styleNo?: string;
  lastNo?: string;
  shipmentStatus?: ShipmentStatus;
  deliveryDate?: Dayjs;
}

interface StyleOption {
  label: string;
  value: string;
}

function renderImportStatus(status?: OrderImportStatus, errorMessage?: string) {
  if (status === "FAILED") {
    return <Tag color="red">{errorMessage || "导入失败"}</Tag>;
  }
  if (status === "PARTIAL") {
    return <Tag color="gold">部分识别</Tag>;
  }
  return <Tag color="green">已导入</Tag>;
}

function renderSizeQuantities(value?: Record<string, number>) {
  const entries = Object.entries(value || {}).filter(([, count]) => Number(count) > 0);
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

function styleGroupOf(styleNo?: string) {
  if (!styleNo) {
    return "";
  }
  const parts = styleNo.trim().split("-").filter(Boolean);
  return parts.length >= 2 ? `${parts[0]}-${parts[1]}` : styleNo.trim();
}

function buildStyleOptions(lines: OrderLine[]): StyleOption[] {
  const styles = new Set<string>();
  lines.forEach((line) => {
    const fullStyle = line.developmentNo?.trim();
    if (!fullStyle) {
      return;
    }
    styles.add(fullStyle);
  });

  return Array.from(styles)
    .sort((a, b) => a.localeCompare(b))
    .map((value) => ({
      label: `${styleGroupOf(value)} / ${value}`,
      value,
    }));
}

export default function OrderWorkspacePage() {
  const { message } = App.useApp();
  const [form] = Form.useForm<FilterValues>();
  const [lines, setLines] = useState<OrderLine[]>([]);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState<OrderLineQueryParams>({ page: 1, size: 20 });
  const [total, setTotal] = useState(0);

  const loadLines = useCallback(async (params: OrderLineQueryParams) => {
    setLoading(true);
    try {
      const page = await fetchOrderLines(params);
      setLines(page.records);
      setTotal(page.total);
    } catch (error) {
      setLines([]);
      setTotal(0);
      message.error(error instanceof Error ? error.message : "订单明细加载失败");
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void loadLines(query);
  }, [loadLines, query]);

  const styleOptions = useMemo(() => buildStyleOptions(lines), [lines]);

  const submitFilters = (values: FilterValues) => {
    setQuery({
      orderNo: values.orderNo,
      styleNo: values.styleNo,
      lastNo: values.lastNo,
      shipmentStatus: values.shipmentStatus,
      deliveryDate: values.deliveryDate?.format("YYYY-MM-DD"),
      page: 1,
      size: query.size ?? 20,
    });
  };

  const resetFilters = () => {
    form.resetFields();
    setQuery({ page: 1, size: query.size ?? 20 });
  };

  const columns = useMemo<ColumnsType<OrderLine>>(
    () => [
      {
        title: "订单流水号",
        dataIndex: "orderNo",
        width: 130,
        fixed: "left",
        render: formatEmpty,
      },
      {
        title: "图片",
        dataIndex: "imageUrl",
        width: 86,
        fixed: "left",
        render: (value: string) =>
          value ? (
            <Image
              src={toAssetUrl(value)}
              width={58}
              height={44}
              className="order-image"
              preview={{ mask: "查看" }}
            />
          ) : (
            <Tag>无图</Tag>
          ),
      },
      { title: "楦头", dataIndex: "lastNo", width: 110, fixed: "left", render: formatEmpty },
      { title: "开发编号", dataIndex: "developmentNo", width: 130, render: formatEmpty },
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
      {
        title: "尺码数量",
        dataIndex: "sizeQuantities",
        width: 240,
        render: renderSizeQuantities,
      },
      { title: "双数", dataIndex: "quantity", width: 90, align: "right", render: formatEmpty },
      { title: "箱数", dataIndex: "cartonCount", width: 90, align: "right", render: formatEmpty },
      { title: "总数量", dataIndex: "totalQuantity", width: 100, align: "right", render: formatEmpty },
      {
        title: "导入状态",
        dataIndex: "importStatus",
        width: 120,
        render: (status: OrderImportStatus, record) =>
          renderImportStatus(status, record.errorMessage),
      },
      { title: "上传时间", dataIndex: "createdAt", width: 170, render: formatDateTime },
    ],
    [],
  );

  const pagination: TablePaginationConfig = {
    current: query.page,
    pageSize: query.size,
    total,
    showSizeChanger: true,
    showTotal: (count) => `共 ${count} 行`,
  };

  return (
    <div className="workspace">
      <div className="toolbar-band">
        <div>
          <Typography.Title level={3}>订单列表</Typography.Title>
          <Typography.Text type="secondary">
            这里看 Excel 里解析出来的订单明细，上传入口放在打印列表里。
          </Typography.Text>
        </div>
      </div>

      <div className="page-panel">
        <Form
          form={form}
          layout="inline"
          className="filter-form"
          onFinish={submitFilters}
        >
          <Form.Item name="orderNo" label="订单流水号">
            <Input allowClear placeholder="订单流水号" />
          </Form.Item>
          <Form.Item name="styleNo" label="款号">
            <Select
              allowClear
              className="style-cascader"
              options={styleOptions}
              placeholder="选择款号"
              showSearch
              optionFilterProp="label"
            />
          </Form.Item>
          <Form.Item name="lastNo" label="楦头">
            <Input allowClear placeholder="楦头" />
          </Form.Item>
          <Form.Item name="shipmentStatus" label="出货状态">
            <Select
              allowClear
              className="status-select"
              placeholder="全部"
              options={[
                { label: "未出货", value: "NOT_SHIPPED" },
                { label: "已出货", value: "SHIPPED" },
              ]}
            />
          </Form.Item>
          <Form.Item name="deliveryDate" label="出货时间">
            <DatePicker allowClear />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                查询
              </Button>
              <Button onClick={resetFilters}>重置</Button>
              <Button icon={<ReloadOutlined />} onClick={() => void loadLines(query)}>
                刷新
              </Button>
            </Space>
          </Form.Item>
        </Form>

        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={lines}
          pagination={pagination}
          scroll={{ x: 3450 }}
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
    </div>
  );
}
