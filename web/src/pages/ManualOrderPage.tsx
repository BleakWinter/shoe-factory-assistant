import { ArrowLeftOutlined, CheckOutlined, EditOutlined, ReloadOutlined } from "@ant-design/icons";
import { Alert, App, Button, Checkbox, Empty, Form, Input, Modal, Space, Statistic, Table, Tag, Typography } from "antd";
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { confirmManualShipment, fetchManualOrder, localToday, type ManualOrderView } from "../api/manualOrderApi";
import ManualOrderEditor from "../components/ManualOrderEditor";
import { useCustomers } from "../hooks/useCustomers";

export default function ManualOrderPage() {
  const {id}=useParams();
  const orderId=Number(id);
  const navigate=useNavigate();
  const {message}=App.useApp();
  const customers=useCustomers();
  const [view,setView]=useState<ManualOrderView>();
  const [loading,setLoading]=useState(false);
  const [editing,setEditing]=useState(false);
  const [shipping,setShipping]=useState(false);
  const [submitting,setSubmitting]=useState(false);
  const [requestId,setRequestId]=useState("");
  const [shipmentForm]=Form.useForm<{shippingDate:string; detailIds:number[]}>();
  useEffect(()=>{
    let active=true;
    setLoading(true);setView(undefined);
    fetchManualOrder(orderId).then(v=>{if(active)setView(v);})
      .catch(e=>{if(active)message.error(e instanceof Error?e.message:"订单加载失败");})
      .finally(()=>{if(active)setLoading(false);});
    return ()=>{active=false;};
  },[orderId,message]);
  async function refresh() {
    setLoading(true);
    try {setView(await fetchManualOrder(orderId));}
    catch(e) {message.error(e instanceof Error?e.message:"订单加载失败");}
    finally {setLoading(false);}
  }
  function openShipment() {
    shipmentForm.resetFields();
    shipmentForm.setFieldsValue({shippingDate:localToday(),
      detailIds:(view?.items||[]).filter(i=>i.remainingQuantity>0).map(i=>i.detail.id)});
    setRequestId(Array.from(crypto.getRandomValues(new Uint32Array(4)),n=>n.toString(16).padStart(8,"0")).join("")); setShipping(true);
  }
  async function confirm() {
    const values=await shipmentForm.validateFields().catch(()=>undefined);
    if (!values) return;
    setSubmitting(true);
    try {
      const next=await confirmManualShipment(orderId,{requestId,shippingDate:values.shippingDate,detailIds:values.detailIds});
      setView(next);setShipping(false);message.success("出货已确认，统计和月度对账已同步");
    } catch(e){message.error(e instanceof Error?e.message:"确认出货失败");}
    finally {setSubmitting(false);}
  }
  const total=view?.items.reduce((sum,i)=>sum+i.detail.quantity,0)||0;
  const shipped=view?.items.reduce((sum,i)=>sum+i.shippedQuantity,0)||0;
  const customer=customers.find(c=>c.id===view?.order.customerId);
  return <div className="workspace">
    <div className="toolbar-band">
      <div><Typography.Title level={3}>手工订单 {view?.order.orderNo}</Typography.Title>
        <Typography.Text type="secondary">{customer?.name||view?.order.customerName} · 订单日期 {view?.order.orderDate||"-"}</Typography.Text></div>
      <Space wrap>
        <Button icon={<ArrowLeftOutlined/>} onClick={()=>navigate("/orders")}>订单列表</Button>
        <Button icon={<ReloadOutlined/>} loading={loading} onClick={()=>void refresh()}>刷新</Button>
        <Button icon={<EditOutlined/>} disabled={!view||shipped>0} onClick={()=>setEditing(true)}>编辑订单</Button>
        <Button type="primary" icon={<CheckOutlined/>} disabled={!view||total-shipped<=0} onClick={openShipment}>确认出货</Button>
      </Space>
    </div>
    {!view ? <div className="page-panel"><Empty description={loading?"正在加载订单":"订单未加载，请刷新重试"}/></div> : <>
      <div className="manual-summary-grid">
        <div className="page-panel"><Statistic title="订单数量" value={total} suffix="双"/></div>
        <div className="page-panel"><Statistic title="已出货" value={shipped} suffix="双"/></div>
        <div className="page-panel"><Statistic title="未出货" value={Math.max(0,total-shipped)} suffix="双"/></div>
      </div>
      <div className="page-panel">
        <Typography.Title level={5}>订单明细</Typography.Title>
        {shipped>0&&<Alert type="info" showIcon message="此订单已有出货记录，订单明细已锁定。尚未出货的明细仍可确认出货，每条明细一次全部出货。"/>}
        <Table rowKey={row=>row.detail.id} dataSource={view.items} pagination={false} scroll={{x:720}}
          columns={[
            {title:"款号",render:(_,r)=>r.detail.developmentNo},
            {title:"颜色 / 材料",render:(_,r)=>r.detail.upperMaterial||"-"},
            {title:"订单双数",render:(_,r)=>r.detail.quantity},
            {title:"已出货",dataIndex:"shippedQuantity"},
            {title:"未出货",dataIndex:"remainingQuantity"},
            {title:"备注",render:(_,r)=>r.detail.remark||"-"},
          ]}/>
        {view.order.remark&&<Typography.Paragraph>订单备注：{view.order.remark}</Typography.Paragraph>}
      </div>
      <div className="page-panel">
        <Typography.Title level={5}>出货记录</Typography.Title>
        <Table rowKey={row=>row.shipment.id} dataSource={view.shipments} pagination={false} scroll={{x:720}}
          columns={[
            {title:"出货编号",render:(_,r)=>r.shipment.taskNo},
            {title:"实际出货日期",render:(_,r)=>r.shipment.shippingDate},
            {title:"本次双数",render:(_,r)=>r.shipment.totalPairs},
            {title:"出货明细",render:(_,r)=><Space wrap>{r.items.map(i=><Tag key={i.sourceDetailId}>
              {view.items.find(d=>d.detail.id===i.sourceDetailId)?.detail.developmentNo}：
              {view.items.find(d=>d.detail.id===i.sourceDetailId)?.detail.quantity} 双
            </Tag>)}</Space>},
          ]}/>
        <Space style={{marginTop:16}}><Button onClick={()=>navigate("/statistics")}>查看数据统计</Button>
          <Button onClick={()=>navigate("/monthly-reconciliation")}>查看月度对账</Button></Space>
      </div>
    </>}
    <ManualOrderEditor open={editing} view={view} onClose={()=>setEditing(false)} onSaved={v=>{setView(v);setEditing(false);}}/>
    <Modal open={shipping} title="确认实际出货" okText="确认出货" cancelText="取消"
      onCancel={()=>{if(!submitting)setShipping(false);}} confirmLoading={submitting} onOk={()=>void confirm()} forceRender>
      <Form name="manual-shipment-confirmation" form={shipmentForm} layout="vertical">
        <Form.Item name="shippingDate" label="实际出货日期" rules={[{required:true,message:"请选择出货日期"}]}>
          <Input type="date" min={view?.order.orderDate} max={localToday()}/>
        </Form.Item>
        <Alert type="info" showIcon message="勾选本次出货明细，每条明细按订单双数一次全部出货，不填写分批数量。确认后计入所选月份的对账。"/>
        <Form.Item name="detailIds" label="本次出货明细" style={{marginTop:16}}
          rules={[{required:true,type:"array",min:1,message:"请至少选择一条出货明细"}]}>
          <Checkbox.Group style={{display:"flex",flexDirection:"column",gap:12}}
            options={view?.items.filter(i=>i.remainingQuantity>0).map(i=>({value:i.detail.id,
              label:`${i.detail.developmentNo}${i.detail.upperMaterial?` · ${i.detail.upperMaterial}`:""}（全部 ${i.detail.quantity} 双）`}))}/>
        </Form.Item>
      </Form>
    </Modal>
  </div>;
}
