import {
  ArrowRightOutlined,
  BarChartOutlined,
  ReloadOutlined,
  RightOutlined,
} from "@ant-design/icons";
import { App, Button, Empty, Skeleton, Space, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import { fetchOrderStatistics } from "../api/statisticsApi";
import type { DevelopmentNoStatisticNode, OrderStatistics } from "../types/order";

function formatCount(value?: number) {
  return new Intl.NumberFormat("zh-CN").format(value || 0);
}

function getNodeChildren(node?: DevelopmentNoStatisticNode) {
  return node?.children || [];
}

function getLevelText(level?: number) {
  if (level === 1) {
    return "一级";
  }
  if (level === 2) {
    return "二级";
  }
  if (level === 3) {
    return "三级";
  }
  return "款号";
}

function getDisplayDevelopmentNo(path: DevelopmentNoStatisticNode[], node: DevelopmentNoStatisticNode) {
  if (node.fullDevelopmentNo) {
    return node.fullDevelopmentNo;
  }
  return [...path.map((item) => item.label), node.label].join("-");
}

export default function ShoeStatisticsPage() {
  const { message } = App.useApp();
  const [statistics, setStatistics] = useState<OrderStatistics | null>(null);
  const [loading, setLoading] = useState(false);
  const [drillPath, setDrillPath] = useState<DevelopmentNoStatisticNode[]>([]);
  const [selectedKey, setSelectedKey] = useState("");

  const loadStatistics = useCallback(async () => {
    setLoading(true);
    try {
      const next = await fetchOrderStatistics();
      setStatistics(next);
      setDrillPath([]);
      setSelectedKey("");
    } catch (error) {
      setStatistics(null);
      message.error(error instanceof Error ? error.message : "统计数据加载失败");
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void loadStatistics();
  }, [loadStatistics]);

  const activeNode = drillPath[drillPath.length - 1];
  const visibleNodes = activeNode
    ? getNodeChildren(activeNode)
    : statistics?.developmentNoTree || [];
  const maxPairs = Math.max(1, ...visibleNodes.map((node) => node.pairCount || 0));

  const openNode = useCallback((node: DevelopmentNoStatisticNode) => {
    if (getNodeChildren(node).length > 0) {
      setDrillPath((prev) => [...prev, node]);
      setSelectedKey("");
      return;
    }
    setSelectedKey(node.key);
  }, []);

  const columns = useMemo<ColumnsType<DevelopmentNoStatisticNode>>(
    () => [
      {
        title: "层级",
        dataIndex: "level",
        width: 90,
        render: (value: number) => <Tag color="blue">{getLevelText(value)}</Tag>,
      },
      {
        title: "款号",
        dataIndex: "label",
        render: (_, record) => (
          <Typography.Text strong={record.key === selectedKey}>
            {getDisplayDevelopmentNo(drillPath, record)}
          </Typography.Text>
        ),
      },
      {
        title: "双数",
        dataIndex: "pairCount",
        width: 130,
        align: "right",
        render: (value: number) => `${formatCount(value)} 双`,
      },
      {
        title: "款号数量",
        dataIndex: "styleCount",
        width: 110,
        align: "right",
        render: formatCount,
      },
      {
        title: "明细行",
        dataIndex: "detailCount",
        width: 100,
        align: "right",
        render: formatCount,
      },
      {
        title: "下级",
        key: "children",
        width: 100,
        align: "right",
        render: (_, record) => {
          const childCount = getNodeChildren(record).length;
          return childCount > 0 ? `${childCount} 项` : "-";
        },
      },
    ],
    [drillPath, selectedKey],
  );

  const topColumns = useMemo<ColumnsType<DevelopmentNoStatisticNode>>(
    () => [
      {
        title: "款号",
        dataIndex: "fullDevelopmentNo",
        render: (value: string, record) => value || record.label,
      },
      {
        title: "双数",
        dataIndex: "pairCount",
        width: 130,
        align: "right",
        render: (value: number) => `${formatCount(value)} 双`,
      },
      {
        title: "明细行",
        dataIndex: "detailCount",
        width: 100,
        align: "right",
        render: formatCount,
      },
    ],
    [],
  );

  return (
    <div className="workspace statistics-page">
      <div className="toolbar-band">
        <div>
          <Typography.Title level={3}>数据统计</Typography.Title>
          <Typography.Text type="secondary">按订单明细汇总鞋子双数和款号层级。</Typography.Text>
        </div>
        <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadStatistics()}>
          刷新
        </Button>
      </div>

      {loading && !statistics ? (
        <div className="page-panel">
          <Skeleton active paragraph={{ rows: 8 }} />
        </div>
      ) : (
        <>
          <div className="statistics-summary-grid">
            <div className="statistics-metric">
              <Statistic title="鞋子总双数" value={statistics?.totalPairs || 0} suffix="双" />
            </div>
            <div className="statistics-metric">
              <Statistic title="款号数量" value={statistics?.styleCount || 0} />
            </div>
            <div className="statistics-metric">
              <Statistic title="订单明细行" value={statistics?.detailCount || 0} />
            </div>
          </div>

          <div className="statistics-grid">
            <div className="page-panel statistics-chart-panel">
              <div className="statistics-panel-heading">
                <div>
                  <Typography.Title level={4}>款号分层统计</Typography.Title>
                  <Space size={4} wrap className="statistics-breadcrumb">
                    <Button
                      size="small"
                      type={drillPath.length === 0 ? "primary" : "default"}
                      onClick={() => {
                        setDrillPath([]);
                        setSelectedKey("");
                      }}
                    >
                      全部
                    </Button>
                    {drillPath.map((item, index) => (
                      <Button
                        key={item.key}
                        size="small"
                        type={index === drillPath.length - 1 ? "primary" : "default"}
                        onClick={() => {
                          setDrillPath((prev) => prev.slice(0, index + 1));
                          setSelectedKey("");
                        }}
                      >
                        {item.label}
                      </Button>
                    ))}
                  </Space>
                </div>
                {activeNode ? (
                  <Tag color="geekblue">
                    {getDisplayDevelopmentNo(drillPath.slice(0, -1), activeNode)}
                  </Tag>
                ) : null}
              </div>

              {visibleNodes.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无统计数据" />
              ) : (
                <div className="statistics-bar-list">
                  {visibleNodes.map((node) => {
                    const childCount = getNodeChildren(node).length;
                    const width = Math.max(4, Math.round(((node.pairCount || 0) / maxPairs) * 100));
                    return (
                      <button
                        key={node.key}
                        type="button"
                        className={`statistics-bar-row${node.key === selectedKey ? " statistics-bar-row-active" : ""}`}
                        onClick={() => openNode(node)}
                      >
                        <span className="statistics-bar-label">
                          <strong>{node.label}</strong>
                          <small>{getLevelText(node.level)}</small>
                        </span>
                        <span className="statistics-bar-track">
                          <span className="statistics-bar-fill" style={{ width: `${width}%` }} />
                        </span>
                        <span className="statistics-bar-value">{formatCount(node.pairCount)} 双</span>
                        {childCount > 0 ? <RightOutlined /> : <BarChartOutlined />}
                      </button>
                    );
                  })}
                </div>
              )}
            </div>

            <div className="page-panel statistics-top-panel">
              <div className="statistics-panel-heading">
                <div>
                  <Typography.Title level={4}>双数前十款号</Typography.Title>
                  <Typography.Text type="secondary">按完整款号汇总</Typography.Text>
                </div>
                <ArrowRightOutlined />
              </div>
              <Table
                rowKey="key"
                columns={topColumns}
                dataSource={statistics?.topDevelopmentNos || []}
                pagination={false}
                size="small"
                className="nested-data-table"
              />
            </div>
          </div>

          <div className="page-panel">
            <Table
              rowKey="key"
              columns={columns}
              dataSource={visibleNodes}
              loading={loading}
              pagination={false}
              className="data-table statistics-node-table"
              onRow={(record) => ({
                onClick: () => openNode(record),
              })}
            />
          </div>
        </>
      )}
    </div>
  );
}
