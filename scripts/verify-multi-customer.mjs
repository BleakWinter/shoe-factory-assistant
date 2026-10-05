// Run only against the isolated MySQL fixture described in docs/多客户录单与对账.md.
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
const base="http://127.0.0.1:18081/api";
async function api(path,method="GET",payload,expected=200) {
  const response=await fetch(base+path,{method,headers:{"Content-Type":"application/json"},body:payload?JSON.stringify(payload):undefined});
  const body=await response.json();
  assert.equal(response.status,expected,method+" "+path+": "+body.message);
  if(expected===200)assert.equal(body.success,true);
  return body.data;
}
const suffix=Date.now().toString(36);
const numericStyle=String(Date.now());
const customers=await api("/customer-configs");
const dawei=customers.find(c=>c.code==="DAWEI");
const baoxiang=customers.find(c=>c.code==="BAOXIANG");
const rabbit=customers.find(c=>c.code==="TIANTUZI");
assert.ok(dawei&&baoxiang&&rabbit);
assert.equal(dawei.name,"达为");assert.equal(baoxiang.name,"宝翔");assert.equal(rabbit.name,"天兔子");
const legacy=await api("/orders/statistics?customerId="+dawei.id);
assert.equal(legacy.totalPairs,10);assert.equal(legacy.shippedPairs,10);assert.equal(legacy.totalPaymentAmount,250);
const style="SHARED-"+suffix;
const priceB=await api("/price-configs","POST",{customerId:baoxiang.id,developmentNo:style,shoePrice:12});
await api("/price-configs","POST",{customerId:rabbit.id,developmentNo:style,shoePrice:19});
await api("/manual-orders","POST",{customerId:dawei.id,orderNo:"INVALID",orderDate:"2026-09-01",items:[{developmentNo:style,quantity:1}]},400);
await api("/manual-orders","POST",{customerId:rabbit.id,orderNo:"INVALID",orderDate:"2026-09-01",items:[null]},400);
const orderNo="SAME-NUMBER-"+suffix;
const payloadB={customerId:baoxiang.id,orderNo,orderDate:"2026-09-01",deliveryDate:"2026-10-15",items:[
  {developmentNo:style,colorMaterial:"黑色牛皮",quantity:100},{developmentNo:numericStyle,colorMaterial:"白色",quantity:20}
]};
let b=await api("/manual-orders","POST",payloadB);
let t=await api("/manual-orders","POST",{customerId:rabbit.id,orderNo,orderDate:"2026-09-01",items:[{developmentNo:style,quantity:50}]});
await api("/manual-orders","POST",payloadB,400);
assert.equal((await api("/orders/"+b.order.id+"/details")).length,2);
assert.equal((await api("/orders/"+t.order.id+"/details")).length,1);
assert.equal(Object.hasOwn(b.items[0].detail,"unitPrice"),false);
const beforeB=await api("/orders/statistics?customerId="+baoxiang.id);
assert.ok(beforeB.totalPairs>=120);
assert.ok(beforeB.developmentNoTree.some(row=>row.label===numericStyle));
const shipmentPath="/manual-orders/"+b.order.id+"/shipments";
const first={requestId:randomUUID(),shippingDate:"2026-09-20",detailIds:[b.items[0].detail.id]};
await api(shipmentPath,"POST",{requestId:randomUUID(),shippingDate:"2026-09-20",items:[{detailId:b.items[0].detail.id,quantity:30}]},400);
for (const detailIds of [[],[null],[b.items[0].detail.id,b.items[0].detail.id],[t.items[0].detail.id]]) {
  await api(shipmentPath,"POST",{...first,requestId:randomUUID(),detailIds},400);
}
await api(shipmentPath,"POST",{...first,requestId:randomUUID(),shippingDate:"2999-01-01"},400);
await api(shipmentPath,"POST",{...first,requestId:randomUUID(),shippingDate:"2026-08-01"},400);
const concurrent=await Promise.all([api(shipmentPath,"POST",first),api(shipmentPath,"POST",first)]);
b=concurrent[1];assert.equal(b.shipments.length,1);assert.equal(b.items[0].shippedQuantity,100);assert.equal(b.items[0].remainingQuantity,0);
assert.equal(b.items[1].remainingQuantity,20);assert.equal(b.shipments[0].shipment.totalPairs,100);
assert.equal(Object.hasOwn(b.shipments[0].items[0],"unitPrice"),false);
assert.equal(Object.hasOwn(b.shipments[0].items[0],"shippedQuantity"),false);
await api(shipmentPath,"POST",{...first,shippingDate:"2026-09-21"},400);
await api(shipmentPath,"POST",{...first,requestId:randomUUID()},400);
await api("/manual-orders/"+b.order.id,"PUT",payloadB,400);
const sepPath="/monthly-reconciliations?month=2026-09&customerId="+baoxiang.id;
const sepBefore=await api(sepPath);
assert.equal(sepBefore.details.find(d=>d.orderNo===orderNo).shippingQuantity,100);
assert.equal(sepBefore.details.find(d=>d.orderNo===orderNo).amount,1200);
const paymentBefore=(await api("/orders/statistics?customerId="+baoxiang.id)).totalPaymentAmount;
await api("/price-configs/"+priceB.id,"PUT",{customerId:baoxiang.id,developmentNo:style,shoePrice:20});
const sepAfter=await api(sepPath);
assert.equal(sepAfter.details.find(d=>d.orderNo===orderNo).amount,2000);
assert.equal((await api("/orders/statistics?customerId="+baoxiang.id)).totalPaymentAmount,paymentBefore+800);
const second={requestId:randomUUID(),shippingDate:"2026-10-02",detailIds:[b.items[1].detail.id]};
const competing=await Promise.all([second,{...second,requestId:randomUUID()}].map(async payload=>{
  const response=await fetch(base+shipmentPath,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(payload)});
  const body=await response.json();
  assert.ok(response.status===200||response.status===400,JSON.stringify(body));
  if(response.status===400)assert.match(body.message,/已确认出货/);
  return response.status;
}));
assert.equal(competing.filter(status=>status===200).length,1);
b=await api("/manual-orders/"+b.order.id);
assert.equal(b.shipments.length,2);assert.ok(b.items.every(i=>i.remainingQuantity===0));
const octPath="/monthly-reconciliations?month=2026-10&customerId="+baoxiang.id;
let oct=await api(octPath);
assert.equal(oct.details.find(d=>d.orderNo===orderNo).shippingQuantity,20);
assert.equal(oct.details.find(d=>d.orderNo===orderNo).missingPrice,true);
assert.equal(oct.details.find(d=>d.orderNo===orderNo).amount,null);
const pending=await api("/price-configs?customerId="+baoxiang.id+"&developmentNos="+numericStyle);
const numericPrice=pending.records.find(p=>p.developmentNo===numericStyle);
for (const shoePrice of [5,0,null]) {
  await api("/price-configs/"+numericPrice.id,"PUT",{customerId:baoxiang.id,developmentNo:numericStyle,shoePrice});
  oct=await api(octPath);
  const row=oct.details.find(d=>d.orderNo===orderNo);
  assert.equal(row.missingPrice,shoePrice===null);assert.equal(row.amount,shoePrice===null?null:20*shoePrice);
}
t=await api("/manual-orders/"+t.order.id+"/shipments","POST",{requestId:randomUUID(),shippingDate:"2026-10-02",detailIds:[t.items[0].detail.id]});
const rabbitOct=await api("/monthly-reconciliations?month=2026-10&customerId="+rabbit.id);
assert.equal(rabbitOct.details.find(d=>d.orderNo===orderNo).amount,950);
const bStats=await api("/orders/statistics?customerId="+baoxiang.id);
assert.equal(bStats.shippedOrderStatistics.filter(r=>r.orderNo===orderNo).reduce((s,r)=>s+r.shippedPairs,0),120);
const allStats=await api("/orders/statistics");
const tStats=await api("/orders/statistics?customerId="+rabbit.id);
assert.equal(allStats.totalPairs,bStats.totalPairs+tStats.totalPairs+10);
assert.equal(allStats.shippedPairs,bStats.shippedPairs+tStats.shippedPairs+10);
assert.equal(allStats.totalPaymentAmount,bStats.totalPaymentAmount+tStats.totalPaymentAmount+250);
const allOrders=await api("/orders?page=1&size=100");
assert.equal(allOrders.records.filter(o=>o.orderNo===orderNo).length,2);
assert.ok((await api("/orders?sourceType=1")).records.every(o=>o.sourceType===1));
await api("/orders/"+b.order.id+"/recognize-order","POST",undefined,400);
const inactive=await api("/customer-configs","POST",{code:"TEST_"+suffix,name:"停用测试",recipientName:"停用测试-"+suffix,inputMode:"MANUAL",enabled:false});
await api("/manual-orders","POST",{customerId:inactive.id,orderNo:"BLOCKED",orderDate:"2026-09-01",items:[{developmentNo:style,quantity:1}]},400);
await api("/monthly-reconciliations?month=invalid&customerId="+rabbit.id,"GET",undefined,400);
let editable=await api("/manual-orders","POST",{customerId:baoxiang.id,orderNo:"EDIT-"+suffix,orderDate:"2026-09-01",
  items:[{developmentNo:"EDIT-"+suffix,quantity:10},{developmentNo:"DELETE-"+suffix,quantity:3}]});
const retained=editable.items[0].detail.id;
editable=await api("/manual-orders/"+editable.order.id,"PUT",{customerId:baoxiang.id,orderNo:"EDIT-"+suffix,orderDate:"2026-09-01",
  items:[{id:retained,developmentNo:"EDIT-"+suffix,quantity:20}]});
assert.equal(editable.items.length,1);assert.equal(editable.items[0].detail.id,retained);assert.equal(editable.order.totalQuantity,20);
await api("/manual-orders/"+editable.order.id,"PUT",{customerId:baoxiang.id,orderNo:"EDIT-"+suffix,orderDate:"2026-09-01",
  items:[{id:t.items[0].detail.id,developmentNo:"FOREIGN",quantity:1}]},400);
console.log("PASS: legacy schema without added detail columns, manual orders, customer isolation, numeric styles, full-line confirmation, cross-month lines, duplicate blocking, concurrent idempotency, historical repricing, missing/zero/cleared prices, monthly reconciliation, statistics totals, disabled customers.");
console.log(JSON.stringify({baoxiangOrderId:b.order.id,rabbitOrderId:t.order.id,editableOrderId:editable.order.id,orderNo}));
