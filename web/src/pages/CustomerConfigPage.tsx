import { EditOutlined, PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import { App, Button, Form, Input, Modal, Select, Space, Switch, Table, Tag, Typography } from "antd";
import { useCallback, useEffect, useState } from "react";
import { fetchCustomers, saveCustomer, type Customer, type CustomerPayload } from "../api/customerApi";
import { formatDateTime } from "../utils/format";
export default function CustomerConfigPage() {
  const {message}=App.useApp();
  const [customers,setCustomers]=useState<Customer[]>([]);
  const [loading,setLoading]=useState(false);
  const [saving,setSaving]=useState(false);
  const [open,setOpen]=useState(false);
  const [editing,setEditing]=useState<Customer>();
  const [keyword,setKeyword]=useState("");
  const [form]=Form.useForm<CustomerPayload>();
  const load=useCallback(async()=>{
    setLoading(true);
    try {setCustomers(await fetchCustomers());}
    catch(e){message.error(e instanceof Error?e.message:"客户配置加载失败");}
    finally {setLoading(false);}
  },[message]);
  useEffect(()=>{void load();},[load]);
  function edit(customer?:Customer) {
    setEditing(customer);form.resetFields();
    form.setFieldsValue(customer||{enabled:true,inputMode:"MANUAL"});
    setOpen(true);
  }
  async function save() {
    const values=await form.validateFields();
    setSaving(true);
    try {await saveCustomer(editing?.id,values);setOpen(false);message.success("客户配置已保存");await load();}
    catch(e){message.error(e instanceof Error?e.message:"客户保存失败");}
    finally {setSaving(false);}
  }
  async function toggle(customer:Customer,enabled:boolean) {
    try {await saveCustomer(customer.id,{...customer,enabled});await load();}
    catch(e){message.error(e instanceof Error?e.message:"状态修改失败");}
  }
  return <div className="workspace customer-config-page">
    <div className="toolbar-band">
      <div><Typography.Title level={3}>客户配置</Typography.Title>
        <Typography.Text type="secondary">按客户选择录单方式，订单与出货数据统一用于统计和月度对账。</Typography.Text></div>
      <Space><Button icon={<ReloadOutlined/>} loading={loading} onClick={()=>void load()}>刷新</Button>
        <Button type="primary" icon={<PlusOutlined/>} onClick={()=>edit()}>新增客户</Button></Space>
    </div>
    <div className="page-panel">
      <Input.Search allowClear placeholder="客户名称或编码" value={keyword} onChange={e=>setKeyword(e.target.value)} style={{maxWidth:300,marginBottom:16}}/>
      <Table rowKey="id" loading={loading} pagination={false} scroll={{x:900}}
        dataSource={customers.filter(c=>(c.name+c.code).toLowerCase().includes(keyword.trim().toLowerCase()))}
        columns={[
          {title:"客户名称",dataIndex:"name"},
          {title:"编码",dataIndex:"code"},
          {title:"出货 / 对账名称",dataIndex:"recipientName"},
          {title:"录单方式",dataIndex:"inputMode",render:value=><Tag color={value==="EXCEL"?"blue":"green"}>{value==="EXCEL"?"Excel 上传识别":"手工录入、确认出货"}</Tag>},
          {title:"状态",render:(_,c)=><Switch checked={c.enabled} checkedChildren="启用" unCheckedChildren="停用" onChange={enabled=>void toggle(c,enabled)}/>},
          {title:"更新时间",dataIndex:"updatedAt",render:formatDateTime},
          {title:"操作",render:(_,c)=><Button icon={<EditOutlined/>} onClick={()=>edit(c)}>编辑</Button>},
        ]}/>
      <Typography.Paragraph type="secondary" style={{marginTop:16}}>停用客户后不能新增订单，已有订单、出货记录和对账历史仍可查看。</Typography.Paragraph>
    </div>
    <Modal open={open} title={editing?"编辑客户":"新增客户"} okText="保存" cancelText="取消"
      confirmLoading={saving} onCancel={()=>setOpen(false)} onOk={()=>void save()} forceRender>
      <Form name="customer-config-editor" form={form} layout="vertical">
        <Form.Item name="name" label="客户名称" rules={[{required:true,whitespace:true,message:"请输入客户名称"}]}><Input maxLength={50}/></Form.Item>
        <Form.Item name="code" label="客户编码" rules={[{required:true,message:"请输入编码"},{pattern:/^[A-Za-z0-9_-]+$/,message:"使用字母、数字、下划线或短横线"}]}>
          <Input disabled={editing?.code==="DAWEI"} maxLength={30} placeholder="例如 BAOXIANG"/>
        </Form.Item>
        <Form.Item name="recipientName" label="出货 / 对账名称" rules={[{required:true,whitespace:true,message:"请输入出货单位名称"}]}>
          <Input maxLength={128} placeholder="例如 达为鞋业"/>
        </Form.Item>
        <Form.Item name="inputMode" label="录单方式" rules={[{required:true}]}>
          <Select disabled={Boolean(editing)} options={[{value:"EXCEL",label:"Excel 上传识别（达为模板）",disabled:true},{value:"MANUAL",label:"手工录入、确认出货"}]}/>
        </Form.Item>
        <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch/></Form.Item>
      </Form>
    </Modal>
  </div>;
}
