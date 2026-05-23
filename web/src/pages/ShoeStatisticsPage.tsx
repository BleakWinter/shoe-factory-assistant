import {
  BarChartOutlined,
  FileSearchOutlined,
  ReloadOutlined,
} from "@ant-design/icons";
import { App, Button, Empty, Modal, Skeleton, Space, Statistic, Table, Tag, Typography } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import { fetchOrderStatistics } from "../api/statisticsApi";
import type { DevelopmentNoOrderReference, DevelopmentNoStatisticNode, OrderStatistics } from "../types/order";

function formatCount(value?: number) {
  return new Intl.NumberFormat("zh-CN").format(value || 0);
}

function formatText(value?: string) {
  return value?.trim() || "-";
}

const DONUT_COLORS = [
  "#2f6df6",
  "#0f766e",
  "#f59e0b",
  "#dc2626",
  "#7c3aed",
  "#0891b2",
  "#16a34a",
  "#be185d",
  "#64748b",
];

const MAX_DONUT_SLICES = 8;
const DONUT_DEPTH_OFFSETS = [16, 12, 8, 4];

interface DonutSlice {
  key: string;
  label: string;
  value: number;
  percent: number;
  color: string;
  depthColor: string;
  startAngle: number;
  endAngle: number;
  node?: DevelopmentNoStatisticNode;
}

function shadeHexColor(color: string, amount: number) {
  const hex = color.replace("#", "");
  const value = Number.parseInt(hex, 16);
  const red = Math.max(0, Math.min(255, (value >> 16) + amount));
  const green = Math.max(0, Math.min(255, ((value >> 8) & 0xff) + amount));
  const blue = Math.max(0, Math.min(255, (value & 0xff) + amount));
  return `#${((1 << 24) + (red << 16) + (green << 8) + blue).toString(16).slice(1)}`;
}

function getNodeChildren(node?: DevelopmentNoStatisticNode) {
  return node?.children || [];
}

function getDisplayDevelopmentNo(path: DevelopmentNoStatisticNode[], node: DevelopmentNoStatisticNode) {
  if (node.fullDevelopmentNo) {
    return node.fullDevelopmentNo;
  }
  return [...path.map((item) => item.label), node.label].join("-");
}

function polarToCartesian(center: number, radius: number, angle: number) {
  const angleInRadians = ((angle - 90) * Math.PI) / 180;
  return {
    x: center + radius * Math.cos(angleInRadians),
    y: center + radius * Math.sin(angleInRadians),
  };
}

function describeDonutSlice(startAngle: number, endAngle: number) {
  const center = 110;
  const outerRadius = 92;
  const innerRadius = 54;
  const safeEndAngle = endAngle - startAngle >= 360 ? startAngle + 359.99 : endAngle;
  const outerStart = polarToCartesian(center, outerRadius, startAngle);
  const outerEnd = polarToCartesian(center, outerRadius, safeEndAngle);
  const innerStart = polarToCartesian(center, innerRadius, safeEndAngle);
  const innerEnd = polarToCartesian(center, innerRadius, startAngle);
  const largeArcFlag = safeEndAngle - startAngle > 180 ? 1 : 0;

  return [
    `M ${outerStart.x} ${outerStart.y}`,
    `A ${outerRadius} ${outerRadius} 0 ${largeArcFlag} 1 ${outerEnd.x} ${outerEnd.y}`,
    `L ${innerStart.x} ${innerStart.y}`,
    `A ${innerRadius} ${innerRadius} 0 ${largeArcFlag} 0 ${innerEnd.x} ${innerEnd.y}`,
    "Z",
  ].join(" ");
}

function buildDonutSlices(nodes: DevelopmentNoStatisticNode[], parentPath: DevelopmentNoStatisticNode[]) {
  const sortedNodes = [...nodes]
    .filter((node) => (node.pairCount || 0) > 0)
    .sort((left, right) => (right.pairCount || 0) - (left.pairCount || 0));
  const total = sortedNodes.reduce((sum, node) => sum + (node.pairCount || 0), 0);
  if (total <= 0) {
    return [];
  }

  const directSlices = sortedNodes.slice(0, MAX_DONUT_SLICES).map((node) => ({
    key: node.key,
    label: [...parentPath.map((item) => item.label), node.label].join("-"),
    value: node.pairCount || 0,
    node,
  }));
  const otherValue = sortedNodes
    .slice(MAX_DONUT_SLICES)
    .reduce((sum, node) => sum + (node.pairCount || 0), 0);
  const chartItems = otherValue > 0
    ? [...directSlices, { key: "OTHER", label: "其他", value: otherValue }]
    : directSlices;

  let startAngle = 0;
  return chartItems.map((item, index) => {
    const angle = (item.value / total) * 360;
    const endAngle = index === chartItems.length - 1 ? 360 : startAngle + angle;
    const color = DONUT_COLORS[index % DONUT_COLORS.length];
    const slice: DonutSlice = {
      ...item,
      percent: Math.round((item.value / total) * 1000) / 10,
      color,
      depthColor: shadeHexColor(color, -58),
      startAngle,
      endAngle,
    };
    startAngle = endAngle;
    return slice;
  });
}

export default function ShoeStatisticsPage() {
  const { message } = App.useApp();
  const [statistics, setStatistics] = useState<OrderStatistics | null>(null);
  const [loading, setLoading] = useState(false);
  const [drillPath, setDrillPath] = useState<DevelopmentNoStatisticNode[]>([]);
  const [selectedKey, setSelectedKey] = useState("");
  const [orderReferenceNode, setOrderReferenceNode] = useState<DevelopmentNoStatisticNode | null>(null);

  const loadStatistics = useCallback(async () => {
    setLoading(true);
    try {
      const next = await fetchOrderStatistics();
      setStatistics(next);
      setDrillPath([]);
      setSelectedKey("");
      setOrderReferenceNode(null);
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
  const donutSlices = useMemo(() => buildDonutSlices(visibleNodes, drillPath), [drillPath, visibleNodes]);
  const visiblePairTotal = visibleNodes.reduce((sum, node) => sum + (node.pairCount || 0), 0);
  const topNodes = statistics?.topDevelopmentNos || [];
  const maxTopPairs = Math.max(1, ...topNodes.map((node) => node.pairCount || 0));
  const orderReferences = orderReferenceNode?.orderReferences || [];
  const orderReferenceColumns = useMemo(
    () => [
      {
        title: "发票编号",
        dataIndex: "invoiceNo",
        key: "invoiceNo",
        render: (value?: string) => formatText(value),
      },
      {
        title: "双数",
        dataIndex: "pairCount",
        key: "pairCount",
        align: "right" as const,
        render: (value?: number) => `${formatCount(value)} 双`,
      },
    ],
    [],
  );

  const openNode = useCallback((node: DevelopmentNoStatisticNode) => {
    if (getNodeChildren(node).length > 0) {
      setDrillPath((prev) => [...prev, node]);
      setSelectedKey("");
      return;
    }
    setSelectedKey(node.key);
  }, []);

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

              <div className={`statistics-donut-layout${donutSlices.length === 0 ? " statistics-donut-layout-empty" : ""}`}>
                <div className="statistics-donut-wrap">
                  <svg className="statistics-donut-svg" viewBox="0 0 220 238" role="img" aria-label="款号双数占比">
                    {donutSlices.length === 0 ? (
                      <>
                        <circle className="statistics-donut-empty-ring-depth" cx="110" cy="126" r="73" />
                        <circle className="statistics-donut-empty-ring" cx="110" cy="110" r="73" />
                      </>
                    ) : (
                      <>
                        {DONUT_DEPTH_OFFSETS.map((offset) => (
                          <g key={offset} transform={`translate(0 ${offset})`}>
                            {donutSlices.map((slice) => (
                              <path
                                key={`${slice.key}-${offset}`}
                                className="statistics-donut-depth-slice"
                                d={describeDonutSlice(slice.startAngle, slice.endAngle)}
                                fill={slice.depthColor}
                              />
                            ))}
                          </g>
                        ))}
                        {donutSlices.map((slice) => (
                          <path
                            key={slice.key}
                            className={[
                              "statistics-donut-slice",
                              slice.node?.key === selectedKey ? "statistics-donut-slice-active" : "",
                              !slice.node ? "statistics-donut-slice-disabled" : "",
                            ].filter(Boolean).join(" ")}
                            d={describeDonutSlice(slice.startAngle, slice.endAngle)}
                            fill={slice.color}
                            onClick={() => {
                              if (slice.node) {
                                openNode(slice.node);
                              }
                            }}
                          >
                            <title>{`${slice.label}: ${formatCount(slice.value)} 双，占 ${slice.percent}%`}</title>
                          </path>
                        ))}
                      </>
                    )}
                  </svg>
                  <div className="statistics-donut-center">
                    <span>{donutSlices.length === 0 ? "暂无" : formatCount(visiblePairTotal)}</span>
                    <small>{donutSlices.length === 0 ? "统计数据" : "当前双数"}</small>
                  </div>
                </div>
                <div className="statistics-donut-legend">
                  {donutSlices.length === 0 ? (
                    <div className="statistics-donut-empty-note">
                      <Typography.Text strong>暂无统计数据</Typography.Text>
                      <Typography.Text type="secondary">上传并识别订单后生成占比图</Typography.Text>
                    </div>
                  ) : (
                    donutSlices.map((slice) => {
                      const childCount = getNodeChildren(slice.node).length;
                      const canOpen = !!slice.node;
                      const isLeaf = canOpen && childCount === 0;
                      const orderReferenceCount = slice.node?.orderReferences?.length || 0;
                      return (
                        <div
                          key={slice.key}
                          role="button"
                          tabIndex={canOpen ? 0 : -1}
                          aria-disabled={!canOpen}
                          className={[
                            "statistics-donut-legend-item",
                            slice.node?.key === selectedKey ? "statistics-donut-legend-item-active" : "",
                            !canOpen ? "statistics-donut-legend-item-disabled" : "",
                          ].filter(Boolean).join(" ")}
                          onClick={() => slice.node && openNode(slice.node)}
                          onKeyDown={(event) => {
                            if (!slice.node || (event.key !== "Enter" && event.key !== " ")) {
                              return;
                            }
                            event.preventDefault();
                            openNode(slice.node);
                          }}
                        >
                          <span className="statistics-donut-swatch" style={{ background: slice.color }} />
                          <span className="statistics-donut-legend-main">
                            <strong>{slice.label}</strong>
                            <small>
                              {formatCount(slice.value)} 双
                              {childCount > 0 ? ` / ${childCount} 个下级` : ""}
                            </small>
                          </span>
                          <strong className="statistics-donut-percent">{slice.percent}%</strong>
                          {isLeaf ? (
                            <Button
                              size="small"
                              type="link"
                              className="statistics-order-link"
                              icon={<FileSearchOutlined />}
                              disabled={orderReferenceCount === 0}
                              onClick={(event) => {
                                event.stopPropagation();
                                if (slice.node) {
                                  setOrderReferenceNode(slice.node);
                                }
                              }}
                            >
                              查看发票编号
                            </Button>
                          ) : null}
                        </div>
                      );
                    })
                  )}
                </div>
              </div>
            </div>

            <div className="page-panel statistics-top-panel">
              <div className="statistics-panel-heading">
                <div>
                  <Typography.Title level={4}>双数前十款号</Typography.Title>
                  <Typography.Text type="secondary">按完整款号汇总</Typography.Text>
                </div>
                <BarChartOutlined />
              </div>
              {topNodes.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据" />
              ) : (
                <div className="statistics-rank-list">
                  {topNodes.map((node, index) => {
                    const width = Math.max(4, Math.round(((node.pairCount || 0) / maxTopPairs) * 100));
                    return (
                      <div key={node.key} className="statistics-rank-row">
                        <span className="statistics-rank-index">{index + 1}</span>
                        <span className="statistics-rank-main">
                          <span className="statistics-rank-label">{node.fullDevelopmentNo || node.label}</span>
                          <span className="statistics-rank-track">
                            <span className="statistics-rank-fill" style={{ width: `${width}%` }} />
                          </span>
                        </span>
                        <strong>{formatCount(node.pairCount)} 双</strong>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>

        </>
      )}
      <Modal
        open={!!orderReferenceNode}
        title={
          orderReferenceNode
            ? `发票编号：${orderReferenceNode.fullDevelopmentNo || orderReferenceNode.label}`
            : "发票编号"
        }
        width={560}
        footer={null}
        onCancel={() => setOrderReferenceNode(null)}
        destroyOnClose
      >
        <Table<DevelopmentNoOrderReference>
          rowKey={(record, index) => [
            record.orderId || "order",
            record.invoiceNo || "invoice",
            record.orderNo || "customer-order",
            index,
          ].join("-")}
          columns={orderReferenceColumns}
          dataSource={orderReferences}
          pagination={orderReferences.length > 10 ? { pageSize: 10, showSizeChanger: false } : false}
          size="small"
          className="statistics-order-reference-table"
        />
      </Modal>
    </div>
  );
}
