import { EyeOutlined, FileAddOutlined, ReloadOutlined, SearchOutlined } from "@ant-design/icons";
import { App, Button, Form, Input, Modal, Pagination, Select, Space, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { Key } from "react";
import { createPackingOrderTask, fetchPackingOrderTask, fetchPackingOrderTasks, previewPackingOrder } from "../api/packingOrderApi";
import { fetchOrderDetails, fetchOrders } from "../api/orderApi";
import { fetchInnerBoxTypes } from "../api/innerBoxTypeApi";
import type { InnerBoxType } from "../types/innerBoxType";
import PackingOrderItems from "../components/PackingOrderItems";
import type { OrderRecord, OrderRecordDetail } from "../types/order";
import type { PackingOrderTask, PackingOrderItem } from "../types/packingOrder";
import { groupPackingSourceDetails } from "../utils/packingOrderGroups";
import type { PackingSourceGroup } from "../utils/packingOrderGroups";
import { formatDateTime, formatEmpty } from "../utils/format";
import { isCompleteBoxSpec } from "../utils/boxSpec";

function today() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

export default function PackingOrderPage() {
  const { message } = App.useApp();
  const [filters, setFilters] = useState<{ orderNo?: string; developmentNo?: string }>({});
  const [tasks, setTasks] = useState<PackingOrderTask[]>([]);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [orderDate, setOrderDate] = useState(today);
  const [items, setItems] = useState<PackingOrderItem[]>([]);
  const [boxTypes, setBoxTypes] = useState<InnerBoxType[]>([]);
  const [saving, setSaving] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [detailTask, setDetailTask] = useState<PackingOrderTask>();
  const [detailLoadingId, setDetailLoadingId] = useState<number>();
  const [pickerOpen, setPickerOpen] = useState(false);
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [orderSearch, setOrderSearch] = useState("");
  const [orderLoading, setOrderLoading] = useState(false);
  const [selectedOrder, setSelectedOrder] = useState<{ id: number; label: string }>();
  const [sourceDetails, setSourceDetails] = useState<OrderRecordDetail[]>([]);
  const [sourceLoading, setSourceLoading] = useState(false);
  const [developmentSearch, setDevelopmentSearch] = useState("");
  const [selectedIds, setSelectedIds] = useState<Key[]>([]);
  const previewRequest = useRef(0);
  const listRequest = useRef(0);
  const detailRequest = useRef(0);

  const loadTasks = useCallback(async (targetPage = page) => {
    const requestId = ++listRequest.current;
    setLoading(true);
    try {
      const data = await fetchPackingOrderTasks({ ...filters, page: targetPage, size });
      if (requestId !== listRequest.current) return;
      setTasks(data.records); setTotal(data.total);
    } catch (error) {
      if (requestId === listRequest.current) {
        setTasks([]); setTotal(0);
        message.error(error instanceof Error ? error.message : "订包装记录加载失败");
      }
    } finally { if (requestId === listRequest.current) setLoading(false); }
  }, [page, size, filters, message]);
  useEffect(() => { void loadTasks(); }, [loadTasks]);
  const loadBoxTypes = useCallback(async () => {
    try { setBoxTypes(await fetchInnerBoxTypes()); }
    catch (error) { message.error(error instanceof Error ? error.message : "内盒类型加载失败"); }
  }, [message]);
  useEffect(() => { if (createOpen) void loadBoxTypes(); }, [createOpen, loadBoxTypes]);
  useEffect(() => () => { previewRequest.current++; listRequest.current++; detailRequest.current++; }, []);

  useEffect(() => {
    if (!pickerOpen) return;
    let active = true;
    const timer = window.setTimeout(async () => {
      setOrderLoading(true);
      try {
        const data = await fetchOrders({ sourceType: 1, orderNo: orderSearch.trim() || undefined, page: 1, size: 100 });
        if (!active) return;
        setOrders(data.records);
        setSelectedOrder((current) => current || (data.records[0] ? {
          id: data.records[0].id, label: data.records[0].orderNo || `订单 ${data.records[0].id}`,
        } : undefined));
      } catch (error) {
        if (active) message.error(error instanceof Error ? error.message : "订单加载失败");
      } finally { if (active) setOrderLoading(false); }
    }, 250);
    return () => { active = false; window.clearTimeout(timer); };
  }, [pickerOpen, orderSearch, message]);

  useEffect(() => {
    if (!pickerOpen || !selectedOrder) return;
    let active = true;
    setSourceLoading(true); setSourceDetails([]); setSelectedIds([]);
    void fetchOrderDetails(selectedOrder.id).then((data) => {
      if (active) setSourceDetails(data);
    }).catch((error) => {
      if (active) message.error(error instanceof Error ? error.message : "订单明细加载失败");
    }).finally(() => { if (active) setSourceLoading(false); });
    return () => { active = false; };
  }, [pickerOpen, selectedOrder?.id, message]);

  const draftKeys = useMemo(() => new Set(items.map((item) => item.key)), [items]);
  const sourceGroups = useMemo(() => groupPackingSourceDetails(selectedOrder?.id, sourceDetails), [selectedOrder?.id, sourceDetails]);
  const totalPairs = items.reduce((sum, item) => sum + (item.quantity || 0), 0);
  const totalCartons = items.reduce((sum, item) => sum + (item.cartonCount || 0), 0);
  const hasProblems = items.some((item) => item.packingProblems?.length);

  const closePicker = () => { previewRequest.current++; setPreviewLoading(false); setPickerOpen(false); setSelectedIds([]); };
  const prepareItems = async (ids: number[], closeWhenReady: boolean) => {
    const requestId = ++previewRequest.current;
    setPreviewLoading(true);
    try {
      const prepared = await previewPackingOrder(ids);
      if (requestId !== previewRequest.current) return;
      setItems((current) => prepared.map((item) => {
        const previous = current.find((value) => value.key === item.key);
        return previous ? { ...item, boxSpec: previous.boxSpec, innerBoxTypeId: previous.innerBoxTypeId,
          innerBoxTypeName: previous.innerBoxTypeName, remark: previous.remark } : item;
      }));
      if (closeWhenReady) { setPickerOpen(false); setSelectedIds([]); }
    } catch (error) {
      if (requestId === previewRequest.current) message.error(error instanceof Error ? error.message : "装箱汇总加载失败");
    } finally { if (requestId === previewRequest.current) setPreviewLoading(false); }
  };

  const save = async () => {
    if (!orderDate) { message.warning("请选择日期"); return; }
    if (hasProblems) { message.warning("请先核对有问题的装箱明细，再刷新汇总"); return; }
    const missingBox = items.find((item) => !isCompleteBoxSpec(item.boxSpec));
    if (missingBox) { message.warning(`请展开并填写 ${missingBox.developmentNo || "该款"} 的内盒规格（长、宽、高）`); return; }
    const missingType = items.find((item) => !item.innerBoxTypeId);
    if (missingType) { message.warning(`请展开并选择 ${missingType.developmentNo || "该款"} 的内盒类型`); return; }
    if (!items.length) return;
    setSaving(true);
    try {
      await createPackingOrderTask({ orderDate, items: items.map((item) => ({
        sourceDetailIds: item.sourceDetailIds, boxSpec: item.boxSpec || "", innerBoxTypeId: item.innerBoxTypeId!,
        remark: item.remark || "",
        expectedPackingCapacities: item.packingCapacities, expectedPackingDetailIds: item.sourcePackingDetailIds,
      })) });
      message.success("订包装记录已保存"); setCreateOpen(false); setItems([]); setPage(1); await loadTasks(1);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "订包装记录保存失败");
    } finally { setSaving(false); }
  };

  const viewTask = async (task: PackingOrderTask) => {
    const requestId = ++detailRequest.current;
    setDetailLoadingId(task.id);
    try {
      const data = await fetchPackingOrderTask(task.id);
      if (requestId === detailRequest.current) setDetailTask(data);
    } catch (error) {
      if (requestId === detailRequest.current) message.error(error instanceof Error ? error.message : "订包装明细加载失败");
    } finally { if (requestId === detailRequest.current) setDetailLoadingId(undefined); }
  };
  const taskColumns: ColumnsType<PackingOrderTask> = [
    { title: "日期", dataIndex: "orderDate", width: 120, render: formatEmpty },
    { title: "记录编号", dataIndex: "taskNo", width: 220, render: formatEmpty },
    { title: "订单流水号", dataIndex: "orderNos", width: 180, ellipsis: true, render: formatEmpty },
    { title: "开发款号", dataIndex: "developmentNos", width: 220, ellipsis: true, render: formatEmpty },
    { title: "明细数", dataIndex: "itemCount", width: 90, align: "right", render: formatEmpty },
    { title: "总双数", dataIndex: "totalPairs", width: 100, align: "right", render: formatEmpty },
    { title: "总箱数", dataIndex: "totalCartonCount", width: 100, align: "right", render: formatEmpty },
    { title: "保存时间", dataIndex: "createdAt", width: 170, render: formatDateTime },
    { title: "操作", width: 100, fixed: "right", render: (_, task) => <Button type="link" icon={<EyeOutlined />}
      loading={detailLoadingId === task.id} onClick={() => void viewTask(task)}>查看</Button> },
  ];
  const sourceColumns: ColumnsType<PackingSourceGroup> = [
    { title: "订单明细", dataIndex: "sourceDetailIds", width: 100, render: (ids: number[]) => `${ids.length} 行` },
    { title: "开发款号", dataIndex: "developmentNo", width: 180, render: formatEmpty },
    { title: "楦头", dataIndex: "lastNo", width: 120, render: formatEmpty },
    { title: "双数", dataIndex: "quantity", width: 100, align: "right", render: formatEmpty },
    { title: "箱数", dataIndex: "cartonCount", width: 100, align: "right", render: formatEmpty },
    { title: "箱号范围", dataIndex: "cartonRanges", width: 180, render: formatEmpty },
    { title: "内盒规格", dataIndex: "boxSpec", width: 150, render: formatEmpty },
  ];

  return <div className="workspace packing-order-page">
    <div className="toolbar-band"><div><Typography.Title level={3}>订包装</Typography.Title>
      <Typography.Text type="secondary">查看包装规格和装箱汇总，保存每次订包装记录。</Typography.Text></div>
      <Button type="primary" icon={<FileAddOutlined />} onClick={() => {
        setItems([]); setOrderDate(today()); setCreateOpen(true); previewRequest.current++;
      }}>新建订包装</Button>
    </div>
    <div className="page-panel">
      <Form layout="inline" className="task-filter-form" onFinish={(values) => {
        setFilters({ orderNo: values.orderNo?.trim() || undefined, developmentNo: values.developmentNo?.trim() || undefined }); setPage(1);
      }}>
        <Form.Item label="订单流水号" name="orderNo"><Input allowClear placeholder="请输入订单流水号" /></Form.Item>
        <Form.Item label="开发款号" name="developmentNo"><Input allowClear placeholder="请输入开发款号" /></Form.Item>
        <Form.Item><Space><Button type="primary" htmlType="submit" icon={<SearchOutlined />}>查询</Button>
          <Button icon={<ReloadOutlined />} onClick={() => void loadTasks()}>刷新</Button></Space></Form.Item>
      </Form>
      <Table rowKey="id" columns={taskColumns} dataSource={tasks} loading={loading} pagination={false}
        scroll={{ x: 1350 }} className="data-table" locale={{ emptyText: "暂无订包装记录" }} />
      <Pagination current={page} pageSize={size} total={total} showSizeChanger showTotal={(value) => `共 ${value} 条`}
        onChange={(nextPage, nextSize) => { setPage(nextPage); setSize(nextSize); }} />
    </div>
    <Modal open={createOpen} title="新建订包装" width="96vw" styles={{ body: { maxHeight: "78vh", overflow: "auto" } }}
      onCancel={() => { if (!saving) { closePicker(); setCreateOpen(false); } }}
      footer={<Space><Button disabled={saving} onClick={() => { closePicker(); setCreateOpen(false); }}>取消</Button>
        <Button type="primary" loading={saving} disabled={!items.length || hasProblems || previewLoading} onClick={() => void save()}>保存记录</Button></Space>}>
      <div className="packing-workspace-toolbar"><label className="packing-date-field"><span>日期</span>
        <Input type="date" value={orderDate} disabled={saving} onChange={(event) => setOrderDate(event.target.value)} /></label>
        <Space wrap><Button disabled={saving || previewLoading || !items.length} icon={<ReloadOutlined />}
          onClick={() => void prepareItems(items.flatMap((item) => item.sourceDetailIds), false)}>刷新装箱汇总</Button>
          <Button disabled={saving || previewLoading} onClick={() => {
            setOrderSearch(""); setDevelopmentSearch(""); setPickerOpen(true);
          }}>选择订单款号</Button></Space>
      </div>
      <PackingOrderItems key={createOpen ? "draft-open" : "draft-closed"} items={items} onChange={saving || previewLoading ? undefined : (key, field, value) =>
        setItems((current) => current.map((item) => item.key === key ? { ...item, [field]: value } : item))}
        innerBoxTypes={boxTypes} onReloadTypes={() => void loadBoxTypes()}
        onTypeChange={saving || previewLoading ? undefined : (key, id) => setItems((current) => current.map((item) =>
          item.key === key ? { ...item, innerBoxTypeId: id, innerBoxTypeName: boxTypes.find((type) => type.id === id)?.name } : item))}
        onRemove={saving || previewLoading ? undefined : (key) => setItems((current) => current.filter((item) => item.key !== key))} />
      <div className="packing-order-total"><Typography.Text>{items.length} 条明细 · {totalPairs} 双 · {totalCartons} 箱</Typography.Text>
        {hasProblems && <Typography.Text type="danger">请展开核对装箱明细，修正后刷新汇总</Typography.Text>}</div>
    </Modal>
    <Modal open={pickerOpen} title="选择订单款号" width="85vw" onCancel={closePicker}
      footer={<Space><Button onClick={closePicker}>取消</Button><Button type="primary" loading={previewLoading}
        disabled={!selectedIds.length || sourceLoading} onClick={() => void prepareItems(
          [...new Set([...items.flatMap((item) => item.sourceDetailIds), ...sourceGroups.filter((group) => selectedIds.includes(group.key)).flatMap((group) => group.sourceDetailIds)])], true,
        )}>加入本次订包装</Button></Space>}>
      <Space className="packing-source-filters" wrap>
        <Select showSearch disabled={previewLoading} filterOption={false} placeholder="搜索订单流水号" loading={orderLoading} value={selectedOrder?.id}
          onSearch={setOrderSearch} onChange={(id) => {
            const order = orders.find((item) => item.id === id);
            setSelectedOrder({ id, label: order?.orderNo || `订单 ${id}` });
            setSourceDetails([]); setSelectedIds([]);
          }} options={[
            ...(selectedOrder && !orders.some((order) => order.id === selectedOrder.id) ? [{ value: selectedOrder.id, label: selectedOrder.label }] : []),
            ...orders.map((order) => ({ value: order.id, label: order.orderNo || `订单 ${order.id}` })),
          ]} />
        <Input allowClear disabled={previewLoading} value={developmentSearch} placeholder="筛选开发款号" onChange={(event) => setDevelopmentSearch(event.target.value)} />
      </Space>
      <Table rowKey="key" columns={sourceColumns} dataSource={sourceGroups.filter((item) =>
        !developmentSearch.trim() || item.developmentNo?.toLowerCase().includes(developmentSearch.trim().toLowerCase()))}
        loading={sourceLoading} pagination={{ pageSize: 10, showSizeChanger: false }} scroll={{ x: 820 }} className="data-table"
        locale={{ emptyText: "暂无订单明细，请先在订单列表识别订单" }}
        rowSelection={{ selectedRowKeys: selectedIds, preserveSelectedRowKeys: true,
          getCheckboxProps: (item) => ({ disabled: previewLoading || draftKeys.has(item.key) }), onChange: setSelectedIds }} />
    </Modal>
    <Modal open={Boolean(detailTask)} title={detailTask ? `订包装记录：${detailTask.taskNo}` : "订包装记录"}
      width="96vw" styles={{ body: { maxHeight: "78vh", overflow: "auto" } }} footer={null} onCancel={() => setDetailTask(undefined)}>
      <div className="packing-workspace-toolbar"><Typography.Text>日期：{detailTask?.orderDate || "-"}</Typography.Text>
        <Typography.Text>{detailTask?.totalPairs || 0} 双 · {detailTask?.totalCartonCount || 0} 箱</Typography.Text></div>
      <PackingOrderItems key={detailTask?.id} items={detailTask?.items || []} />
    </Modal>
  </div>;
}
