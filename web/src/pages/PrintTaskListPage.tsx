import { InboxOutlined, PrinterOutlined, ReloadOutlined, UploadOutlined } from "@ant-design/icons";
import { App, Button, Modal, Space, Table, Tag, Typography, Upload } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { UploadProps } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import { uploadOrderFile } from "../api/orderApi";
import { fetchPrintTasks, generatePrintTaskPreview } from "../api/printTaskApi";
import PdfPreview from "../components/PdfPreview";
import type { PrintTask, PrintTaskStatus } from "../types/order";
import { formatDateTime, formatEmpty } from "../utils/format";

const allowedExtensions = ["xlsx", "xls"];

function isAllowedFile(file: File) {
  const extension = file.name.split(".").pop()?.toLowerCase();
  return Boolean(extension && allowedExtensions.includes(extension));
}

function renderTaskStatus(status: PrintTaskStatus) {
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
  const [tasks, setTasks] = useState<PrintTask[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [activeTask, setActiveTask] = useState<PrintTask | null>(null);
  const [previewUrl, setPreviewUrl] = useState("");
  const [previewLoading, setPreviewLoading] = useState<"ORDER" | "PACKING" | "">("");

  const loadTasks = useCallback(async () => {
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
    setActiveTask(task);
    setPreviewUrl(task.previewUrl || "");
  };

  const closePrintModal = () => {
    setActiveTask(null);
    setPreviewUrl("");
    setPreviewLoading("");
  };

  const loadPreview = async (printType: "ORDER" | "PACKING") => {
    if (!activeTask) {
      return;
    }
    setPreviewLoading(printType);
    try {
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
              loading={previewLoading === "ORDER"}
              onClick={() => void loadPreview("ORDER")}
            >
              订单
            </Button>
            <Button
              loading={previewLoading === "PACKING"}
              onClick={() => void loadPreview("PACKING")}
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
