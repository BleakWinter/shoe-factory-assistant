import { InboxOutlined, PrinterOutlined, ReloadOutlined, UploadOutlined } from "@ant-design/icons";
import { App, Button, Modal, Space, Table, Tag, Typography, Upload } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { UploadProps } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import { uploadOrderFile } from "../api/orderApi";
import { fetchPrintTasks, generatePrintTaskPreview } from "../api/printTaskApi";
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
    PRINTING: { color: "blue", label: "打印中" },
    SUCCESS: { color: "green", label: "已完成" },
    FAILED: { color: "red", label: "失败" },
    CANCELED: { color: "default", label: "已取消" },
  };
  const item = map[status] || { color: "default", label: status };
  return <Tag color={item.color}>{item.label}</Tag>;
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

export default function PrintTaskListPage() {
  const { message } = App.useApp();
  // tasks 是表格数据；activeTask 是当前打开打印弹窗的那条任务。
  const [tasks, setTasks] = useState<PrintTask[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [activeTask, setActiveTask] = useState<PrintTask | null>(null);
  const [previewUrl, setPreviewUrl] = useState("");
  const [previewLoading, setPreviewLoading] = useState<PrintType | "">("");

  const loadTasks = useCallback(async () => {
    // 刷新打印任务列表。上传成功后也会调用一次。
    setLoading(true);
    try {
      const data = await fetchPrintTasks();
      setTasks(data);
    } catch (error) {
      setTasks([]);
      message.error(error instanceof Error ? error.message : "打印列表加载失败");
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
    disabled: uploading,
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
        message.success(
          `订单已上传，打印列表已生成任务 ${result.printTaskNo || ""}，共 ${result.totalPairs || 0} 双`,
        );
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

  const openPrintModal = (task: PrintTask) => {
    // 如果任务之前已经生成过预览，打开弹窗时直接显示旧 previewUrl。
    setActiveTask(task);
    setPreviewUrl(task.previewUrl || "");
  };

  const closePrintModal = () => {
    setActiveTask(null);
    setPreviewUrl("");
    setPreviewLoading("");
  };

  const loadPreview = async (printType: PrintType) => {
    if (!activeTask) {
      return;
    }
    setPreviewLoading(printType);
    try {
      // 生成 PDF 后，把当前任务的 previewUrl 同步回表格缓存。
      const preview = await generatePrintTaskPreview(activeTask.id, printType);
      setPreviewUrl(preview.previewUrl);
      setTasks((prev) =>
        prev.map((task) =>
          task.id === activeTask.id ? { ...task, previewUrl: preview.previewUrl } : task,
        ),
      );
    } catch (error) {
      message.error(error instanceof Error ? error.message : "PDF 预览生成失败");
    } finally {
      setPreviewLoading("");
    }
  };

  const columns = useMemo<ColumnsType<PrintTask>>(
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
        dataIndex: "status",
        width: 110,
        render: renderTaskStatus,
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
            <Button disabled icon={<UploadOutlined />}>
              {/* 预留按钮：后续做“重新上传并覆盖旧订单”。 */}
              重新上传
            </Button>
          </Space>
        ),
      },
    ],
    [],
  );

  return (
    <div className="workspace">
      <div className="toolbar-band">
        <div>
          <Typography.Title level={3}>打印列表</Typography.Title>
          <Typography.Text type="secondary">
            在这里上传订单 Excel，系统会生成待打印记录；点打印后选择订单或装箱单预览。
          </Typography.Text>
        </div>
        <Space wrap>
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
          <Button icon={<ReloadOutlined />} onClick={() => void loadTasks()}>
            刷新
          </Button>
        </Space>
      </div>

      <div className="page-panel">
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={tasks}
          pagination={{ pageSize: 20, showSizeChanger: false }}
          scroll={{ x: 1180 }}
          className="data-table"
        />
      </div>

      <Modal
        open={Boolean(activeTask)}
        title={activeTask ? `打印预览：${activeTask.orderNo || activeTask.taskNo}` : "打印预览"}
        onCancel={closePrintModal}
        width={1120}
        footer={null}
        destroyOnClose
      >
        <div className="print-modal-body">
          <Space wrap className="print-options">
            <Button
              type="primary"
              loading={previewLoading === PRINT_TYPES.ORDER}
              onClick={() => void loadPreview(PRINT_TYPES.ORDER)}
            >
              订单
            </Button>
            <Button
              loading={previewLoading === PRINT_TYPES.PACKING}
              onClick={() => void loadPreview(PRINT_TYPES.PACKING)}
            >
              装箱单
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
