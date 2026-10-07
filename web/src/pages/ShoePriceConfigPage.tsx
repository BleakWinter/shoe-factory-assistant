import { EditOutlined } from "@ant-design/icons";
import { App, Button, Checkbox, Form, Image, Input, InputNumber, Modal, Select, Space, Table, Tag } from "antd";
import { toAssetUrl } from "../api/orderApi";
import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { createShoePriceConfig, fetchShoePriceConfigs, updateShoePriceConfig } from "../api/priceConfigApi";
import { useCustomers } from "../hooks/useCustomers";
import type { ShoePriceConfig } from "../types/priceConfig";
import { formatDateTime } from "../utils/format";
import PriceConfigToolbar from "../components/PriceConfigToolbar";
type Price = ShoePriceConfig;
interface Values { customerId:number; developmentNo:string; shoePrice?:number|null }
export default function ShoePriceConfigPage() {
  const {message}=App.useApp();
  const navigate=useNavigate();
  const customers=useCustomers();
  const [customerId,setCustomerId]=useState<number>();
  const [keyword,setKeyword]=useState("");
  const [incompleteOnly,setIncompleteOnly]=useState(false);
  const [rows,setRows]=useState<Price[]>([]);
  const [total,setTotal]=useState(0);
  const [page,setPage]=useState(1);
  const [loading,setLoading]=useState(false);
  const [saving,setSaving]=useState(false);
  const [open,setOpen]=useState(false);
  const [editing,setEditing]=useState<Price>();
  const [form]=Form.useForm<Values>();
  const requestSequence=useRef(0);
  const load=useCallback(async()=>{
    setLoading(true);
    const sequence=++requestSequence.current;
    try {
      const data=await fetchShoePriceConfigs({customerId,developmentNos:keyword,incompleteOnly,page,size:20});
      if(sequence!==requestSequence.current)return;
      setRows(data.records);setTotal(data.total);
    } catch(e){if(sequence===requestSequence.current){setRows([]);setTotal(0);message.error(e instanceof Error?e.message:"价格配置加载失败");}}
    finally {if(sequence===requestSequence.current)setLoading(false);}
  },[customerId,keyword,incompleteOnly,page,message]);
  useEffect(()=>{void load();},[load]);
  function edit(row?:Price) {
    setEditing(row);form.resetFields();
    form.setFieldsValue(row||{customerId:customerId||customers[0]?.id});setOpen(true);
  }
  async function save() {
    const values=await form.validateFields();setSaving(true);
    try {
      const payload={...values,shoePrice:values.shoePrice??null};
      if(editing)await updateShoePriceConfig(editing.id,payload);
      else await createShoePriceConfig(payload);
      setOpen(false);message.success("客户款号价格已保存");await load();
    } catch(e){message.error(e instanceof Error?e.message:"价格保存失败");}
    finally {setSaving(false);}
  }
  return <div className="workspace">
    <PriceConfigToolbar activeTab="prices" loading={loading} onRefresh={()=>void load()} onCreate={()=>edit()}/>
    <div className="page-panel">
      <Space wrap style={{marginBottom:16}}>
        <Select allowClear placeholder="全部客户" value={customerId} style={{width:180}}
          options={customers.map(c=>({label:c.name,value:c.id}))} onChange={value=>{setCustomerId(value);setPage(1);}}/>
        <Input.Search allowClear placeholder="款号" onSearch={value=>{setKeyword(value);setPage(1);}}/>
        <Checkbox checked={incompleteOnly} onChange={e=>{setIncompleteOnly(e.target.checked);setPage(1);}}>仅未配置价格</Checkbox>
      </Space>
      <Table rowKey="id" loading={loading} dataSource={rows} scroll={{x:750}} pagination={{current:page,pageSize:20,total,showSizeChanger:false,onChange:setPage}}
        columns={[
          {title:"客户",dataIndex:"customerName"},{title:"款号",dataIndex:"developmentNo"},
          {title:"图片",dataIndex:"imageUrl",render:value=>value?<Image width={60} height={48} style={{objectFit:"contain"}} src={toAssetUrl(value)}/>:"-"},
          {title:"颜色 / 材料",dataIndex:"upperMaterial",render:value=>value||"-"},
          {title:"单价",dataIndex:"shoePrice",render:value=>value==null?"-":`¥ ${Number(value).toFixed(2)}`},
          {title:"状态",dataIndex:"complete",render:value=><Tag color={value?"green":"gold"}>{value?"已配置":"待配置"}</Tag>},
          {title:"更新时间",dataIndex:"updatedAt",render:formatDateTime},
          {title:"操作",render:(_,row)=><Space><Button icon={<EditOutlined/>} onClick={()=>edit(row)}>编辑</Button><Button onClick={()=>navigate(`/price-configs/quotations/new?customerId=${row.customerId}&developmentNo=${encodeURIComponent(row.developmentNo)}`)}>做报价</Button></Space>},
        ]}/>
    </div>
    <Modal open={open} title={editing?"编辑价格":"新建客户款号价格"} okText="保存" cancelText="取消"
      confirmLoading={saving} onCancel={()=>setOpen(false)} onOk={()=>void save()} forceRender>
      <Form name="customer-price-editor" form={form} layout="vertical">
        <Form.Item name="customerId" label="客户" rules={[{required:true,message:"请选择客户"}]}>
          <Select disabled={Boolean(editing)} options={customers.map(c=>({label:c.name,value:c.id}))}/>
        </Form.Item>
        <Form.Item name="developmentNo" label="款号" rules={[{required:true,whitespace:true,message:"请输入款号"}]}><Input maxLength={128} disabled={Boolean(editing)}/></Form.Item>
        <Form.Item name="shoePrice" label="单价" extra="留空表示待配置；0 表示零价。"><InputNumber min={0} max={99999999.99} precision={2}/></Form.Item>
      </Form>
    </Modal>
  </div>;
}
