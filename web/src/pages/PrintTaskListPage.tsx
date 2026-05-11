import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
  ReloadOutlined,
} from "@ant-design/icons";
import { App, Button, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  fetchPendingTasks,
  updatePrintTaskStatus,
} from "../api/printTaskApi";
import type { PrintTask, PrintTaskStatus } from "../types/order";
import { formatDateTime, formatEmpty } from "../utils/format";

export default function PrintTaskListPage() {
  const { message } = App.useApp();
  const [tasks, setTasks] = useState<PrintTask[]>([]);
  const [loading, setLoading] = useState(false);
  const [updatingId, setUpdatingId] = useState<number>();

  const loadTasks = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchPendingTasks();
      setTasks(data);
    } catch (error) {
      setTasks([]);
      message.error(error instanceof Error ? error.message : "打印任务加载失败");
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void loadTasks();
  }, [loadTasks]);

  const updateStatus = async (task: PrintTask, status: PrintTaskStatus) => {
    setUpdatingId(task.id);
    try {
      await updatePrintTaskStatus(task.id, status);
      message.success("任务状态已更新");
      await loadTasks();
    } catch (error) {
      message.error(error instanceof Error ? error.message : "更新状态失败");
    } finally {
      setUpdatingId(undefined);
    }
  };

  const columns = useMemo<ColumnsType<PrintTask>>(
    () => [
      {
        title: "任务号",
        dataIndex: "taskNo",
        width: 220,
      },
      {
        title: "订单号",
        dataIndex: "orderNo",
        width: 150,
        render: formatEmpty,
      },
      {
        title: "打印类型",
        dataIndex: "printType",
        width: 120,
        render: (value: string) =>
          value === "ORDER" ? <Tag color="blue">订单</Tag> : <Tag color="purple">装箱单</Tag>,
      },
      {
        title: "份数",
        dataIndex: "copies",
        width: 80,
        align: "right",
      },
      {
        title: "打印机",
        dataIndex: "printerName",
        width: 160,
        render: formatEmpty,
      },
      {
        title: "状态",
        dataIndex: "status",
        width: 110,
        render: (status: PrintTaskStatus) => <Tag color="gold">{status}</Tag>,
      },
      {
        title: "创建时间",
        dataIndex: "createdAt",
        width: 180,
        render: formatDateTime,
      },
      {
        title: "操作",
        key: "actions",
        fixed: "right",
        width: 220,
        render: (_, task) => (
          <Space size={8}>
            <Button
              icon={<LoadingOutlined />}
              loading={updatingId === task.id}
              onClick={() => void updateStatus(task, "PRINTING")}
            >
              打印中
            </Button>
            <Button
              icon={<CheckCircleOutlined />}
              loading={updatingId === task.id}
              onClick={() => void updateStatus(task, "SUCCESS")}
            >
              成功
            </Button>
            <Button
              danger
              icon={<CloseCircleOutlined />}
              loading={updatingId === task.id}
              onClick={() => void updateStatus(task, "FAILED")}
            >
              失败
            </Button>
          </Space>
        ),
      },
    ],
    [updatingId],
  );

  return (
    <div className="workspace">
      <div className="toolbar-band">
        <div>
          <Typography.Title level={3}>打印任务</Typography.Title>
          <Typography.Text type="secondary">
            当前展示待打印队列，后续由本地 print-agent 消费。
          </Typography.Text>
        </div>
        <Button icon={<ReloadOutlined />} onClick={() => void loadTasks()}>
          刷新
        </Button>
      </div>
      <div className="page-panel">
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={tasks}
          pagination={false}
          scroll={{ x: 1100 }}
          className="data-table"
        />
      </div>
    </div>
  );
}
