import {
  InboxOutlined,
  ReloadOutlined,
  SearchOutlined,
} from "@ant-design/icons";
import {
  Alert,
  App,
  Button,
  DatePicker,
  Form,
  Image,
  Input,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
} from "antd";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import type { UploadProps } from "antd";
import type { Dayjs } from "dayjs";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  fetchOrderLines,
  toAssetUrl,
  uploadOrderFile,
} from "../api/orderApi";
import type { OrderImportStatus, OrderLine, OrderLineQueryParams } from "../types/order";
import { formatDateTime, formatEmpty } from "../utils/format";

interface FilterValues {
  orderNo?: string;
  styleNo?: string;
  customerName?: string;
  lastNo?: string;
  deliveryDate?: Dayjs;
}

const allowedExtensions = ["xlsx", "xls"];

function isAllowedFile(file: File) {
  const extension = file.name.split(".").pop()?.toLowerCase();
  return Boolean(extension && allowedExtensions.includes(extension));
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

export default function OrderWorkspacePage() {
  const { message } = App.useApp();
  const [form] = Form.useForm<FilterValues>();
  const [lines, setLines] = useState<OrderLine[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
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
      message.error(error instanceof Error ? error.message : "订单表加载失败");
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void loadLines(query);
  }, [loadLines, query]);

  const uploadProps: UploadProps = {
    accept: ".xlsx,.xls",
    multiple: false,
    showUploadList: false,
    disabled: uploading,
    beforeUpload(file) {
      if (!isAllowedFile(file)) {
        message.error("订单文件请上传 xlsx 或 xls");
        return Upload.LIST_IGNORE;
      }
      return true;
    },
    customRequest: async (options) => {
      setUploading(true);
      try {
        const file = options.file as File;
        const result = await uploadOrderFile(file);
        options.onSuccess?.(result);
        message.success(
          `订单已导入，${result.lineCount || 0} 行明细已进入订单表，打印任务已加入打印列表`,
        );
        setQuery((prev) => ({ ...prev, page: 1 }));
      } catch (error) {
        const err = error instanceof Error ? error : new Error("上传失败");
        options.onError?.(err);
        message.error(err.message);
      } finally {
        setUploading(false);
      }
    },
  };

  const submitFilters = (values: FilterValues) => {
    setQuery({
      orderNo: values.orderNo,
      styleNo: values.styleNo,
      customerName: values.customerName,
      lastNo: values.lastNo,
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
      {
        title: "订单号",
        dataIndex: "orderNo",
        width: 130,
        fixed: "left",
        render: formatEmpty,
      },
      {
        title: "客户",
        dataIndex: "customerName",
        width: 120,
        render: formatEmpty,
      },
      {
        title: "发票编号",
        dataIndex: "invoiceNo",
        width: 110,
        render: formatEmpty,
      },
      {
        title: "款号",
        dataIndex: "styleNo",
        width: 120,
        render: formatEmpty,
      },
      {
        title: "楦头号",
        dataIndex: "lastNo",
        width: 110,
        render: formatEmpty,
      },
      {
        title: "开发编号",
        dataIndex: "developmentNo",
        width: 120,
        render: formatEmpty,
      },
      {
        title: "客人订单号",
        dataIndex: "customerOrderNo",
        width: 130,
        render: formatEmpty,
      },
      {
        title: "仓库号/店铺号",
        dataIndex: "warehouseNo",
        width: 140,
        render: formatEmpty,
      },
      {
        title: "出货时间",
        dataIndex: "deliveryDate",
        width: 120,
        render: formatEmpty,
      },
      {
        title: "PO#",
        dataIndex: "poNo",
        width: 110,
        render: formatEmpty,
      },
      {
        title: "客人型体号",
        dataIndex: "customerStyleNo",
        width: 130,
        render: formatEmpty,
      },
      {
        title: "英文颜色",
        dataIndex: "englishColor",
        width: 130,
        render: formatEmpty,
      },
      {
        title: "英文材质",
        dataIndex: "englishMaterial",
        width: 130,
        render: formatEmpty,
      },
      {
        title: "面料",
        dataIndex: "upperMaterial",
        width: 220,
        render: formatEmpty,
      },
      {
        title: "里料/垫脚",
        dataIndex: "liningMaterial",
        width: 180,
        render: formatEmpty,
      },
      {
        title: "饰扣/鞋带",
        dataIndex: "accessory",
        width: 150,
        render: formatEmpty,
      },
      {
        title: "包中底/水台",
        dataIndex: "insolePlatform",
        width: 160,
        render: formatEmpty,
      },
      {
        title: "大底",
        dataIndex: "outsole",
        width: 220,
        render: formatEmpty,
      },
      {
        title: "商标",
        dataIndex: "trademark",
        width: 100,
        render: formatEmpty,
      },
      {
        title: "双数",
        dataIndex: "quantity",
        width: 90,
        align: "right",
        render: formatEmpty,
      },
      {
        title: "尺码数量",
        dataIndex: "sizeQuantities",
        width: 220,
        render: renderSizeQuantities,
      },
      {
        title: "导入状态",
        dataIndex: "importStatus",
        width: 120,
        render: (status: OrderImportStatus, record) =>
          renderImportStatus(status, record.errorMessage),
      },
      {
        title: "上传时间",
        dataIndex: "createdAt",
        width: 170,
        render: formatDateTime,
      },
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
          <Typography.Title level={3}>订单表</Typography.Title>
          <Typography.Text type="secondary">
            一个订单文件可以拆成很多行明细，图片、款号、楦头号和材料都放在这里。
          </Typography.Text>
        </div>
        <Upload {...uploadProps}>
          <Button
            type="primary"
            size="large"
            loading={uploading}
            icon={<InboxOutlined />}
          >
            上传订单 Excel
          </Button>
        </Upload>
      </div>

      <Alert
        type="info"
        showIcon
        className="page-alert"
        message="上传订单后，订单明细进入订单表；打印列表只生成这个订单的一条打印任务。"
      />

      <div className="page-panel">
        <Form
          form={form}
          layout="inline"
          className="filter-form"
          onFinish={submitFilters}
        >
          <Form.Item name="orderNo" label="订单号">
            <Input allowClear placeholder="订单号" />
          </Form.Item>
          <Form.Item name="styleNo" label="款号">
            <Input allowClear placeholder="款号" />
          </Form.Item>
          <Form.Item name="customerName" label="客户">
            <Input allowClear placeholder="客户" />
          </Form.Item>
          <Form.Item name="lastNo" label="楦头号">
            <Input allowClear placeholder="楦头号" />
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
              <Button
                icon={<ReloadOutlined />}
                onClick={() => void loadLines(query)}
              >
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
          scroll={{ x: 3100 }}
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
