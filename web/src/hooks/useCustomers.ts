import { useEffect, useState } from "react";
import { App } from "antd";
import { fetchCustomers, type Customer } from "../api/customerApi";
export function useCustomers() {
  const { message }=App.useApp();
  const [customers,setCustomers]=useState<Customer[]>([]);
  useEffect(()=>{
    let active=true;
    fetchCustomers().then(value=>{if(active)setCustomers(value);})
      .catch(error=>{if(active)message.error(error instanceof Error?error.message:"客户加载失败");});
    return ()=>{active=false;};
  },[message]);
  return customers;
}
