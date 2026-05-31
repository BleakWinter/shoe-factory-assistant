import { Bar, Column, type BarConfig, type ColumnConfig } from "@ant-design/charts";
import { FileSearchOutlined, ReloadOutlined } from "@ant-design/icons";
import { App, Button, Empty, Modal, Segmented, Skeleton, Statistic, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import { fetchOrderStatistics } from "../api/statisticsApi";
import type {
  DevelopmentNoOrderReference,
  DevelopmentNoStatisticNode,
  OrderStatistics,
  StatisticsTimePoint,
  UnshippedInvoiceStatistic,
} from "../types/order";

function formatCount(value?: number) {
  return new Intl.NumberFormat("zh-CN").format(value || 0);
}

function formatText(value?: string) {
  return value?.trim() || "-";
}

function parseDate(value?: string) {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function formatDate(value?: string) {
  const date = parseDate(value);
  if (!date) {
    return "-";
  }
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
}

function getPendingDays(value?: string) {
  const date = parseDate(value);
  if (!date) {
    return undefined;
  }
  const start = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  return Math.max(0, Math.floor((today - start) / 86_400_000));
}

function formatPendingDays(value?: number) {
  if (value === undefined) {
    return "-";
  }
  return value === 0 ? "今天" : `${formatCount(value)} 天`;
}

type StatisticModalType = "total" | "shipped" | "unshipped" | "styles";
type TimeGranularity = "year" | "month" | "day";

interface BarChartDatum {
  key: string;
  label: string;
  value: number;
}

interface TrendChartDatum {
  key: string;
  label: string;
  value: number;
}

interface StyleRankingRow {
  rank: number;
  key: string;
  label: string;
  pairCount: number;
  shippedPairCount: number;
  unshippedPairCount: number;
  invoiceCount: number;
  node: DevelopmentNoStatisticNode;
}

interface UnshippedInvoiceRow extends UnshippedInvoiceStatistic {
  rank: number;
  key: string;
  pendingDays?: number;
}

function getNodeChildren(node?: DevelopmentNoStatisticNode) {
  return node?.children || [];
}

function getNodeDisplayName(node: DevelopmentNoStatisticNode) {
  return node.fullDevelopmentNo || node.label;
}

function flattenStyleNodes(nodes: DevelopmentNoStatisticNode[]): DevelopmentNoStatisticNode[] {
  return nodes.flatMap((node) => {
    const children = getNodeChildren(node);
    return children.length > 0 ? flattenStyleNodes(children) : [node];
  });
}

function buildStyleRankingRows(nodes: DevelopmentNoStatisticNode[]): StyleRankingRow[] {
  return flattenStyleNodes(nodes)
    .map((node) => ({
      key: node.key,
      label: getNodeDisplayName(node),
      pairCount: node.pairCount || 0,
      shippedPairCount: node.shippedPairCount || 0,
      unshippedPairCount: node.unshippedPairCount || 0,
      invoiceCount: node.orderReferences?.length || 0,
      node,
    }))
    .sort((left, right) => right.pairCount - left.pairCount || left.label.localeCompare(right.label, "zh-CN", { numeric: true }))
    .map((row, index) => ({ ...row, rank: index + 1 }));
}

function buildUnshippedInvoiceRows(items: UnshippedInvoiceStatistic[]): UnshippedInvoiceRow[] {
  return items
    .filter((item) => (item.unshippedPairCount || 0) > 0)
    .map((item, index) => ({
      ...item,
      key: [item.orderId || "order", item.invoiceNo || "invoice", index].join("-"),
      pendingDays: getPendingDays(item.createdAt),
    }))
    .sort((left, right) => {
      const leftTime = parseDate(left.createdAt)?.getTime() ?? Number.POSITIVE_INFINITY;
      const rightTime = parseDate(right.createdAt)?.getTime() ?? Number.POSITIVE_INFINITY;
      return leftTime - rightTime || formatText(left.invoiceNo).localeCompare(formatText(right.invoiceNo), "zh-CN", { numeric: true });
    })
    .map((row, index) => ({ ...row, rank: index + 1 }));
}

function truncateText(value: string, maxLength: number) {
  return value.length > maxLength ? `${value.slice(0, maxLength)}...` : value;
}

function getPeriodKey(date: string, granularity: TimeGranularity) {
  if (granularity === "year") {
    return date.slice(0, 4);
  }
  if (granularity === "day") {
    return date.slice(0, 10);
  }
  return date.slice(0, 7);
}

function getPeriodLabel(period: string, granularity: TimeGranularity) {
  if (granularity === "year") {
    return `${period}年`;
  }
  if (granularity === "month") {
    const [year, month] = period.split("-");
    return `${year}年${Number(month)}月`;
  }
  return period;
}

function buildTrendChartData(points: StatisticsTimePoint[], granularity: TimeGranularity): TrendChartDatum[] {
  const valuesByPeriod = new Map<string, number>();
  for (const point of points) {
    if (!point.date) {
      continue;
    }
    const key = getPeriodKey(point.date, granularity);
    valuesByPeriod.set(key, (valuesByPeriod.get(key) || 0) + (point.value || 0));
  }
  return [...valuesByPeriod.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, value]) => ({
      key,
      label: getPeriodLabel(key, granularity),
      value,
    }));
}

export default function ShoeStatisticsPage() {
  const { message } = App.useApp();
  const [statistics, setStatistics] = useState<OrderStatistics | null>(null);
  const [loading, setLoading] = useState(false);
  const [activeStatistic, setActiveStatistic] = useState<StatisticModalType | null>(null);
  const [timeGranularity, setTimeGranularity] = useState<TimeGranularity>("month");
  const [orderReferenceNode, setOrderReferenceNode] = useState<DevelopmentNoStatisticNode | null>(null);

  const loadStatistics = useCallback(async () => {
    setLoading(true);
    try {
      const next = await fetchOrderStatistics();
      setStatistics(next);
      setActiveStatistic(null);
      setTimeGranularity("month");
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

  const openStatistic = useCallback((type: StatisticModalType) => {
    setActiveStatistic(type);
    if (type === "total" || type === "shipped") {
      setTimeGranularity("month");
    }
  }, []);

  const styleRows = useMemo(
    () => buildStyleRankingRows(statistics?.developmentNoTree || []),
    [statistics?.developmentNoTree],
  );
  const unshippedInvoiceRows = useMemo(
    () => buildUnshippedInvoiceRows(statistics?.unshippedInvoiceStatistics || []),
    [statistics?.unshippedInvoiceStatistics],
  );
  const orderReferences = orderReferenceNode?.orderReferences || [];
  const activeRows = useMemo(() => {
    if (activeStatistic !== "styles") {
      return [];
    }
    return styleRows
      .map((row) => ({
        ...row,
        value: row.pairCount,
      }))
      .filter((row) => row.value > 0)
      .sort((left, right) => right.value - left.value || left.label.localeCompare(right.label, "zh-CN", { numeric: true }))
      .map((row, index) => ({ ...row, rank: index + 1 }));
  }, [activeStatistic, styleRows]);
  const unshippedChartData = useMemo<BarChartDatum[]>(
    () => unshippedInvoiceRows.slice(0, 20).map((row) => ({
      key: row.key,
      label: formatText(row.invoiceNo),
      value: row.unshippedPairCount || 0,
    })),
    [unshippedInvoiceRows],
  );
  const modalTitleMap: Record<StatisticModalType, string> = {
    total: "创建订单双数趋势",
    shipped: "出货对数趋势",
    unshipped: "未出货发票排序",
    styles: "款号数量排名",
  };
  const trendSource = activeStatistic === "total"
    ? statistics?.orderCreatedTrend || []
    : activeStatistic === "shipped"
      ? statistics?.shippedPairsTrend || []
      : [];
  const trendChartData = useMemo(
    () => buildTrendChartData(trendSource, timeGranularity),
    [timeGranularity, trendSource],
  );
  const trendUnit = "双";
  const trendTooltipName = activeStatistic === "total" ? "创建订单双数" : "出货对数";
  const orderReferenceColumns = useMemo<ColumnsType<DevelopmentNoOrderReference>>(
    () => [
      {
        title: "发票流水号",
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
      {
        title: "已出货",
        dataIndex: "shippedPairCount",
        key: "shippedPairCount",
        align: "right" as const,
        render: (value?: number) => `${formatCount(value)} 双`,
      },
      {
        title: "未出货",
        dataIndex: "unshippedPairCount",
        key: "unshippedPairCount",
        align: "right" as const,
        render: (value?: number) => `${formatCount(value)} 双`,
      },
    ],
    [],
  );

  const barChartConfig = useMemo<BarConfig>(() => ({
    data: unshippedChartData,
    xField: "label",
    yField: "value",
    height: Math.max(300, unshippedChartData.length * 34),
    autoFit: true,
    scale: {
      y: { nice: true },
    },
    axis: {
      x: {
        title: false,
        labelFormatter: (value: string) => truncateText(value, 14),
      },
      y: {
        title: false,
        labelFormatter: (value: number) => formatCount(value),
      },
    },
    label: {
      text: (datum: BarChartDatum) => `${formatCount(datum.value)} 双`,
      position: "right",
      style: {
        fill: "#172033",
        fontSize: 12,
      },
    },
    tooltip: {
      title: "label",
      items: [{ field: "value", name: "未出货双数" }],
    },
    style: {
      fill: "#f97316",
      radiusTopRight: 4,
      radiusBottomRight: 4,
    },
  }), [unshippedChartData]);

  const trendColumnConfig = useMemo<ColumnConfig>(() => ({
    data: trendChartData,
    xField: "label",
    yField: "value",
    height: 340,
    autoFit: true,
    scale: {
      y: { nice: true },
    },
    axis: {
      x: {
        title: false,
        labelFormatter: (value: string) => truncateText(value, timeGranularity === "day" ? 10 : 12),
      },
      y: {
        title: false,
        labelFormatter: (value: number) => formatCount(value),
      },
    },
    label: {
      text: (datum: TrendChartDatum) => `${formatCount(datum.value)}${trendUnit}`,
      position: "top",
      style: {
        fill: "#172033",
        fontSize: 12,
      },
    },
    tooltip: {
      title: "label",
      items: [{ field: "value", name: trendTooltipName }],
    },
    style: {
      fill: activeStatistic === "total" ? "#2f6df6" : "#0f766e",
      radiusTopLeft: 4,
      radiusTopRight: 4,
    },
  }), [activeStatistic, timeGranularity, trendChartData, trendTooltipName, trendUnit]);

  const rankingColumns = useMemo<ColumnsType<StyleRankingRow>>(
    () => [
      { title: "排名", dataIndex: "rank", key: "rank", width: 72 },
      {
        title: "款号",
        dataIndex: "label",
        key: "label",
        ellipsis: true,
      },
      {
        title: "总双数",
        dataIndex: "pairCount",
        key: "pairCount",
        align: "right" as const,
        render: (value?: number) => `${formatCount(value)} 双`,
      },
      {
        title: "已出货",
        dataIndex: "shippedPairCount",
        key: "shippedPairCount",
        align: "right" as const,
        render: (value?: number) => `${formatCount(value)} 双`,
      },
      {
        title: "未出货",
        dataIndex: "unshippedPairCount",
        key: "unshippedPairCount",
        align: "right" as const,
        render: (value?: number) => `${formatCount(value)} 双`,
      },
      {
        title: "发票",
        dataIndex: "invoiceCount",
        key: "invoiceCount",
        align: "right" as const,
        render: (value?: number, row?: StyleRankingRow) => (
          <Button
            size="small"
            type="link"
            icon={<FileSearchOutlined />}
            disabled={!value}
            onClick={(event) => {
              event.stopPropagation();
              if (row) {
                setOrderReferenceNode(row.node);
              }
            }}
          >
            {value || 0}
          </Button>
        ),
      },
    ],
    [],
  );

  const unshippedInvoiceColumns = useMemo<ColumnsType<UnshippedInvoiceRow>>(
    () => [
      { title: "排名", dataIndex: "rank", key: "rank", width: 72 },
      {
        title: "发票流水号",
        dataIndex: "invoiceNo",
        key: "invoiceNo",
        ellipsis: true,
        render: (value?: string) => formatText(value),
      },
      {
        title: "创建订单时间",
        dataIndex: "createdAt",
        key: "createdAt",
        width: 128,
        render: (value?: string) => formatDate(value),
      },
      {
        title: "距今",
        dataIndex: "pendingDays",
        key: "pendingDays",
        width: 96,
        align: "right" as const,
        render: (value?: number) => formatPendingDays(value),
      },
      {
        title: "未出货",
        dataIndex: "unshippedPairCount",
        key: "unshippedPairCount",
        align: "right" as const,
        render: (value?: number) => `${formatCount(value)} 双`,
      },
      {
        title: "已出货",
        dataIndex: "shippedPairCount",
        key: "shippedPairCount",
        align: "right" as const,
        render: (value?: number) => `${formatCount(value)} 双`,
      },
      {
        title: "总双数",
        dataIndex: "pairCount",
        key: "pairCount",
        align: "right" as const,
        render: (value?: number) => `${formatCount(value)} 双`,
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
            <button className="statistics-metric statistics-metric-button" type="button" onClick={() => openStatistic("total")}>
              <Statistic title="鞋子总双数" value={statistics?.totalPairs || 0} suffix="双" />
            </button>
            <button className="statistics-metric statistics-metric-button" type="button" onClick={() => openStatistic("shipped")}>
              <Statistic title="已出货对数" value={statistics?.shippedPairs || 0} suffix="双" />
            </button>
            <button className="statistics-metric statistics-metric-button" type="button" onClick={() => openStatistic("unshipped")}>
              <Statistic title="未出货对数" value={statistics?.unshippedPairs || 0} suffix="双" />
            </button>
            <button className="statistics-metric statistics-metric-button" type="button" onClick={() => openStatistic("styles")}>
              <Statistic title="款号数量" value={statistics?.styleCount || 0} />
            </button>
          </div>
        </>
      )}
      <Modal
        open={!!activeStatistic}
        title={activeStatistic ? modalTitleMap[activeStatistic] : ""}
        width={activeStatistic === "styles" ? 860 : 920}
        footer={null}
        onCancel={() => setActiveStatistic(null)}
        destroyOnClose
      >
        {activeStatistic && (activeStatistic === "total" || activeStatistic === "shipped") ? (
          <>
            <div className="statistics-modal-controls">
              <Segmented
                value={timeGranularity}
                onChange={(value) => setTimeGranularity(value as TimeGranularity)}
                options={[
                  { label: "年", value: "year" },
                  { label: "月", value: "month" },
                  { label: "日", value: "day" },
                ]}
              />
            </div>
            {trendChartData.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据" />
            ) : (
              <div className="statistics-modal-chart">
                <Column {...trendColumnConfig} />
              </div>
            )}
          </>
        ) : null}
        {activeStatistic === "unshipped" ? (
          unshippedInvoiceRows.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据" />
          ) : (
            <>
              <div className="statistics-modal-chart">
                <Bar {...barChartConfig} />
              </div>
              <Table<UnshippedInvoiceRow>
                rowKey="key"
                columns={unshippedInvoiceColumns}
                dataSource={unshippedInvoiceRows}
                pagination={unshippedInvoiceRows.length > 10 ? { pageSize: 10, showSizeChanger: false } : false}
                size="small"
                className="statistics-order-reference-table"
              />
            </>
          )
        ) : null}
        {activeStatistic === "styles" ? (
          <Table<StyleRankingRow>
            rowKey="key"
            columns={rankingColumns}
            dataSource={activeRows}
            pagination={activeRows.length > 10 ? { pageSize: 10, showSizeChanger: false } : false}
            size="small"
            className="statistics-ranking-table"
            onRow={(record) => ({
              onClick: () => setOrderReferenceNode(record.node),
            })}
          />
        ) : null}
      </Modal>
      <Modal
        open={!!orderReferenceNode}
        title={
          orderReferenceNode
            ? `发票流水号：${orderReferenceNode.fullDevelopmentNo || orderReferenceNode.label}`
            : "发票流水号"
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
