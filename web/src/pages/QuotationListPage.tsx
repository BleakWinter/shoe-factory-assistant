import { CopyOutlined } from "@ant-design/icons";
import { App, Button, Cascader, Image, Input, Select, Space, Table, Tag, Typography } from "antd";
import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchQuotation, fetchQuotationDevelopmentNoOptions, fetchQuotations } from "../api/quotationApi";
import { useCustomers } from "../hooks/useCustomers";
import type { Quotation } from "../types/quotation";
import type { DevelopmentNoOption } from "../types/order";
import { formatDateTime } from "../utils/format";
import { priceText, quotationDraft, today } from "../utils/quotation";
import PriceConfigToolbar from "../components/PriceConfigToolbar";

export default function QuotationListPage() {
  const { message } = App.useApp(), customers = useCustomers(), navigate = useNavigate();
  const [customerId, setCustomerId] = useState<number>();
  const [developmentNoPaths, setDevelopmentNoPaths] = useState<string[][]>([]);
  const [developmentNoOptions, setDevelopmentNoOptions] = useState<DevelopmentNoOption[]>([]);
  const [optionLoading, setOptionLoading] = useState(false);
  const [startDate, setStartDate] = useState(today), [endDate, setEndDate] = useState(today);
  const [page, setPage] = useState(1), [rows, setRows] = useState<Quotation[]>([]), [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false), [copying, setCopying] = useState<number>();
  const sequence = useRef(0);
  const optionSequence = useRef(0);
  const loadOptions = useCallback(async () => {
    const current = ++optionSequence.current; setOptionLoading(true); setDevelopmentNoOptions([]);
    try { const options = await fetchQuotationDevelopmentNoOptions(customerId); if (current === optionSequence.current) setDevelopmentNoOptions(options); }
    catch(error) { if (current === optionSequence.current) message.error(error instanceof Error ? error.message : "报价款号加载失败"); }
    finally { if (current === optionSequence.current) setOptionLoading(false); }
  }, [customerId, message]);
  const load = useCallback(async () => {
    const current = ++sequence.current; setLoading(true);
    try {
      const developmentNos = developmentNoPaths.map(path => path[path.length - 1]).filter(Boolean).join(",");
      const data = await fetchQuotations({ customerId, developmentNos, startDate: startDate || undefined, endDate: endDate || undefined, page, size: 20 });
      if (current === sequence.current) { setRows(data.records); setTotal(data.total); }
    }
    catch(error) { if (current === sequence.current) { setRows([]); setTotal(0); message.error(error instanceof Error ? error.message : "报价加载失败"); } }
    finally { if (current === sequence.current) setLoading(false); }
  }, [customerId, developmentNoPaths, startDate, endDate, page, message]);
  useEffect(() => { void load(); return () => { sequence.current++; }; }, [load]);
  useEffect(() => { void loadOptions(); return () => { optionSequence.current++; }; }, [loadOptions]);
  function resetFilters() {
    setCustomerId(undefined); setDevelopmentNoPaths([]); setPage(1);
    const date = today(); setStartDate(date); setEndDate(date);
  }
  async function copy(row: Quotation) {
    setCopying(row.id);
    try { const quote = await fetchQuotation(row.id); navigate("/price-configs/quotations/new", { state: { copy: { ...quotationDraft(quote), developmentNo: "", quotationDate: today() } } }); }
    catch(error) { message.error(error instanceof Error ? error.message : "复制失败"); }
    finally { setCopying(undefined); }
  }
  return <div className="workspace quotation-workspace">
    <PriceConfigToolbar activeTab="quotations" loading={loading} onRefresh={() => void Promise.all([load(), loadOptions()])}
      onCreate={() => navigate("/price-configs/quotations/new")}/>
    <div className="page-panel"><Space wrap style={{ marginBottom: 16 }}>
      <Select allowClear placeholder="全部客户" value={customerId} style={{ width: 180 }} options={customers.map(c => ({ label: c.name, value: c.id }))} onChange={value => { setCustomerId(value); setDevelopmentNoPaths([]); setPage(1); }}/>
      <Cascader allowClear multiple showSearch className="style-cascader" aria-label="款号" style={{ width: 320 }}
        placeholder={optionLoading ? "款号加载中…" : "款号（可多选）"} disabled={optionLoading}
        displayRender={labels => labels.join("-")} maxTagCount="responsive" showCheckedStrategy={Cascader.SHOW_CHILD}
        options={developmentNoOptions} value={developmentNoPaths}
        onChange={value => { setDevelopmentNoPaths(value as string[][]); setPage(1); }}/>
      <Space size={8} wrap>
        <span>报价日期</span>
        <Input allowClear type="date" aria-label="开始日期" value={startDate} max={endDate || undefined} style={{ width: 150 }}
          onChange={e => { setStartDate(e.target.value); setPage(1); }}/>
        <span>至</span>
        <Input allowClear type="date" aria-label="结束日期" value={endDate} min={startDate || undefined} style={{ width: 150 }}
          onChange={e => { setEndDate(e.target.value); setPage(1); }}/>
        <Button onClick={() => { setStartDate(""); setEndDate(""); setPage(1); }}>全部时间</Button>
      </Space>
      <Button onClick={resetFilters}>重置</Button>
      <Typography.Text type="secondary">每份报价显示最新版本</Typography.Text></Space>
      <Table rowKey="id" dataSource={rows} loading={loading} scroll={{ x: 1000 }} pagination={{ current: page, pageSize: 20, total, showSizeChanger: false, onChange: setPage }} columns={[
        { title: "鞋图", dataIndex: "imageUrl", width: 80, render: value => value ? <Image width={60} height={48} style={{ objectFit: "contain" }} src={value}/> : "—" },
        { title: "客户", dataIndex: "customerName", render: value => value || <Tag color="gold">待填写</Tag> },
        { title: "款号", dataIndex: "developmentNo", render: (value, row) => <Button type="link" onClick={() => navigate(`/price-configs/quotations/${row.id}`)}>{value || "未填款号"}</Button> },
        { title: "颜色 / 材料", dataIndex: "colorMaterial", render: value => value || "—" },
        { title: "最终报价", dataIndex: "finalPrice", render: value => value === null ? <Tag color="gold">待填写</Tag> : `¥ ${priceText(value)}` },
        { title: "当前客户单价", dataIndex: "currentPrice", render: value => value === null ? "未配置" : `¥ ${priceText(value)}` },
        { title: "版本", dataIndex: "versionNo", render: value => `V${value}` },
        { title: "状态", render: (_, row) => <Tag color={row.adoptedAt ? "green" : "blue"}>{row.adoptedAt ? "曾采用" : "已保存"}</Tag> },
        { title: "保存时间", dataIndex: "createdAt", render: formatDateTime },
        { title: "操作", width: 205, render: (_, row) => <Space><Button onClick={() => navigate(`/price-configs/quotations/${row.id}`)}>编辑</Button><Button icon={<CopyOutlined/>} loading={copying === row.id} onClick={() => void copy(row)}>复制</Button></Space> },
      ]}/></div>
  </div>;
}
