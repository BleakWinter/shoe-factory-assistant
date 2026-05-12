import { ReloadOutlined } from "@ant-design/icons";
import { App, Button, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import { fetchPrintTasks } from "../api/printTaskApi";
import type { PrintTask, PrintTaskStatus } from "../types/order";
import { formatDateTime, formatEmpty } from "../utils/format";

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

  const columns = useMemo<ColumnsType<PrintTask>>(
    () => [
      {
        title: "订单号",
        dataIndex: "orderNo",
        width: 160,
        render: formatEmpty,
      },
      {
        title: "客户",
        dataIndex: "customerName",
        width: 150,
        render: formatEmpty,
      },
      {
        title: "订单里的款号",
        dataIndex: "styleNos",
        minWidth: 260,
        render: renderStyleNos,
      },
      {
        title: "总双数",
        dataIndex: "totalPairs",
        width: 110,
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
        title: "加入时间",
        dataIndex: "createdAt",
        width: 170,
        render: formatDateTime,
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
            这里按订单显示打印任务，只放打印需要认得出来的订单信息。
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
          pagination={{ pageSize: 20, showSizeChanger: false }}
          scroll={{ x: 980 }}
          className="data-table"
        />
      </div>
    </div>
  );
}
