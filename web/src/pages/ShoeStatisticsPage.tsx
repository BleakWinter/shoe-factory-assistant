import { Column, type ColumnConfig } from "@ant-design/charts";
import { FileSearchOutlined, ReloadOutlined } from "@ant-design/icons";
import { App, Button, Empty, Modal, Segmented, Skeleton, Statistic, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import { fetchOrderStatistics } from "../api/statisticsApi";
import type {
  DevelopmentNoOrderReference,
  DevelopmentNoStatisticNode,
  OrderStatistics,
  ShippedOrderStatistic,
  StatisticsTimePoint,
  UnpricedShippedStyle,
  UnpricedUnshippedStyle,
  UnshippedInvoiceStatistic,
} from "../types/order";

function formatCount(value?: number) {
  return new Intl.NumberFormat("zh-CN").format(value || 0);
}

function formatAmount(value?: number) {
  return new Intl.NumberFormat("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value || 0);
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

type StatisticModalType = "total" | "shipped" | "payment" | "unshipped" | "styles";
type TimeGranularity = "year" | "month" | "day";
type UnshippedSortMode = "pending" | "pairs";

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

interface UnpricedShippedStyleRow {
  key: string;
  developmentNo: string;
  periodKey: string;
  periodLabel: string;
  shippedPairs: number;
}

interface ShippedOrderStatisticRow {
  key: string;
  orderNo: string;
  developmentNo: string;
  shippedPairs: number;
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
      rank: index + 1,
    }));
}

function sortUnshippedInvoiceRows(rows: UnshippedInvoiceRow[], mode: UnshippedSortMode) {
  return [...rows]
    .sort((left, right) => {
      const pendingDiff = (right.pendingDays || 0) - (left.pendingDays || 0);
      const unshippedDiff = (right.unshippedPairCount || 0) - (left.unshippedPairCount || 0);
      const leftTime = parseDate(left.createdAt)?.getTime() ?? Number.POSITIVE_INFINITY;
      const rightTime = parseDate(right.createdAt)?.getTime() ?? Number.POSITIVE_INFINITY;
      const timeDiff = leftTime - rightTime;
      const invoiceDiff = formatText(left.invoiceNo).localeCompare(formatText(right.invoiceNo), "zh-CN", { numeric: true });

      if (mode === "pairs") {
        return unshippedDiff || pendingDiff || timeDiff || invoiceDiff;
      }
      return pendingDiff || timeDiff || unshippedDiff || invoiceDiff;
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

function buildUnpricedShippedStyleRows(
  items: UnpricedShippedStyle[],
  granularity: TimeGranularity,
): UnpricedShippedStyleRow[] {
  const rowsByKey = new Map<string, UnpricedShippedStyleRow>();
  for (const item of items) {
    const developmentNo = formatText(item.developmentNo);
    const periodKey = item.shippingDate ? getPeriodKey(item.shippingDate, granularity) : "unknown";
    const key = `${periodKey}::${developmentNo}`;
    const row = rowsByKey.get(key);
    if (row) {
      row.shippedPairs += item.shippedPairs || 0;
      continue;
    }
    rowsByKey.set(key, {
      key,
      developmentNo,
      periodKey,
      periodLabel: periodKey === "unknown" ? "-" : getPeriodLabel(periodKey, granularity),
      shippedPairs: item.shippedPairs || 0,
    });
  }
  return [...rowsByKey.values()].sort((left, right) => {
    if (left.periodKey === "unknown") {
      return right.periodKey === "unknown" ? right.shippedPairs - left.shippedPairs : 1;
    }
    if (right.periodKey === "unknown") {
      return -1;
    }
    return right.periodKey.localeCompare(left.periodKey)
      || right.shippedPairs - left.shippedPairs
      || left.developmentNo.localeCompare(right.developmentNo, "zh-CN", { numeric: true });
  });
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

function buildShippedOrderStatisticRows(
  items: ShippedOrderStatistic[],
  granularity: TimeGranularity,
  selectedPeriod: string | null,
): ShippedOrderStatisticRow[] {
  if (!selectedPeriod) {
    return [];
  }
  const rowsByKey = new Map<string, ShippedOrderStatisticRow>();
  for (const item of items) {
    if (!item.shippingDate || getPeriodKey(item.shippingDate, granularity) !== selectedPeriod) {
      continue;
    }
    const orderNo = formatText(item.orderNo);
    const developmentNo = formatText(item.developmentNo);
    const key = `${orderNo}::${developmentNo}`;
    const row = rowsByKey.get(key);
    if (row) {
      row.shippedPairs += item.shippedPairs || 0;
      continue;
    }
    rowsByKey.set(key, {
      key,
      orderNo,
      developmentNo,
      shippedPairs: item.shippedPairs || 0,
    });
  }
  return [...rowsByKey.values()].sort(
    (left, right) => left.orderNo.localeCompare(right.orderNo, "zh-CN", { numeric: true })
      || left.developmentNo.localeCompare(right.developmentNo, "zh-CN", { numeric: true }),
  );
}

export default function ShoeStatisticsPage() {
  const { message } = App.useApp();
  const [statistics, setStatistics] = useState<OrderStatistics | null>(null);
  const [loading, setLoading] = useState(false);
  const [activeStatistic, setActiveStatistic] = useState<StatisticModalType | null>(null);
  const [timeGranularity, setTimeGranularity] = useState<TimeGranularity>("month");
  const [unshippedSortMode, setUnshippedSortMode] = useState<UnshippedSortMode>("pending");
  const [orderReferenceNode, setOrderReferenceNode] = useState<DevelopmentNoStatisticNode | null>(null);
  const [selectedShippedPeriod, setSelectedShippedPeriod] = useState<string | null>(null);

  const loadStatistics = useCallback(async () => {
    setLoading(true);
    try {
      const next = await fetchOrderStatistics();
      setStatistics(next);
      setActiveStatistic(null);
      setTimeGranularity("month");
      setUnshippedSortMode("pending");
      setOrderReferenceNode(null);
      setSelectedShippedPeriod(null);
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
    setSelectedShippedPeriod(null);
    if (type === "total" || type === "shipped" || type === "payment") {
      setTimeGranularity("month");
    }
    if (type === "unshipped") {
      setUnshippedSortMode("pending");
    }
  }, []);

  const styleRows = useMemo(
    () => buildStyleRankingRows(statistics?.developmentNoTree || []),
    [statistics?.developmentNoTree],
  );
  const unshippedInvoiceBaseRows = useMemo(
    () => buildUnshippedInvoiceRows(statistics?.unshippedInvoiceStatistics || []),
    [statistics?.unshippedInvoiceStatistics],
  );
  const unshippedInvoiceRows = useMemo(
    () => sortUnshippedInvoiceRows(unshippedInvoiceBaseRows, unshippedSortMode),
    [unshippedInvoiceBaseRows, unshippedSortMode],
  );
  const unshippedRankingRows = useMemo(() => unshippedInvoiceRows.slice(0, 12), [unshippedInvoiceRows]);
  const unpricedShippedStyleRows = useMemo(
    () => buildUnpricedShippedStyleRows(statistics?.unpricedShippedStyles || [], timeGranularity),
    [statistics?.unpricedShippedStyles, timeGranularity],
  );
  const maxUnshippedPairCount = useMemo(
    () => Math.max(1, ...unshippedRankingRows.map((row) => row.unshippedPairCount || 0)),
    [unshippedRankingRows],
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
  const modalTitleMap: Record<StatisticModalType, string> = {
    total: "创建订单双数趋势",
    shipped: "出货对数趋势",
    payment: "出货总货款趋势",
    unshipped: "未出货发票排序",
    styles: "款号数量排名",
  };
  const unshippedSortTitle = unshippedSortMode === "pending"
    ? "按最久没有出货排序"
    : "按未出货双数排序";
  const unshippedSortNote = unshippedSortMode === "pending"
    ? "条形长度表示未出货双数，排序优先看等待天数"
    : "条形长度按最高未出货双数归一";
  const trendSource = activeStatistic === "total"
    ? statistics?.orderCreatedTrend || []
    : activeStatistic === "shipped"
      ? statistics?.shippedPairsTrend || []
      : activeStatistic === "payment"
        ? statistics?.paymentAmountTrend || []
      : [];
  const trendChartData = useMemo(
    () => buildTrendChartData(trendSource, timeGranularity),
    [timeGranularity, trendSource],
  );
  const shippedOrderStatisticRows = useMemo(
    () => buildShippedOrderStatisticRows(
      statistics?.shippedOrderStatistics || [],
      timeGranularity,
      selectedShippedPeriod,
    ),
    [selectedShippedPeriod, statistics?.shippedOrderStatistics, timeGranularity],
  );
  const selectedShippedPairs = useMemo(
    () => shippedOrderStatisticRows.reduce((total, row) => total + row.shippedPairs, 0),
    [shippedOrderStatisticRows],
  );
  const isPaymentTrend = activeStatistic === "payment";
  const trendUnit = isPaymentTrend ? "元" : "双";
  const trendTooltipName = activeStatistic === "total"
    ? "创建订单双数"
    : activeStatistic === "payment"
      ? "出货总货款"
      : "出货对数";
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
  const shippedOrderStatisticColumns = useMemo<ColumnsType<ShippedOrderStatisticRow>>(
    () => [
      {
        title: "订单流水号",
        dataIndex: "orderNo",
        key: "orderNo",
        ellipsis: true,
      },
      {
        title: "开发款号",
        dataIndex: "developmentNo",
        key: "developmentNo",
        ellipsis: true,
      },
      {
        title: "出货双数",
        dataIndex: "shippedPairs",
        key: "shippedPairs",
        width: 120,
        align: "right" as const,
        render: (value?: number) => `${formatCount(value)} 双`,
      },
    ],
    [],
  );

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
        labelFormatter: (value: number) => isPaymentTrend ? `¥${formatCount(value)}` : formatCount(value),
      },
    },
    label: {
      text: (datum: TrendChartDatum) => isPaymentTrend
        ? `¥${formatAmount(datum.value)}`
        : `${formatCount(datum.value)}${trendUnit}`,
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
      fill: activeStatistic === "total" ? "#2f6df6" : activeStatistic === "payment" ? "#b45309" : "#0f766e",
      radiusTopLeft: 4,
      radiusTopRight: 4,
      cursor: activeStatistic === "shipped" ? "pointer" : "default",
    },
    onReady: (chart) => {
      if (activeStatistic !== "shipped") {
        return;
      }
      chart.on("element:click", (event: { data?: { data?: TrendChartDatum } }) => {
        const datum = event?.data?.data;
        if (datum?.key) {
          setSelectedShippedPeriod(datum.key);
        }
      });
    },
  }), [activeStatistic, isPaymentTrend, timeGranularity, trendChartData, trendTooltipName, trendUnit]);

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
  const unpricedPeriodColumnTitle = timeGranularity === "year"
    ? "出货年份"
    : timeGranularity === "month"
      ? "出货月份"
      : "出货日期";
  const unpricedStyleColumns = useMemo<ColumnsType<UnpricedShippedStyleRow>>(
    () => [
      {
        title: "序号",
        key: "rank",
        width: 58,
        align: "center" as const,
        render: (_value, _record, index) => index + 1,
      },
      {
        title: "开发编号",
        dataIndex: "developmentNo",
        key: "developmentNo",
        render: (value?: string) => formatText(value),
      },
      {
        title: unpricedPeriodColumnTitle,
        dataIndex: "periodLabel",
        key: "periodLabel",
        width: timeGranularity === "day" ? 126 : 112,
      },
      {
        title: "未计价出货双数",
        dataIndex: "shippedPairs",
        key: "shippedPairs",
        width: 144,
        align: "right" as const,
        render: (value?: number) => `${formatCount(value)} 双`,
      },
    ],
    [timeGranularity, unpricedPeriodColumnTitle],
  );
  const unpricedUnshippedStyleColumns = useMemo<ColumnsType<UnpricedUnshippedStyle>>(
    () => [
      {
        title: "序号",
        key: "rank",
        width: 72,
        align: "center" as const,
        render: (_value, _record, index) => index + 1,
      },
      {
        title: "开发编号",
        dataIndex: "developmentNo",
        key: "developmentNo",
        render: (value?: string) => formatText(value),
      },
      {
        title: "待出货双数",
        dataIndex: "unshippedPairs",
        key: "unshippedPairs",
        width: 140,
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
          <Typography.Text type="secondary">按订单明细汇总鞋子双数、款号和出货货款。</Typography.Text>
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
            <button className="statistics-metric statistics-metric-button" type="button" onClick={() => openStatistic("payment")}>
              <Statistic
                title="出货总货款"
                value={statistics?.totalPaymentAmount || 0}
                precision={2}
                prefix="¥"
              />
              <Typography.Text type={statistics?.unpricedShippedPairs ? "warning" : "secondary"} className="statistics-metric-note">
                {statistics?.unpricedShippedPairs
                  ? `${formatCount(statistics.unpricedShippedPairs)} 双未配置单价，暂未计入`
                  : "全部已出货明细均已计价"}
              </Typography.Text>
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
        width={activeStatistic === "payment"
          ? 1120
          : activeStatistic === "unshipped"
            ? 980
            : activeStatistic === "styles"
              ? 860
              : 920}
        footer={null}
        onCancel={() => {
          setActiveStatistic(null);
          setSelectedShippedPeriod(null);
        }}
        destroyOnHidden
      >
        {activeStatistic && (activeStatistic === "total" || activeStatistic === "shipped" || activeStatistic === "payment") ? (
          <>
            <div className="statistics-modal-controls">
              {activeStatistic === "payment" ? (
                <Typography.Text type="secondary" className="statistics-payment-note">
                  按出货日期和当前价格配置计算
                  {statistics?.unpricedShippedPairs
                    ? `，另有 ${formatCount(statistics.unpricedShippedPairs)} 双未配置单价`
                    : ""}
                </Typography.Text>
              ) : activeStatistic === "shipped" ? (
                <Typography.Text type="secondary" className="statistics-payment-note">
                  点击柱状图，查看对应的订单流水号、开发款号和出货双数
                </Typography.Text>
              ) : null}
              <Segmented
                value={timeGranularity}
                onChange={(value) => {
                  setTimeGranularity(value as TimeGranularity);
                  setSelectedShippedPeriod(null);
                }}
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
            {activeStatistic === "payment" ? (
              <div className="statistics-unpriced-section">
                <Typography.Title level={5}>
                  未配置单价的款号
                </Typography.Title>
                <div className="statistics-unpriced-grid">
                  <div className="statistics-unpriced-panel">
                    <div className="statistics-unpriced-heading">
                      <Typography.Text strong>已出货</Typography.Text>
                      <Typography.Text type="warning">
                        {formatCount(statistics?.unpricedShippedPairs)} 双未计入货款
                      </Typography.Text>
                    </div>
                    {unpricedShippedStyleRows.length ? (
                      <Table<UnpricedShippedStyleRow>
                        rowKey="key"
                        columns={unpricedStyleColumns}
                        dataSource={unpricedShippedStyleRows}
                        pagination={unpricedShippedStyleRows.length > 6
                          ? { pageSize: 6, showSizeChanger: false }
                          : false}
                        size="small"
                      />
                    ) : (
                      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有缺价的已出货款号" />
                    )}
                  </div>
                  <div className="statistics-unpriced-panel">
                    <div className="statistics-unpriced-heading">
                      <Typography.Text strong>未出货</Typography.Text>
                      <Typography.Text type="secondary">
                        {formatCount(statistics?.unpricedUnshippedPairs)} 双待出货，暂无出货日期
                      </Typography.Text>
                    </div>
                    {statistics?.unpricedUnshippedStyles?.length ? (
                      <Table<UnpricedUnshippedStyle>
                        rowKey="developmentNo"
                        columns={unpricedUnshippedStyleColumns}
                        dataSource={statistics.unpricedUnshippedStyles}
                        pagination={statistics.unpricedUnshippedStyles.length > 6
                          ? { pageSize: 6, showSizeChanger: false }
                          : false}
                        size="small"
                      />
                    ) : (
                      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有缺价的未出货款号" />
                    )}
                  </div>
                </div>
              </div>
            ) : null}
          </>
        ) : null}
        {activeStatistic === "unshipped" ? (
          unshippedInvoiceRows.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据" />
          ) : (
            <>
              <div className="unshipped-ranking-panel">
                <div className="unshipped-ranking-heading">
                  <div>
                    <Typography.Text strong>{unshippedSortTitle}</Typography.Text>
                    <Typography.Text type="secondary">
                      显示前 {unshippedRankingRows.length} 张，共 {unshippedInvoiceRows.length} 张未出货发票
                    </Typography.Text>
                  </div>
                  <div className="unshipped-ranking-actions">
                    <Segmented
                      size="small"
                      value={unshippedSortMode}
                      onChange={(value) => setUnshippedSortMode(value as UnshippedSortMode)}
                      options={[
                        { label: "最久未出货", value: "pending" },
                        { label: "未出货双数", value: "pairs" },
                      ]}
                    />
                    <Typography.Text type="secondary">
                      {unshippedSortNote}
                    </Typography.Text>
                  </div>
                </div>

                <div className="unshipped-ranking-list">
                  {unshippedRankingRows.map((row) => {
                    const unshippedPairCount = row.unshippedPairCount || 0;
                    const percent = Math.max(4, Math.round((unshippedPairCount / maxUnshippedPairCount) * 100));

                    return (
                      <div className="unshipped-ranking-row" key={row.key}>
                        <span className={`unshipped-ranking-rank ${row.rank <= 3 ? "unshipped-ranking-rank-top" : ""}`}>
                          {row.rank}
                        </span>

                        <div className="unshipped-ranking-main">
                          <div className="unshipped-ranking-line">
                            <Typography.Text strong ellipsis className="unshipped-ranking-invoice">
                              {formatText(row.invoiceNo)}
                            </Typography.Text>
                            <span className="unshipped-ranking-value">
                              {formatCount(unshippedPairCount)} 双
                            </span>
                          </div>

                          <div className="unshipped-ranking-track">
                            <div className="unshipped-ranking-bar" style={{ width: `${percent}%` }} />
                          </div>

                          <div className="unshipped-ranking-meta">
                            <span>创建：{formatDate(row.createdAt)}</span>
                            <span>距今：{formatPendingDays(row.pendingDays)}</span>
                            <span>已出货：{formatCount(row.shippedPairCount)} 双</span>
                            <span>总双数：{formatCount(row.pairCount)} 双</span>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              <Typography.Title level={5} className="unshipped-table-title">
                全部未出货发票
              </Typography.Title>
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
        open={activeStatistic === "shipped" && !!selectedShippedPeriod}
        title={selectedShippedPeriod
          ? `${getPeriodLabel(selectedShippedPeriod, timeGranularity)}出货明细`
          : "出货明细"}
        width={720}
        footer={null}
        onCancel={() => setSelectedShippedPeriod(null)}
        destroyOnHidden
      >
        <Typography.Paragraph type="secondary">
          共 {formatCount(shippedOrderStatisticRows.length)} 条，合计 {formatCount(selectedShippedPairs)} 双
        </Typography.Paragraph>
        <Table<ShippedOrderStatisticRow>
          rowKey="key"
          columns={shippedOrderStatisticColumns}
          dataSource={shippedOrderStatisticRows}
          pagination={shippedOrderStatisticRows.length > 10
            ? { pageSize: 10, showSizeChanger: false }
            : false}
          size="small"
          className="statistics-order-reference-table"
          locale={{ emptyText: "该时间段暂无出货明细" }}
        />
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
        destroyOnHidden
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
