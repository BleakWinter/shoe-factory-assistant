import { Alert, Skeleton } from "antd";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import request from "../utils/request";
import type { OrderRecord } from "../types/order";
import ManualOrderPage from "./ManualOrderPage";
import OrderDetailPage from "./OrderDetailPage";
export default function OrderDetailsRoute() {
  const {id}=useParams();
  const [order,setOrder]=useState<OrderRecord>();
  const [error,setError]=useState("");
  useEffect(()=>{
    let active=true;setOrder(undefined);setError("");
    request.get<OrderRecord>(`/orders/${id}`).then(({data})=>{if(active)setOrder(data);})
      .catch(e=>{if(active)setError(e instanceof Error?e.message:"订单加载失败");});
    return ()=>{active=false;};
  },[id]);
  if(error)return <Alert type="error" showIcon message={error}/>;
  if(!order)return <Skeleton active/>;
  return order.sourceType===3 ? <ManualOrderPage/> : <OrderDetailPage/>;
}
