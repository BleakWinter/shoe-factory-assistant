import { CopyOutlined, PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import { App, Button, Image, Input, Select, Space, Table, Tag, Typography } from "antd";
import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchQuotation, fetchQuotations } from "../api/quotationApi";
import { useCustomers } from "../hooks/useCustomers";
import type { Quotation } from "../types/quotation";
import { formatDateTime } from "../utils/format";
import { priceText, quotationDraft, today } from "../utils/quotation";

export default function QuotationListPage() {
  const { message } = App.useApp(), customers = useCustomers(), navigate = useNavigate();
  const [customerId, setCustomerId] = useState<number>(), [keyword, setKeyword] = useState("");
  const [page, setPage] = useState(1), [rows, setRows] = useState<Quotation[]>([]), [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false), [copying, setCopying] = useState<number>();
  const sequence = useRef(0);
  const load = useCallback(async () => {
    const current = ++sequence.current; setLoading(true);
    try { const data = await fetchQuotations({ customerId, developmentNo: keyword, page, size: 20 }); if (current === sequence.current) { setRows(data.records); setTotal(data.total); } }
    catch(error) { if (current === sequence.current) { setRows([]); setTotal(0); message.error(error instanceof Error ? error.message : "报价加载失败"); } }
    finally { if (current === sequence.current) setLoading(false); }
  }, [customerId, keyword, page, message]);
  useEffect(() => { void load(); return () => { sequence.current++; }; }, [load]);
  async function copy(row: Quotation) {
    setCopying(row.id);
    try { const quote = await fetchQuotation(row.id); navigate("/price-configs/quotations/new", { state: { copy: { ...quotationDraft(quote), developmentNo: "", quotationDate: today() } } }); }
    catch(error) { message.error(error instanceof Error ? error.message : "复制失败"); }
    finally { setCopying(undefined); }
  }
  return <div className="workspace quotation-workspace">
    <div className="toolbar-band"><div><Typography.Title level={3}>鞋款报价</Typography.Title><Typography.Text type="secondary">在系统里算成本、保存报价；每次修改保留独立版本。</Typography.Text></div>
      <Space wrap><Button onClick={() => navigate("/price-configs")}>客户单价</Button><Button icon={<ReloadOutlined/>} loading={loading} onClick={() => void load()}>刷新</Button>
        <Button type="primary" icon={<PlusOutlined/>} onClick={() => navigate("/price-configs/quotations/new")}>新建报价</Button></Space></div>
    <div className="page-panel"><Space wrap style={{ marginBottom: 16 }}>
      <Select allowClear placeholder="全部客户" value={customerId} style={{ width: 180 }} options={customers.map(c => ({ label: c.name, value: c.id }))} onChange={value => { setCustomerId(value); setPage(1); }}/>
      <Input.Search allowClear placeholder="搜索款号" onSearch={value => { setKeyword(value); setPage(1); }}/>
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
