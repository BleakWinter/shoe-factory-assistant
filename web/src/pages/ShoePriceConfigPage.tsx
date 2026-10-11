import { EditOutlined } from "@ant-design/icons";
import { App, Button, Cascader, Checkbox, Form, Image, Input, InputNumber, Modal, Select, Space, Table, Tag } from "antd";
import { toAssetUrl } from "../api/orderApi";
import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { createShoePriceConfig, fetchShoePriceConfigDevelopmentNoOptions, fetchShoePriceConfigs, updateShoePriceConfig } from "../api/priceConfigApi";
import { fetchQuotationForPriceConfig } from "../api/quotationApi";
import { useCustomers } from "../hooks/useCustomers";
import type { ShoePriceConfig } from "../types/priceConfig";
import type { DevelopmentNoOption } from "../types/order";
import { formatDateTime } from "../utils/format";
import PriceConfigToolbar from "../components/PriceConfigToolbar";
type Price = ShoePriceConfig;
interface Values { customerId:number; developmentNo:string; shoePrice?:number|null }
export default function ShoePriceConfigPage() {
  const {message}=App.useApp();
  const navigate=useNavigate();
  const customers=useCustomers();
  const [customerId,setCustomerId]=useState<number>();
  const [developmentNoPaths,setDevelopmentNoPaths]=useState<string[][]>([]);
  const [developmentNoOptions,setDevelopmentNoOptions]=useState<DevelopmentNoOption[]>([]);
  const [optionLoading,setOptionLoading]=useState(false);
  const [incompleteOnly,setIncompleteOnly]=useState(false);
  const [rows,setRows]=useState<Price[]>([]);
  const [total,setTotal]=useState(0);
  const [page,setPage]=useState(1);
  const [loading,setLoading]=useState(false);
  const [saving,setSaving]=useState(false);
  const [open,setOpen]=useState(false);
  const [editing,setEditing]=useState<Price>();
  const [openingQuotationId,setOpeningQuotationId]=useState<number>();
  const [form]=Form.useForm<Values>();
  const requestSequence=useRef(0);
  const optionRequestSequence=useRef(0);
  const quotationRequestSequence=useRef(0);
  const openingQuotation=useRef(false);
  const loadOptions=useCallback(async()=>{
    const sequence=++optionRequestSequence.current;
    setOptionLoading(true);setDevelopmentNoOptions([]);
    try {
      const options=await fetchShoePriceConfigDevelopmentNoOptions(customerId);
      if(sequence===optionRequestSequence.current)setDevelopmentNoOptions(options);
    } catch(e){if(sequence===optionRequestSequence.current)message.error(e instanceof Error?e.message:"款号选项加载失败");}
    finally {if(sequence===optionRequestSequence.current)setOptionLoading(false);}
  },[customerId,message]);
  const load=useCallback(async()=>{
    setLoading(true);
    const sequence=++requestSequence.current;
    try {
      const developmentNos=developmentNoPaths.map(path=>path[path.length-1]).filter(Boolean).join(",");
      const data=await fetchShoePriceConfigs({customerId,developmentNos,incompleteOnly,page,size:20});
      if(sequence!==requestSequence.current)return;
      setRows(data.records);setTotal(data.total);
    } catch(e){if(sequence===requestSequence.current){setRows([]);setTotal(0);message.error(e instanceof Error?e.message:"价格配置加载失败");}}
    finally {if(sequence===requestSequence.current)setLoading(false);}
  },[customerId,developmentNoPaths,incompleteOnly,page,message]);
  useEffect(()=>{void load();return ()=>{requestSequence.current++;};},[load]);
  useEffect(()=>{void loadOptions();return ()=>{optionRequestSequence.current++;};},[loadOptions]);
  useEffect(()=>()=>{quotationRequestSequence.current++;},[]);
  async function openQuotation(row:Price) {
    if(openingQuotation.current)return;
    openingQuotation.current=true;setOpeningQuotationId(row.id);
    const sequence=++quotationRequestSequence.current;
    try {
      const quotationId=await fetchQuotationForPriceConfig(row.customerId,row.developmentNo);
      if(sequence!==quotationRequestSequence.current)return;
      navigate(quotationId!=null?`/price-configs/quotations/${quotationId}`:
        `/price-configs/quotations/new?customerId=${row.customerId}&developmentNo=${encodeURIComponent(row.developmentNo)}`);
    } catch(e){if(sequence===quotationRequestSequence.current)message.error(e instanceof Error?e.message:"报价加载失败");}
    finally {if(sequence===quotationRequestSequence.current){openingQuotation.current=false;setOpeningQuotationId(undefined);}}
  }
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
      setOpen(false);message.success("客户款号价格已保存");await Promise.all([load(),loadOptions()]);
    } catch(e){message.error(e instanceof Error?e.message:"价格保存失败");}
    finally {setSaving(false);}
  }
  return <div className="workspace">
    <PriceConfigToolbar activeTab="prices" loading={loading} onRefresh={()=>void Promise.all([load(),loadOptions()])} onCreate={()=>edit()}/>
    <div className="page-panel">
      <Space wrap style={{marginBottom:16}}>
        <Select allowClear placeholder="全部客户" value={customerId} style={{width:180}}
          options={customers.map(c=>({label:c.name,value:c.id}))} onChange={value=>{setCustomerId(value);setDevelopmentNoPaths([]);setPage(1);}}/>
        <Cascader allowClear multiple showSearch className="style-cascader" aria-label="款号" style={{width:320}}
          placeholder={optionLoading?"款号加载中…":"款号（可多选）"} disabled={optionLoading}
          displayRender={labels=>labels.join("-")} maxTagCount="responsive" showCheckedStrategy={Cascader.SHOW_CHILD}
          options={developmentNoOptions} value={developmentNoPaths}
          onChange={value=>{setDevelopmentNoPaths(value as string[][]);setPage(1);}}/>
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
          {title:"操作",render:(_,row)=><Space><Button icon={<EditOutlined/>} onClick={()=>edit(row)}>编辑</Button><Button loading={openingQuotationId===row.id} disabled={openingQuotationId!=null} onClick={()=>void openQuotation(row)}>做报价</Button></Space>},
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
