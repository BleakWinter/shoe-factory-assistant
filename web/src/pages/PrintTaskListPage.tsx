import {
  CheckCircleOutlined,
  InboxOutlined,
  PrinterOutlined,
  ReloadOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import { App, Button, Modal, Select, Space, Table, Tag, Typography, Upload } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { UploadProps } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import { reuploadOrderFile, uploadOrderFile } from "../api/orderApi";
import {
  fetchPrintTasks,
  generatePrintTaskPreview,
  markPrintTaskPrinted,
  regeneratePrintTaskPreview,
} from "../api/printTaskApi";
import PdfPreview from "../components/PdfPreview";
import { PRINT_TYPES } from "../types/order";
import type { PrintTask, PrintTaskStatus, PrintType } from "../types/order";
import { formatDateTime, formatEmpty } from "../utils/format";

const allowedExtensions = ["xlsx", "xls"];

function isAllowedFile(file: File) {
  // 前端先做一次扩展名校验，后端仍会再校验一遍。
  const extension = file.name.split(".").pop()?.toLowerCase();
  return Boolean(extension && allowedExtensions.includes(extension));
}

function renderTaskStatus(status: PrintTaskStatus) {
  // 后端存英文枚举，页面转成爸妈看得懂的中文状态。
  const map: Record<PrintTaskStatus, { color: string; label: string }> = {
    PENDING: { color: "gold", label: "待打印" },
    PRINTED: { color: "green", label: "已打印" },
    FAILED: { color: "red", label: "失败" },
    INVALID: { color: "default", label: "已失效" },
  };
  const item = map[status] || { color: "default", label: status };
  return <Tag color={item.color}>{item.label}</Tag>;
}

interface PrintTaskRow {
  key: string;
  orderId?: number;
  orderNo?: string;
  customerName?: string;
  originalFileName?: string;
  styleNos?: string[];
  totalPairs?: number;
  createdAt?: string;
  orderTask?: PrintTask;
  packingTask?: PrintTask;
}

function renderStyleNos(styleNos?: string[]) {
  // 一个订单可能有多个开发编号，用标签分开显示。
  if (!styleNos || styleNos.length === 0) {
    return "-";
  }
  return (
    <Space size={[4, 4]} wrap>
      {styleNos.map((styleNo) => (
        <Tag key={styleNo}>{styleNo}</Tag>
      ))}
    </Space>
  );
}

function taskForPrintType(row: PrintTaskRow | null, printType: PrintType) {
  if (!row) {
    return undefined;
  }
  return printType === PRINT_TYPES.ORDER ? row.orderTask : row.packingTask;
}

function mergeTaskIntoRow(row: PrintTaskRow, task: PrintTask): PrintTaskRow {
  const nextRow = {
    ...row,
    orderId: row.orderId ?? task.orderId,
    orderNo: row.orderNo || task.orderNo,
    customerName: row.customerName || task.customerName,
    originalFileName: row.originalFileName || task.originalFileName,
    styleNos: row.styleNos && row.styleNos.length > 0 ? row.styleNos : task.styleNos,
    totalPairs: row.totalPairs ?? task.totalPairs,
    createdAt: row.createdAt || task.createdAt,
  };

  if (task.printType === PRINT_TYPES.ORDER) {
    return { ...nextRow, orderTask: task };
  }
  if (task.printType === PRINT_TYPES.PACKING) {
    return { ...nextRow, packingTask: task };
  }
  return nextRow;
}

function buildPrintTaskRows(tasks: PrintTask[]) {
  const groups = new Map<string, PrintTaskRow>();
  tasks.forEach((task) => {
    const key = String(task.orderId ?? task.orderNo ?? task.id);
    const current = groups.get(key) || { key };
    groups.set(key, mergeTaskIntoRow(current, task));
  });
  return Array.from(groups.values());
}

function renderPrintFlags(row: PrintTaskRow) {
  const renderFlag = (label: string, task?: PrintTask) => {
    const color =
      task?.status === "PRINTED" ? "green" : task?.status === "FAILED" ? "red" : "default";
    return <Tag color={color}>{label}</Tag>;
  };

  return (
    <Space size={[4, 4]} wrap>
      {renderFlag("订单", row.orderTask)}
      {renderFlag("装箱单", row.packingTask)}
    </Space>
  );
}

function getRowStatus(row: PrintTaskRow): PrintTaskStatus {
  const rowTasks = [row.orderTask, row.packingTask].filter(Boolean) as PrintTask[];
  if (rowTasks.some((task) => task.status === "FAILED")) {
    return "FAILED";
  }
  if (rowTasks.length > 0 && rowTasks.every((task) => task.status === "PRINTED")) {
    return "PRINTED";
  }
  return "PENDING";
}

export default function PrintTaskListPage() {
  const { message } = App.useApp();
  // tasks 保存后端的订单/装箱单任务；页面再合并成一行，恢复以前的操作习惯。
  const [tasks, setTasks] = useState<PrintTask[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [reuploadingOrderId, setReuploadingOrderId] = useState<number | null>(null);
  const [orderNoFilter, setOrderNoFilter] = useState<string>();
  const [activeRow, setActiveRow] = useState<PrintTaskRow | null>(null);
  const [activeTask, setActiveTask] = useState<PrintTask | null>(null);
  const [activePrintType, setActivePrintType] = useState<PrintType | "">("");
  const [previewUrl, setPreviewUrl] = useState("");
  const [previewLoading, setPreviewLoading] = useState<PrintType | "">("");
  const [regenerating, setRegenerating] = useState(false);
  const [markingPrinted, setMarkingPrinted] = useState<PrintType | "">("");

  const loadTasks = useCallback(async () => {
    // 刷新打印任务列表。上传成功后也会调用一次。
    setLoading(true);
    try {
      const data = await fetchPrintTasks();
      setTasks(data);
    } catch (error) {
      setTasks([]);
      message.error(error instanceof Error ? error.message : "打印订单加载失败");
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void loadTasks();
  }, [loadTasks]);

  const uploadProps: UploadProps = {
    accept: ".xlsx,.xls",
    multiple: false,
    showUploadList: false,
    disabled: uploading || reuploadingOrderId !== null,
    beforeUpload(file) {
      if (!isAllowedFile(file)) {
        message.error("请上传 xlsx 或 xls 订单文件");
        return Upload.LIST_IGNORE;
      }
      return true;
    },
    customRequest: async (options) => {
      // Ant Design Upload 默认会自己发请求，这里改成走我们的 uploadOrderFile。
      setUploading(true);
      try {
        const file = options.file as File;
        const result = await uploadOrderFile(file);
        options.onSuccess?.(result);
        message.success(`订单已上传，打印任务 ${result.printTaskNo || ""} 已创建，明细待手动识别`);
        await loadTasks();
      } catch (error) {
        const err = error instanceof Error ? error : new Error("上传失败");
        options.onError?.(err);
        message.error(err.message);
      } finally {
        setUploading(false);
      }
    },
  };

  const buildReuploadProps = useCallback(
    (row: PrintTaskRow): UploadProps => ({
      accept: ".xlsx,.xls",
      multiple: false,
      showUploadList: false,
      disabled: uploading || reuploadingOrderId !== null,
      beforeUpload(file) {
        if (!isAllowedFile(file)) {
          message.error("请上传 xlsx 或 xls 订单文件");
          return Upload.LIST_IGNORE;
        }
        return true;
      },
      customRequest: async (options) => {
        if (!row.orderId) {
          const err = new Error("订单 ID 为空，不能重新上传");
          options.onError?.(err);
          message.error(err.message);
          return;
        }
        setReuploadingOrderId(row.orderId);
        try {
          const file = options.file as File;
          const result = await reuploadOrderFile(row.orderId, file);
          options.onSuccess?.(result);
          message.success(`订单 ${result.printTaskNo || result.orderNo || ""} 已重新上传，原文件已替换`);
          await loadTasks();
        } catch (error) {
          const err = error instanceof Error ? error : new Error("重新上传失败");
          options.onError?.(err);
          message.error(err.message);
        } finally {
          setReuploadingOrderId(null);
        }
      },
    }),
    [loadTasks, message, reuploadingOrderId, uploading],
  );

  const rows = useMemo(() => buildPrintTaskRows(tasks), [tasks]);

  const orderNoOptions = useMemo(
    () =>
      Array.from(
        new Set(rows.map((row) => row.orderNo).filter(Boolean) as string[]),
      ).sort(),
    [rows],
  );

  const filteredRows = useMemo(
    () =>
      orderNoFilter
        ? rows.filter((row) => row.orderNo === orderNoFilter)
        : rows,
    [rows, orderNoFilter],
  );

  const updateTaskCache = (nextTask: PrintTask) => {
    setTasks((prev) => prev.map((task) => (task.id === nextTask.id ? nextTask : task)));
    setActiveTask((current) => (current && current.id === nextTask.id ? nextTask : current));
    setActiveRow((current) => (current ? mergeTaskIntoRow(current, nextTask) : current));
  };

  const requestPreview = async (task: PrintTask) => {
    setActiveTask(task);
    setActivePrintType(task.printType);
    setPreviewUrl(task.previewUrl || "");
    setPreviewLoading(task.printType);
    try {
      const preview = await generatePrintTaskPreview(task.id);
      setPreviewUrl(preview.previewUrl);
      updateTaskCache({ ...task, previewUrl: preview.previewUrl });
    } catch (error) {
      message.error(error instanceof Error ? error.message : "PDF 预览生成失败");
      await loadTasks();
    } finally {
      setPreviewLoading("");
    }
  };

  const openPrintModal = (row: PrintTaskRow) => {
    const defaultTask = row.orderTask || row.packingTask;
    setActiveRow(row);
    if (!defaultTask) {
      setActiveTask(null);
      setActivePrintType("");
      setPreviewUrl("");
      return;
    }
    void requestPreview(defaultTask);
  };

  const closePrintModal = () => {
    setActiveRow(null);
    setActiveTask(null);
    setActivePrintType("");
    setPreviewUrl("");
    setPreviewLoading("");
    setRegenerating(false);
    setMarkingPrinted("");
  };

  const loadPreview = async (printType: PrintType) => {
    const task = taskForPrintType(activeRow, printType);
    if (!task) {
      message.warning(`${printType === PRINT_TYPES.ORDER ? "订单" : "装箱单"}任务未生成`);
      return;
    }
    await requestPreview(task);
  };

  const regeneratePreview = async () => {
    if (!activeTask) {
      return;
    }
    setRegenerating(true);
    try {
      const preview = await regeneratePrintTaskPreview(activeTask.id);
      setPreviewUrl(preview.previewUrl);
      updateTaskCache({ ...activeTask, previewUrl: preview.previewUrl });
      message.success("PDF 已重新生成");
    } catch (error) {
      message.error(error instanceof Error ? error.message : "PDF 重新生成失败");
      await loadTasks();
    } finally {
      setRegenerating(false);
    }
  };

  const markPrinted = async (printType: PrintType) => {
    const task = taskForPrintType(activeRow, printType);
    if (!task) {
      return;
    }
    setMarkingPrinted(printType);
    try {
      const nextTask = await markPrintTaskPrinted(task.id);
      updateTaskCache(nextTask);
      message.success(`${printType === PRINT_TYPES.ORDER ? "订单" : "装箱单"}已标记为已打印`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "标记已打印失败");
    } finally {
      setMarkingPrinted("");
    }
  };

  const columns = useMemo<ColumnsType<PrintTaskRow>>(
    // 表格列定义固定，用 useMemo 避免每次状态变化都重建。
    () => [
      {
        title: "订单流水号",
        dataIndex: "orderNo",
        width: 150,
        render: formatEmpty,
      },
      {
        title: "客户",
        dataIndex: "customerName",
        width: 150,
        render: formatEmpty,
      },
      {
        title: "开发编号",
        dataIndex: "styleNos",
        minWidth: 260,
        render: renderStyleNos,
      },
      {
        title: "订单总对数",
        dataIndex: "totalPairs",
        width: 120,
        align: "right",
        render: formatEmpty,
      },
      {
        title: "状态",
        key: "status",
        width: 110,
        render: (_, record) => renderTaskStatus(getRowStatus(record)),
      },
      {
        title: "打印确认",
        key: "printedFlags",
        width: 150,
        render: (_, record) => renderPrintFlags(record),
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
        width: 210,
        fixed: "right",
        render: (_, record) => (
          <Space size={8}>
            <Button
              type="primary"
              icon={<PrinterOutlined />}
              onClick={() => openPrintModal(record)}
            >
              打印
            </Button>
            <Upload {...buildReuploadProps(record)}>
              <Button
                disabled={
                  !record.orderId ||
                  uploading ||
                  (reuploadingOrderId !== null && reuploadingOrderId !== record.orderId)
                }
                loading={reuploadingOrderId === record.orderId}
                icon={<UploadOutlined />}
              >
                重新上传
              </Button>
            </Upload>
          </Space>
        ),
      },
    ],
    [buildReuploadProps, reuploadingOrderId, uploading],
  );

  return (
    <div className="workspace">
      <div className="toolbar-band">
        <div>
          <Typography.Title level={3}>打印订单</Typography.Title>
          <Typography.Text type="secondary">
            上传订单 Excel 后生成待打印记录，可预览订单和装箱单。
          </Typography.Text>
        </div>
        <Space wrap>
          <Select
            allowClear
            showSearch
            placeholder="过滤订单流水号"
            value={orderNoFilter}
            onChange={(value) => setOrderNoFilter(value)}
            options={orderNoOptions.map((no) => ({ value: no, label: no }))}
            style={{ minWidth: 200 }}
            filterOption={(input, option) =>
              (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
            }
          />
          <Upload {...uploadProps}>
            <Button
              type="primary"
              loading={uploading}
              icon={<InboxOutlined />}
            >
              上传订单 Excel
            </Button>
          </Upload>
          <Button icon={<ReloadOutlined />} onClick={() => void loadTasks()}>
            刷新
          </Button>
        </Space>
      </div>

      <div className="page-panel">
        <Table
          rowKey="key"
          loading={loading}
          columns={columns}
          dataSource={filteredRows}
          pagination={{ pageSize: 20, showSizeChanger: false }}
          scroll={{ x: 1220 }}
          className="data-table"
        />
      </div>

      <Modal
        open={Boolean(activeTask)}
        title={
          activeRow ? `打印预览：${activeRow.orderNo || activeRow.orderId || ""}` : "打印预览"
        }
        onCancel={closePrintModal}
        width={1120}
        footer={null}
        destroyOnClose
      >
        <div className="print-modal-body">
          <Space wrap className="print-options">
            <Button
              type={activePrintType === PRINT_TYPES.ORDER ? "primary" : "default"}
              loading={previewLoading === PRINT_TYPES.ORDER}
              disabled={!activeRow?.orderTask}
              onClick={() => void loadPreview(PRINT_TYPES.ORDER)}
            >
              订单
            </Button>
            <Button
              type={activePrintType === PRINT_TYPES.PACKING ? "primary" : "default"}
              loading={previewLoading === PRINT_TYPES.PACKING}
              disabled={!activeRow?.packingTask}
              onClick={() => void loadPreview(PRINT_TYPES.PACKING)}
            >
              装箱单
            </Button>
            <Button
              danger
              icon={<ReloadOutlined />}
              disabled={!activeTask || !previewUrl || Boolean(previewLoading)}
              loading={regenerating}
              onClick={() => void regeneratePreview()}
            >
              重新生成
            </Button>
            <Button
              icon={<CheckCircleOutlined />}
              disabled={
                activePrintType !== PRINT_TYPES.ORDER ||
                !previewUrl ||
                Boolean(previewLoading) ||
                activeRow?.orderTask?.status === "PRINTED"
              }
              loading={markingPrinted === PRINT_TYPES.ORDER}
              onClick={() => void markPrinted(PRINT_TYPES.ORDER)}
            >
              订单已打印
            </Button>
            <Button
              icon={<CheckCircleOutlined />}
              disabled={
                activePrintType !== PRINT_TYPES.PACKING ||
                !previewUrl ||
                Boolean(previewLoading) ||
                activeRow?.packingTask?.status === "PRINTED"
              }
              loading={markingPrinted === PRINT_TYPES.PACKING}
              onClick={() => void markPrinted(PRINT_TYPES.PACKING)}
            >
              装箱单已打印
            </Button>
            <Typography.Text type="secondary">
              生成预览后，使用 PDF 预览窗口里的打印功能即可。
            </Typography.Text>
          </Space>
          <PdfPreview url={previewUrl} />
        </div>
      </Modal>
    </div>
  );
}
