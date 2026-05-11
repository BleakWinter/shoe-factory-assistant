import {
  InboxOutlined,
  PrinterOutlined,
  ReloadOutlined,
  SearchOutlined,
} from "@ant-design/icons";
import {
  App,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  Upload,
} from "antd";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import type { UploadProps } from "antd";
import type { Dayjs } from "dayjs";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  fetchOrders,
  generatePrintPreview,
  toPreviewUrl,
  uploadOrderSource,
} from "../api/orderApi";
import { createPrintTask } from "../api/printTaskApi";
import PdfPreview from "../components/PdfPreview";
import type {
  OrderQueryParams,
  OrderRecord,
  PrintPreview,
  PrintType,
  RecognitionStatus,
} from "../types/order";
import { formatDateTime, formatEmpty } from "../utils/format";

interface FilterValues {
  orderNo?: string;
  styleNo?: string;
  customerName?: string;
  deliveryDate?: Dayjs;
  recognitionStatus?: RecognitionStatus;
}

const recognitionOptions = [
  { label: "已识别", value: "RECOGNIZED" },
  { label: "待补充", value: "PENDING_MANUAL" },
  { label: "识别失败", value: "FAILED" },
];

const printTypeOptions = [
  { label: "打印订单", value: "ORDER" },
  { label: "打印装箱单", value: "PACKING" },
];

export default function OrderWorkspacePage() {
  const { message } = App.useApp();
  const [form] = Form.useForm<FilterValues>();
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [query, setQuery] = useState<OrderQueryParams>({ page: 1, size: 20 });
  const [total, setTotal] = useState(0);
  const [selectedOrder, setSelectedOrder] = useState<OrderRecord>();
  const [printType, setPrintType] = useState<PrintType>("ORDER");
  const [preview, setPreview] = useState<PrintPreview>();
  const [previewLoading, setPreviewLoading] = useState(false);
  const [copies, setCopies] = useState(1);
  const [printerName, setPrinterName] = useState("");
  const [creatingTask, setCreatingTask] = useState(false);

  const loadOrders = useCallback(async (params: OrderQueryParams) => {
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

  const uploadProps: UploadProps = {
    accept: ".xlsx,.xls,.png,.jpg,.jpeg,.webp",
    multiple: false,
    showUploadList: false,
    customRequest: async (options) => {
      setUploading(true);
      try {
        const file = options.file as File;
        const order = await uploadOrderSource(file);
        options.onSuccess?.(order);
        message.success("订单原稿已上传");
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
      deliveryDate: values.deliveryDate?.format("YYYY-MM-DD"),
      recognitionStatus: values.recognitionStatus,
      page: 1,
      size: query.size ?? 20,
    });
  };

  const resetFilters = () => {
    form.resetFields();
    setQuery({ page: 1, size: query.size ?? 20 });
  };

  const openPrintModal = (order: OrderRecord) => {
    setSelectedOrder(order);
    setPrintType("ORDER");
    setPreview(undefined);
    setCopies(1);
    setPrinterName("");
  };

  const closePrintModal = () => {
    setSelectedOrder(undefined);
    setPreview(undefined);
  };

  const handleGeneratePreview = async () => {
    if (!selectedOrder) {
      return;
    }
    setPreviewLoading(true);
    try {
      const result = await generatePrintPreview(selectedOrder.id, printType);
      setPreview(result);
      message.success("PDF 预览已生成");
    } catch (error) {
      message.error(error instanceof Error ? error.message : "生成预览失败");
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleConfirmPrint = async () => {
    if (!preview) {
      return;
    }
    setCreatingTask(true);
    try {
      await createPrintTask({
        previewId: preview.id,
        copies,
        printerName: printerName || undefined,
      });
      message.success("打印任务已创建");
      closePrintModal();
    } catch (error) {
      message.error(error instanceof Error ? error.message : "创建打印任务失败");
    } finally {
      setCreatingTask(false);
    }
  };

  const columns = useMemo<ColumnsType<OrderRecord>>(
    () => [
      {
        title: "订单号",
        dataIndex: "orderNo",
        width: 150,
        render: formatEmpty,
      },
      {
        title: "客户",
        dataIndex: "customerName",
        width: 130,
        render: formatEmpty,
      },
      {
        title: "款号",
        dataIndex: "styleNo",
        width: 130,
        render: formatEmpty,
      },
      {
        title: "颜色",
        dataIndex: "color",
        width: 110,
        render: formatEmpty,
      },
      {
        title: "数量",
        dataIndex: "quantity",
        width: 90,
        align: "right",
        render: formatEmpty,
      },
      {
        title: "箱数",
        dataIndex: "cartonCount",
        width: 90,
        align: "right",
        render: formatEmpty,
      },
      {
        title: "交期",
        dataIndex: "deliveryDate",
        width: 120,
        render: formatEmpty,
      },
      {
        title: "识别状态",
        dataIndex: "recognitionStatus",
        width: 110,
        render: (status: RecognitionStatus, record) => (
          <Tooltip title={record.errorMessage}>
            {renderRecognitionStatus(status)}
          </Tooltip>
        ),
      },
      {
        title: "原稿",
        dataIndex: "sourceFileName",
        minWidth: 180,
        render: (value: string, record) => (
          <Space size={8}>
            <Tag color={record.sourceFileType === "EXCEL" ? "green" : "gold"}>
              {record.sourceFileType}
            </Tag>
            <Typography.Text ellipsis className="source-file-name">
              {formatEmpty(value)}
            </Typography.Text>
          </Space>
        ),
      },
      {
        title: "上传时间",
        dataIndex: "createdAt",
        width: 170,
        render: formatDateTime,
      },
      {
        title: "操作",
        key: "actions",
        fixed: "right",
        width: 110,
        render: (_, record) => {
          const disabled =
            record.sourceFileType !== "EXCEL" ||
            record.recognitionStatus === "FAILED";
          const title =
            record.sourceFileType !== "EXCEL"
              ? "图片订单暂不支持自动生成打印预览"
              : record.recognitionStatus === "FAILED"
                ? "识别失败后不能生成打印预览"
                : "";
          return (
            <Tooltip title={title}>
              <span>
                <Button
                  type="primary"
                  icon={<PrinterOutlined />}
                  disabled={disabled}
                  onClick={() => openPrintModal(record)}
                >
                  打印
                </Button>
              </span>
            </Tooltip>
          );
        },
      },
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
          <Typography.Title level={3}>订单工作台</Typography.Title>
          <Typography.Text type="secondary">
            上传订单原稿后进入订单列表，打印时再生成对应 PDF。
          </Typography.Text>
        </div>
        <Upload {...uploadProps}>
          <Button
            type="primary"
            size="large"
            loading={uploading}
            icon={<InboxOutlined />}
          >
            上传原稿
          </Button>
        </Upload>
      </div>

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
          <Form.Item name="deliveryDate" label="交期">
            <DatePicker allowClear />
          </Form.Item>
          <Form.Item name="recognitionStatus" label="状态">
            <Select
              allowClear
              placeholder="识别状态"
              options={recognitionOptions}
              className="status-select"
            />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                查询
              </Button>
              <Button onClick={resetFilters}>重置</Button>
              <Button
                icon={<ReloadOutlined />}
                onClick={() => void loadOrders(query)}
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
          dataSource={orders}
          pagination={pagination}
          scroll={{ x: 1360 }}
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
        title={selectedOrder ? `打印：${formatEmpty(selectedOrder.orderNo)}` : "打印"}
        open={Boolean(selectedOrder)}
        onCancel={closePrintModal}
        width={980}
        footer={[
          <Button key="close" onClick={closePrintModal}>
            关闭
          </Button>,
          <Button
            key="preview"
            onClick={handleGeneratePreview}
            loading={previewLoading}
            icon={<PrinterOutlined />}
          >
            生成预览
          </Button>,
          <Button
            key="print"
            type="primary"
            disabled={!preview}
            loading={creatingTask}
            onClick={handleConfirmPrint}
          >
            确认打印
          </Button>,
        ]}
      >
        <div className="print-modal-body">
          <Space wrap size={16} className="print-options">
            <Radio.Group
              optionType="button"
              buttonStyle="solid"
              value={printType}
              options={printTypeOptions}
              onChange={(event) => {
                setPrintType(event.target.value);
                setPreview(undefined);
              }}
            />
            <InputNumber
              min={1}
              max={99}
              value={copies}
              onChange={(value) => setCopies(value ?? 1)}
              addonBefore="份数"
            />
            <Input
              value={printerName}
              onChange={(event) => setPrinterName(event.target.value)}
              placeholder="打印机"
              className="printer-input"
            />
          </Space>
          <PdfPreview url={toPreviewUrl(preview?.previewUrl)} />
        </div>
      </Modal>
    </div>
  );
}

function renderRecognitionStatus(status: RecognitionStatus) {
  if (status === "RECOGNIZED") {
    return <Tag color="green">已识别</Tag>;
  }
  if (status === "PENDING_MANUAL") {
    return <Tag color="gold">待补充</Tag>;
  }
  return <Tag color="red">识别失败</Tag>;
}
