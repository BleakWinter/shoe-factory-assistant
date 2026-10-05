import { MinusCircleOutlined, PlusOutlined } from "@ant-design/icons";
import { Alert, App, Button, Form, Input, InputNumber, Modal, Select, Space } from "antd";
import { useEffect, useState } from "react";
import { useCustomers } from "../hooks/useCustomers";
import { localToday, saveManualOrder, type ManualOrderPayload, type ManualOrderView } from "../api/manualOrderApi";

export default function ManualOrderEditor({open,view,onClose,onSaved}: {
  open:boolean; view?:ManualOrderView; onClose:()=>void; onSaved:(view:ManualOrderView)=>void;
}) {
  const customers=useCustomers();
  const { message }=App.useApp();
  const [form]=Form.useForm<ManualOrderPayload>();
  const [saving,setSaving]=useState(false);
  useEffect(()=>{
    if (!open) return;
    form.resetFields();
    if (view) form.setFieldsValue({
      customerId:view.order.customerId,orderNo:view.order.orderNo,orderDate:view.order.orderDate,
      deliveryDate:view.items[0]?.detail.deliveryDate,remark:view.order.remark,
      items:view.items.map(({detail})=>({id:detail.id,developmentNo:detail.developmentNo,
        colorMaterial:detail.upperMaterial,quantity:detail.quantity,remark:detail.remark})),
    });
    else form.setFieldsValue({orderDate:localToday(),items:[{developmentNo:"",quantity:1}]});
  },[open,view,form]);
  async function save() {
    const values=await form.validateFields().catch(()=>undefined);
    if (!values) return;
    setSaving(true);
    try {
      const result=await saveManualOrder(view?.order.id,{...values,
        deliveryDate:values.deliveryDate || null,
        items:values.items.map(row=>({...row,developmentNo:row.developmentNo.trim()}))});
      message.success(view?"订单已更新":"订单已新增");
      onSaved(result);
    } catch(error) { message.error(error instanceof Error?error.message:"订单保存失败"); }
    finally {setSaving(false);}
  }
  return <Modal title={view?"编辑手工订单":"新增手工订单"} open={open} width={1060}
    okText="保存订单" cancelText="取消" confirmLoading={saving} onCancel={onClose} onOk={()=>void save()} forceRender>
    <Form name="manual-order-editor" form={form} layout="vertical">
      <div className="manual-order-basics">
        <Form.Item name="customerId" label="客户" rules={[{required:true,message:"请选择客户"}]}>
          <Select disabled={Boolean(view)} placeholder="选择宝翔、天兔子或其他手工客户"
            options={customers.filter(c=>c.inputMode==="MANUAL"&&(c.enabled||c.id===view?.order.customerId))
              .map(c=>({label:c.name,value:c.id}))}/>
        </Form.Item>
        <Form.Item name="orderNo" label="客户订单号" rules={[{required:true,whitespace:true,message:"请输入订单号"}]}>
          <Input maxLength={128}/>
        </Form.Item>
        <Form.Item name="orderDate" label="订单日期" rules={[{required:true,message:"请选择订单日期"}]}>
          <Input type="date" max={localToday()}/>
        </Form.Item>
        <Form.Item name="deliveryDate" label="交货日期"><Input type="date"/></Form.Item>
      </div>
      <Alert type="info" showIcon message="订单保存后计入订单统计；确认出货后才计入已出货和月度对账。价格统一从该客户的款号价格配置读取，修改价格后历史对账也会更新。"/>
      <Form.List name="items" rules={[{validator:async(_,rows)=>{if(!rows?.length)throw new Error("请至少添加一条订单明细");}}]}>
        {(fields,{add,remove},{errors})=><>
          <div className="manual-line-header">款号 / 颜色材料 / 订单双数 / 备注</div>
          {fields.map(field=><div key={field.key} className="manual-order-line">
            <Form.Item name={[field.name,"id"]} hidden><Input/></Form.Item>
            <Form.Item name={[field.name,"developmentNo"]} rules={[{required:true,whitespace:true,message:"请输入款号"}]}>
              <Input placeholder="工厂款号" maxLength={128}/>
            </Form.Item>
            <Form.Item name={[field.name,"colorMaterial"]}><Input placeholder="颜色 / 材料" maxLength={255}/></Form.Item>
            <Form.Item name={[field.name,"quantity"]} rules={[{required:true,message:"请输入双数"}]}>
              <InputNumber min={1} max={1000000} precision={0} placeholder="双数"/>
            </Form.Item>
            <Form.Item name={[field.name,"remark"]}><Input placeholder="备注" maxLength={1000}/></Form.Item>
            <Button danger type="text" icon={<MinusCircleOutlined/>} onClick={()=>remove(field.name)} aria-label="删除明细"/>
          </div>)}
          <Space><Button icon={<PlusOutlined/>} onClick={()=>add({quantity:1})}>添加款号</Button><Form.ErrorList errors={errors}/></Space>
        </>}
      </Form.List>
      <Form.Item name="remark" label="订单备注" style={{marginTop:16}}><Input.TextArea maxLength={1000} rows={2}/></Form.Item>
    </Form>
  </Modal>;
}
