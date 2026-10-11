import { Column, type ColumnConfig } from "@ant-design/charts";
import { FileSearchOutlined, ReloadOutlined } from "@ant-design/icons";
import { App, Button, Empty, Modal, Segmented, Select, Skeleton, Space, Statistic, Table, Tag, Typography } from "antd";
import { useCustomers } from "../hooks/useCustomers";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchOrderStatistics } from "../api/statisticsApi";
import type {
  DevelopmentNoOrderReference,
  DevelopmentNoStatisticNode,
  OrderStatistics,
  ShippedOrderStatistic,
  StatisticsTimePoint,
  UnpricedUnshippedStyle,
  UnshippedStyleStatistic,
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

function parseDate(value?: string | Date) {
  if (!value) {
    return null;
  }
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : value;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function formatDate(value?: string | Date) {
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

function addOneCalendarMonth(value?: string) {
  const date = parseDate(value);
  if (!date) {
    return null;
  }
  const targetYear = date.getMonth() === 11 ? date.getFullYear() + 1 : date.getFullYear();
  const targetMonth = (date.getMonth() + 1) % 12;
  const lastTargetDay = new Date(targetYear, targetMonth + 1, 0).getDate();
  return new Date(targetYear, targetMonth, Math.min(date.getDate(), lastTargetDay));
}

function getRemainingDays(value?: string | Date) {
  const date = parseDate(value);
  if (!date) {
    return undefined;
  }
  const deadline = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  return Math.round((deadline - today) / 86_400_000);
}

function formatCountdown(value?: number) {
  if (value === undefined) {
    return "日期待确认";
  }
  if (value < 0) {
    return `已逾期 ${formatCount(Math.abs(value))} 天`;
  }
  if (value === 0) {
    return "今天出货";
  }
  return `还剩 ${formatCount(value)} 天`;
}

function getCountdownTone(value?: number) {
  if (value === undefined) {
    return "unknown";
  }
  if (value < 0) {
    return "overdue";
  }
  if (value <= 7) {
    return "urgent";
  }
  if (value <= 15) {
    return "soon";
  }
  return "normal";
}

type StatisticModalType = "total" | "shipped" | "payment" | "unshipped" | "styles";
type TimeGranularity = "year" | "month" | "day";
type UnshippedSortMode = "deadline" | "pairs";

interface TrendChartDatum {
  key: string;
  label: string;
  value: number;
}

interface TrendChartClickEvent {
  data?: {
    data?: TrendChartDatum | TrendChartDatum[];
  };
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

interface UnshippedStyleRow extends UnshippedStyleStatistic {
  rank: number;
  key: string;
  countdownDate?: Date;
  remainingDays?: number;
  usesDefaultDeliveryDate: boolean;
  countdownTone: ReturnType<typeof getCountdownTone>;
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

function buildUnshippedStyleRows(items: UnshippedStyleStatistic[]): UnshippedStyleRow[] {
  return items
    .filter((item) => (item.unshippedPairCount || 0) > 0)
    .map((item, index) => {
      const deliveryDate = parseDate(item.deliveryDate);
      const countdownDate = deliveryDate || addOneCalendarMonth(item.createdAt) || undefined;
      const remainingDays = getRemainingDays(countdownDate);
      return {
        ...item,
        key: [item.orderId || "order", item.invoiceNo || "invoice", item.developmentNo || "style", index].join("-"),
        countdownDate,
        remainingDays,
        usesDefaultDeliveryDate: item.defaultDeliveryDate ?? !deliveryDate,
        countdownTone: getCountdownTone(remainingDays),
        rank: index + 1,
      };
    });
}

function sortUnshippedStyleRows(rows: UnshippedStyleRow[], mode: UnshippedSortMode) {
  return [...rows]
    .sort((left, right) => {
      const deadlineDiff = (left.countdownDate?.getTime() ?? Number.POSITIVE_INFINITY)
        - (right.countdownDate?.getTime() ?? Number.POSITIVE_INFINITY);
      const unshippedDiff = (right.unshippedPairCount || 0) - (left.unshippedPairCount || 0);
      const leftTime = parseDate(left.createdAt)?.getTime() ?? Number.POSITIVE_INFINITY;
      const rightTime = parseDate(right.createdAt)?.getTime() ?? Number.POSITIVE_INFINITY;
      const timeDiff = leftTime - rightTime;
      const invoiceDiff = formatText(left.invoiceNo).localeCompare(formatText(right.invoiceNo), "zh-CN", { numeric: true });
      const styleDiff = formatText(left.developmentNo).localeCompare(
        formatText(right.developmentNo),
        "zh-CN",
        { numeric: true },
      );

      if (mode === "pairs") {
        return unshippedDiff || deadlineDiff || timeDiff || invoiceDiff || styleDiff;
      }
      return deadlineDiff || unshippedDiff || timeDiff || invoiceDiff || styleDiff;
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
  const navigate = useNavigate();
  const customers=useCustomers();
  const [customerId,setCustomerId]=useState<number>();
  const requestSequence=useRef(0);
  const { message } = App.useApp();
  const [statistics, setStatistics] = useState<OrderStatistics | null>(null);
  const [loading, setLoading] = useState(false);
  const [activeStatistic, setActiveStatistic] = useState<StatisticModalType | null>(null);
  const [timeGranularity, setTimeGranularity] = useState<TimeGranularity>("month");
  const [unshippedSortMode, setUnshippedSortMode] = useState<UnshippedSortMode>("deadline");
  const [orderReferenceNode, setOrderReferenceNode] = useState<DevelopmentNoStatisticNode | null>(null);
  const [selectedShippedPeriod, setSelectedShippedPeriod] = useState<string | null>(null);

  const loadStatistics = useCallback(async () => {
    const sequence=++requestSequence.current;
    setLoading(true);
    setStatistics(null);
    try {
      const next = await fetchOrderStatistics(customerId);
      if(sequence!==requestSequence.current)return;
      setStatistics(next);
      setActiveStatistic(null);
      setTimeGranularity("month");
      setUnshippedSortMode("deadline");
      setOrderReferenceNode(null);
      setSelectedShippedPeriod(null);
    } catch (error) {
      if(sequence!==requestSequence.current)return;
      setStatistics(null);
      message.error(error instanceof Error ? error.message : "统计数据加载失败");
    } finally {
      if(sequence===requestSequence.current)setLoading(false);
    }
  }, [message,customerId]);

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
      setUnshippedSortMode("deadline");
    }
  }, []);

  const styleRows = useMemo(
    () => buildStyleRankingRows(statistics?.developmentNoTree || []),
    [statistics?.developmentNoTree],
  );
  const unshippedStyleBaseRows = useMemo(
    () => buildUnshippedStyleRows(statistics?.unshippedStyleStatistics || []),
    [statistics?.unshippedStyleStatistics],
  );
  const unshippedStyleRows = useMemo(
    () => sortUnshippedStyleRows(unshippedStyleBaseRows, unshippedSortMode),
    [unshippedSortMode, unshippedStyleBaseRows],
  );
  const unshippedRankingRows = useMemo(() => unshippedStyleRows.slice(0, 12), [unshippedStyleRows]);
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
    unshipped: "未出货款出货倒计时",
    styles: "款号数量排名",
  };
  const unshippedSortTitle = unshippedSortMode === "deadline"
    ? "按出货倒计时排序"
    : "按未出货双数排序";
  const unshippedSortNote = unshippedSortMode === "deadline"
    ? "优先显示已逾期和临近出货的款"
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
      chart.on("element:click", (event: TrendChartClickEvent) => {
        const eventData = event?.data?.data;
        const datum = Array.isArray(eventData) ? eventData[0] : eventData;
        const periodKey = datum?.key
          || trendChartData.find((item) => item.label === datum?.label)?.key;
        if (periodKey) {
          setSelectedShippedPeriod(periodKey);
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

  const unshippedStyleColumns = useMemo<ColumnsType<UnshippedStyleRow>>(
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
        title: "款号",
        dataIndex: "developmentNo",
        key: "developmentNo",
        width: 150,
        ellipsis: true,
        render: (value?: string) => formatText(value),
      },
      {
        title: "出货日期",
        key: "deliveryDate",
        width: 150,
        render: (_value, row) => (
          <span className="unshipped-table-delivery-date">
            {formatDate(row.countdownDate)}
            {row.usesDefaultDeliveryDate ? <Tag color="default">默认</Tag> : null}
          </span>
        ),
      },
      {
        title: "倒计时",
        dataIndex: "remainingDays",
        key: "remainingDays",
        width: 136,
        render: (value?: number) => (
          <span className={`shipping-countdown shipping-countdown-${getCountdownTone(value)}`}>
            {formatCountdown(value)}
          </span>
        ),
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
        <Space><Select allowClear placeholder="全部客户" value={customerId} onChange={setCustomerId}
          options={customers.map(c=>({label:c.name,value:c.id}))} style={{width:180}}/>
        <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadStatistics()}>
          刷新
        </Button></Space>
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
                <Column
                  key={`${activeStatistic}-${timeGranularity}`}
                  {...trendColumnConfig}
                />
              </div>
            )}
            {activeStatistic === "payment" ? (
              <div className="statistics-unpriced-section">
                <Button onClick={() => navigate(`/unpriced-styles${customerId ? `?customerId=${customerId}` : ""}`)}>
                  按出货月份查看未报价款号
                </Button>
                <Typography.Title level={5}>
                  未出货且未配置单价的款号
                </Typography.Title>
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
            ) : null}
          </>
        ) : null}
        {activeStatistic === "unshipped" ? (
          unshippedStyleRows.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据" />
          ) : (
            <>
              <div className="unshipped-ranking-panel">
                <div className="unshipped-ranking-heading">
                  <div>
                    <Typography.Text strong>{unshippedSortTitle}</Typography.Text>
                    <Typography.Text type="secondary">
                      显示前 {unshippedRankingRows.length} 款，共 {unshippedStyleRows.length} 个未出货款
                    </Typography.Text>
                    <Typography.Text type="secondary" className="unshipped-default-rule">
                      未填写出货日期时，按订单创建日期顺延 1 个月计算，并标记为“默认”
                    </Typography.Text>
                  </div>
                  <div className="unshipped-ranking-actions">
                    <Segmented
                      size="small"
                      value={unshippedSortMode}
                      onChange={(value) => setUnshippedSortMode(value as UnshippedSortMode)}
                      options={[
                        { label: "出货倒计时", value: "deadline" },
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
                      <div
                        className={`unshipped-ranking-row unshipped-ranking-row-${row.countdownTone}`}
                        key={row.key}
                      >
                        <span className={`unshipped-ranking-rank ${row.rank <= 3 ? "unshipped-ranking-rank-top" : ""}`}>
                          {row.rank}
                        </span>

                        <div className="unshipped-ranking-main">
                          <div className="unshipped-ranking-line">
                            <div className="unshipped-ranking-identity">
                              <Typography.Text strong ellipsis className="unshipped-ranking-invoice">
                                {formatText(row.invoiceNo)}
                              </Typography.Text>
                              <span className="unshipped-ranking-style">
                                款号：{formatText(row.developmentNo)}
                              </span>
                            </div>
                            <div className="unshipped-ranking-status">
                              <span className={`shipping-countdown shipping-countdown-${row.countdownTone}`}>
                                {formatCountdown(row.remainingDays)}
                              </span>
                              <span className="unshipped-ranking-value">
                                {formatCount(unshippedPairCount)} 双
                              </span>
                            </div>
                          </div>

                          <div className="unshipped-ranking-track">
                            <div className="unshipped-ranking-bar" style={{ width: `${percent}%` }} />
                          </div>

                          <div className="unshipped-ranking-meta">
                            <span className="unshipped-ranking-delivery-date">
                              出货日：{formatDate(row.countdownDate)}
                              {row.usesDefaultDeliveryDate ? <em>默认</em> : null}
                            </span>
                            <span>创建：{formatDate(row.createdAt)}</span>
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
                全部未出货款
              </Typography.Title>
              <Table<UnshippedStyleRow>
                rowKey="key"
                columns={unshippedStyleColumns}
                dataSource={unshippedStyleRows}
                pagination={unshippedStyleRows.length > 10 ? { pageSize: 10, showSizeChanger: false } : false}
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
