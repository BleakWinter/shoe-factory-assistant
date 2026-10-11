import { ArrowLeftOutlined, DownOutlined, ReloadOutlined, UpOutlined } from "@ant-design/icons";
import { Alert, Button, Drawer, Empty, Image, Select, Space, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState } from "react";
import type { Key } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { fetchUnpricedMonthDetails, fetchUnpricedMonths } from "../api/reconciliationApi";
import { toAssetUrl } from "../api/orderApi";
import { useCustomers } from "../hooks/useCustomers";
import type { UnpricedMonthDetails, UnpricedMonthSummary, UnpricedQuotationCounts, UnpricedShipmentDetail, UnpricedStyleGroup, UnpricedStyleSummary } from "../types/reconciliation";
import { priceText } from "../utils/quotation";
import "./unpricedStyles.css";

function formatCount(value?: number) {
  return new Intl.NumberFormat("zh-CN").format(value || 0);
}

function formatMonth(month: string) {
  const [year, number] = month.split("-");
  return `${year}年${Number(number)}月`;
}

function formatShippingPeriod(style: UnpricedStyleSummary) {
  const dates = style.details.map(row => row.shippingDate).sort();
  const first = dates[0], last = dates[dates.length - 1];
  return first ? first === last ? first : `${first} ～ ${last}` : "-";
}

const emptyQuotationCounts: UnpricedQuotationCounts = { unquotedCount: 0, draftCount: 0, quotedCount: 0 };

function QuotationProgress({ counts }: { counts?: UnpricedQuotationCounts }) {
  if (!counts) return <Typography.Text type="secondary">报价状态未加载</Typography.Text>;
  return <Space size={[4, 4]} wrap>
    <Tag color="red">未报价 {formatCount(counts.unquotedCount)} 个</Tag>
    <Tag color="gold">报价待完善 {formatCount(counts.draftCount)} 个</Tag>
    <Tag color="blue">已报价待确认 {formatCount(counts.quotedCount)} 个</Tag>
  </Space>;
}

function QuotationState({ style }: { style: UnpricedStyleSummary }) {
  if (style.quotationStatus === "NOT_QUOTED") return <Tag color="red">未报价</Tag>;
  if (style.quotationStatus === "DRAFT") return <Tag color="gold">报价待完善</Tag>;
  if (style.quotationStatus !== "QUOTED") return <Typography.Text type="secondary">报价状态未加载</Typography.Text>;
  return <Space direction="vertical" size={2} className="unpriced-quotation-status">
    <Tag color="blue">{style.latestPricedQuotation?.adoptedAt ? "曾采用 · 当前未定价" : "已报价 · 待客户确认"}</Tag>
    {style.latestQuotation?.finalPrice == null ? <Typography.Text type="secondary">最新版待完善</Typography.Text> : null}
  </Space>;
}

function QuotedPrice({ style }: { style: UnpricedStyleSummary }) {
  const quote = style.latestPricedQuotation;
  if (!quote) return <Typography.Text type="secondary">{style.quotationStatus === "DRAFT" ? "尚未填写最终报价" : "—"}</Typography.Text>;
  const date = quote.quotationDate || quote.createdAt?.slice(0, 10);
  return <Space direction="vertical" size={2}>
    <Typography.Text strong>¥ {priceText(quote.finalPrice)}</Typography.Text>
    <Typography.Text type="secondary">V{quote.versionNo}{date ? ` · ${date}` : ""}</Typography.Text>
  </Space>;
}

export default function UnpricedStylesPage() {
  const navigate = useNavigate();
  const customers = useCustomers();
  const [searchParams, setSearchParams] = useSearchParams();
  const month = searchParams.get("month") || "";
  const customerParam = Number(searchParams.get("customerId"));
  const customerId = Number.isSafeInteger(customerParam) && customerParam > 0 ? customerParam : undefined;
  const [months, setMonths] = useState<UnpricedMonthSummary[]>([]);
  const [details, setDetails] = useState<UnpricedMonthDetails>();
  const [monthsLoading, setMonthsLoading] = useState(true);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [monthsError, setMonthsError] = useState("");
  const [detailsError, setDetailsError] = useState("");
  const [refresh, setRefresh] = useState(0);
  const [expandedKeys, setExpandedKeys] = useState<Key[]>([]);
  const [shipmentStyle, setShipmentStyle] = useState<UnpricedStyleSummary>();

  useEffect(() => {
    let active = true;
    setMonthsLoading(true);
    setMonths([]);
    setMonthsError("");
    fetchUnpricedMonths(customerId)
      .then(result => { if (active) setMonths(result); })
      .catch(error => { if (active) setMonthsError(error instanceof Error ? error.message : "月份汇总加载失败"); })
      .finally(() => { if (active) setMonthsLoading(false); });
    return () => { active = false; };
  }, [customerId, refresh]);

  useEffect(() => {
    let active = true;
    setDetails(undefined);
    setDetailsError("");
    setExpandedKeys([]);
    setShipmentStyle(undefined);
    setDetailsLoading(Boolean(month));
    if (month) {
      fetchUnpricedMonthDetails(month, customerId)
        .then(result => { if (active) setDetails(result); })
        .catch(error => { if (active) setDetailsError(error instanceof Error ? error.message : "待定价款号加载失败"); })
        .finally(() => { if (active) setDetailsLoading(false); });
    }
    return () => { active = false; };
  }, [customerId, month, refresh]);

  function selectMonth(value: string) {
    const next = new URLSearchParams(searchParams);
    if (value) next.set("month", value);
    else next.delete("month");
    setSearchParams(next);
  }

  function toggleGroup(key: string) {
    setExpandedKeys(current => current.includes(key) ? current.filter(item => item !== key) : [...current, key]);
  }

  function openQuotation(style: UnpricedStyleSummary) {
    if (style.latestQuotation) navigate(`/price-configs/quotations/${style.latestQuotation.id}`);
    else if (style.customerId && style.developmentNo && style.quotationStatus === "NOT_QUOTED") {
      navigate(`/price-configs/quotations/new?customerId=${style.customerId}&developmentNo=${encodeURIComponent(style.developmentNo)}`);
    }
  }

  const totals = useMemo(() => months.reduce((result, row) => ({
    count: result.count + row.missingPriceCount,
    quantity: result.quantity + row.shippingQuantity,
    quotationCounts: {
      unquotedCount: result.quotationCounts.unquotedCount + (row.quotationCounts?.unquotedCount || 0),
      draftCount: result.quotationCounts.draftCount + (row.quotationCounts?.draftCount || 0),
      quotedCount: result.quotationCounts.quotedCount + (row.quotationCounts?.quotedCount || 0),
    },
  }), { count: 0, quantity: 0, quotationCounts: emptyQuotationCounts }), [months]);

  const monthColumns: ColumnsType<UnpricedMonthSummary> = [
    { title: "出货月份", dataIndex: "month", width: 160,
      render: (value: string) => <Button type="link" onClick={() => selectMonth(value)}>{formatMonth(value)}</Button> },
    { title: "待定价款号数", dataIndex: "missingPriceCount", width: 140, align: "right", render: value => `${formatCount(value)} 个` },
    { title: "报价进度", dataIndex: "quotationCounts", width: 340, render: counts => <QuotationProgress counts={counts}/> },
    { title: "涉及主款号数", dataIndex: "mainStyleCount", width: 140, align: "right", render: value => `${formatCount(value)} 个` },
    { title: "未计价出货双数", dataIndex: "shippingQuantity", width: 160, align: "right", render: value => `${formatCount(value)} 双` },
    { title: "操作", key: "action", width: 140,
      render: (_, row) => <Button type="link" onClick={() => selectMonth(row.month)}>查看款号</Button> },
  ];

  const detailColumns: ColumnsType<UnpricedShipmentDetail> = [
    { title: "完整款号", dataIndex: "developmentNo", width: 180, render: value => <Typography.Text strong>{value || "未填款号"}</Typography.Text> },
    { title: "客户", dataIndex: "customerName", width: 120 },
    { title: "出货日期", dataIndex: "shippingDate", width: 120 },
    { title: "订单号", dataIndex: "orderNo", width: 140, render: value => value || "-" },
    { title: "出货双数", dataIndex: "shippingQuantity", width: 110, align: "right", render: formatCount },
    { title: "出货单号", dataIndex: "taskNo", width: 150, render: value => value || "-" },
    { title: "颜色 / 材料", dataIndex: "colorMaterial", width: 200, render: value => value || "-" },
    { title: "备注", dataIndex: "remark", width: 160, render: value => value || "-" },
  ];

  const styleColumns: ColumnsType<UnpricedStyleSummary> = [
    { title: "完整款号", dataIndex: "developmentNo", width: 180, render: value => <Typography.Text strong>{value || "未填款号"}</Typography.Text> },
    { title: "鞋图", dataIndex: "imageUrl", width: 90, align: "center",
      render: (value: string | null | undefined, row) => value
        ? <Image className="unpriced-style-image" width={64} height={48} src={toAssetUrl(value)}
            alt={`${row.developmentNo} 鞋图`} preview={{ mask: "查看" }}/>
        : <Typography.Text type="secondary" className="unpriced-image-empty">暂无图片</Typography.Text> },
    { title: "客户", dataIndex: "customerName", width: 100 },
    { title: "报价状态", key: "quotationStatus", width: 185, render: (_, row) => <QuotationState style={row}/> },
    { title: "最近已报价格", key: "quotedPrice", width: 155, render: (_, row) => <QuotedPrice style={row}/> },
    { title: "未计价出货双数", dataIndex: "shippingQuantity", width: 140, align: "right", render: formatCount },
    { title: "出货记录数", key: "shipmentCount", width: 110, align: "right", render: (_, row) => `${formatCount(row.details.length)} 条` },
    { title: "出货日期", key: "shippingPeriod", width: 210, render: (_, row) => formatShippingPeriod(row) },
    { title: "操作", key: "action", width: 210, fixed: "right",
      render: (_, row) => <Space size={0}>
        <Button type="link" onClick={() => setShipmentStyle(row)}>出货明细</Button>
        {row.latestQuotation ? <Button type="link" onClick={() => openQuotation(row)}>
          {row.quotationStatus === "DRAFT" ? "继续报价" : "查看报价"}
        </Button> : <Button type="link" disabled={!row.customerId || !row.developmentNo || row.quotationStatus !== "NOT_QUOTED"}
          onClick={() => openQuotation(row)}>做报价</Button>}
      </Space> },
  ];

  const groupColumns: ColumnsType<UnpricedStyleGroup> = [
    { title: "主款号", dataIndex: "mainStyle", width: 120,
      render: (value: string) => <Button type="link" onClick={() => toggleGroup(value)}>{value}</Button> },
    { title: "待定价款号数", dataIndex: "missingPriceCount", width: 140, align: "right", render: value => `${formatCount(value)} 个` },
    { title: "报价进度", dataIndex: "quotationCounts", width: 350, render: counts => <QuotationProgress counts={counts}/> },
    { title: "未计价出货双数", dataIndex: "shippingQuantity", width: 160, align: "right", render: value => `${formatCount(value)} 双` },
    { title: "操作", key: "action", width: 150,
      render: (_, row) => <Button type="link" icon={expandedKeys.includes(row.mainStyle) ? <UpOutlined/> : <DownOutlined/>}
        onClick={() => toggleGroup(row.mainStyle)}>{expandedKeys.includes(row.mainStyle) ? "收起明细" : "展开明细"}</Button> },
  ];
  const error = month ? detailsError : monthsError;
  const loading = month ? detailsLoading : monthsLoading;

  return <div className="workspace reconciliation-page unpriced-page">
    <div className="toolbar-band reconciliation-toolbar">
      <div>
        <Typography.Title level={3}>待定价款号</Typography.Title>
        <Typography.Text type="secondary">按出货月份查看未配置客户单价的款号，区分未报价、报价待完善与已报价待客户确认。</Typography.Text>
      </div>
      <Space wrap>
        <Select aria-label="客户" allowClear showSearch optionFilterProp="label" placeholder="全部客户" value={customerId}
          options={customers.map(customer => ({ label: customer.name, value: customer.id }))} style={{ width: 180 }}
          onChange={value => {
            const next = new URLSearchParams(searchParams);
            if (value) next.set("customerId", String(value));
            else next.delete("customerId");
            setSearchParams(next);
          }}/>
        <Button icon={<ReloadOutlined/>} loading={monthsLoading || detailsLoading} onClick={() => setRefresh(value => value + 1)}>刷新</Button>
      </Space>
    </div>
    <Alert type="info" showIcon
      message="已报价待确认表示已保存最终报价、尚未转为客户单价。客户确认后，在已有报价中采用为客户单价才计入货款；零元客户单价也视为已配置。同月按客户＋完整款号去重，多次出货双数合计。"/>
    {error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => setRefresh(value => value + 1)}>重试</Button>}/> : null}
    <div className="reconciliation-summary-grid">
      <div className="reconciliation-metric">
        <Statistic title={month ? "本月涉及主款号" : "有待定价款号的月份"} value={month ? details?.groups.length || 0 : months.length} suffix={month ? "个" : "个月"}/>
      </div>
      <div className="reconciliation-metric">
        <Statistic title={month ? "本月待定价款号" : "各月待定价款号合计"} value={month ? details?.missingPriceCount || 0 : totals.count} suffix="个"/>
      </div>
      <div className="reconciliation-metric">
        <Statistic title={month ? "本月未计价出货" : "未计价出货合计"} value={month ? details?.shippingQuantity || 0 : totals.quantity} suffix="双"/>
      </div>
    </div>
    <div className="unpriced-quotation-overview">
      <Typography.Text strong>{month ? "本月报价进度" : "各月报价进度合计"}</Typography.Text>
      <QuotationProgress counts={month ? details?.quotationCounts : totals.quotationCounts}/>
    </div>
    <div className="page-panel reconciliation-panel">
      <div className="reconciliation-panel-heading unpriced-panel-heading">
        <div>
          <Typography.Title level={4}>{month ? `${formatMonth(month)} · 待定价主款号` : "按出货月份汇总"}</Typography.Title>
          <Typography.Text type="secondary">{month ? "展开查看每款报价状态与已报价格，可同时展开多个主款号。" : "点击月份，查看该月的待定价款号及报价进度。"}</Typography.Text>
        </div>
        {month ? <Button icon={<ArrowLeftOutlined/>} onClick={() => selectMonth("")}>返回月份汇总</Button> : null}
      </div>
      {month ? <Table<UnpricedStyleGroup> key={`${customerId || "all"}-${month}`} rowKey="mainStyle" loading={loading}
        tableLayout="fixed" columns={groupColumns} dataSource={details?.groups || []} scroll={{ x: 1000 }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={error ? "加载失败，请重试" : "本月暂无待定价出货记录"}/> }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true, showTotal: total => `共 ${total} 个主款号` }}
        expandable={{ expandedRowKeys: expandedKeys, onExpandedRowsChange: keys => setExpandedKeys([...keys]),
          expandedRowRender: group => <div className="unpriced-detail-panel">
            <Typography.Text type="secondary">主款号 {group.mainStyle} · {formatCount(group.missingPriceCount)} 个待定价款号</Typography.Text>
            <Table<UnpricedStyleSummary> rowKey={row => JSON.stringify([row.customerId, row.developmentNo])} size="small" columns={styleColumns} dataSource={group.styles}
              pagination={group.styles.length > 10 ? { defaultPageSize: 10, showSizeChanger: true } : false} scroll={{ x: 1480 }}/>
          </div> }}/>
        : <Table<UnpricedMonthSummary> key={customerId || "all"} rowKey="month" loading={loading} columns={monthColumns}
          dataSource={months} scroll={{ x: 1100 }}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={error ? "加载失败，请重试" : "暂无待定价的已出货记录"}/> }}
          pagination={{ defaultPageSize: 12, showSizeChanger: false, showTotal: total => `共 ${total} 个月` }}/>
      }
    </div>
    <Drawer open={Boolean(shipmentStyle)} title={`${shipmentStyle?.customerName || ""} · ${shipmentStyle?.developmentNo || "未填款号"} 出货明细`}
      width={1000} onClose={() => setShipmentStyle(undefined)} destroyOnHidden>
      {shipmentStyle ? <Space wrap className="unpriced-drawer-quotation">
        <QuotationState style={shipmentStyle}/><QuotedPrice style={shipmentStyle}/>
      </Space> : null}
      <Typography.Paragraph type="secondary">共 {formatCount(shipmentStyle?.details.length)} 条出货记录，合计 {formatCount(shipmentStyle?.shippingQuantity)} 双。</Typography.Paragraph>
      <Table<UnpricedShipmentDetail> rowKey="taskItemId" size="small" columns={detailColumns} dataSource={shipmentStyle?.details || []}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }} scroll={{ x: 1280 }}/>
    </Drawer>
  </div>;
}
