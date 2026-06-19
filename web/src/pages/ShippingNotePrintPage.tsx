import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  EditOutlined,
  EyeOutlined,
  FileAddOutlined,
  PrinterOutlined,
  ReloadOutlined,
  SearchOutlined,
} from "@ant-design/icons";
import { App, Button, Cascader, Form, Input, Modal, Pagination, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { Key, MouseEvent } from "react";
import { batchRecordPrintProcess, fetchOrderDetails, fetchOrderPackingDetails, fetchOrders } from "../api/orderApi";
import {
  createShippingNoteTask,
  fetchShippingNoteTask,
  fetchShippingNoteTasks,
  updateShippingNoteTask,
} from "../api/shippingNoteApi";
import ShippingNoteSheet, {
  countShippingNoteRows,
  getShippingNotePageCount,
  sumShippingNoteCartons,
  sumShippingNotePairs,
} from "../components/ShippingNoteSheet";
import type {
  DevelopmentNoOption,
  OrderPackingDetail,
  OrderRecord,
  OrderRecordDetail,
  ShippingNoteItem,
  ShippingNoteTask,
  ShippingNoteTaskQueryParams,
} from "../types/order";
import { formatDateTime, formatEmpty } from "../utils/format";
import { getMatchingPackingDetails, hasMatchingPackingDetails } from "../utils/orderMatching";
import { getPackingTotalPairs, sumSizeQuantities as sumPackingSizeQuantities } from "../utils/packingTotals";

const defaultRecipient = "达为鞋业";
const SHIPPING_NOTE_RELATED_PROCESS_TYPES = [5, 6, 7];

function todayText() {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

function buildOrderLabel(order: OrderRecord) {
  return order.orderNo || `订单 ${order.id}`;
}

function pickFirstText(...values: unknown[]) {
  for (const value of values) {
    const text = String(value ?? "").trim();
    if (text) {
      return text;
    }
  }
  return "";
}

function beforeFirstComma(value?: string) {
  const text = String(value ?? "").trim();
  const commaIndex = text.search(/[,，]/);
  return commaIndex >= 0 ? text.slice(0, commaIndex).trim() : text;
}

function sumSizeQuantities(value?: Record<string, number>) {
  return Object.values(value || {}).reduce((total, count) => total + (Number(count) > 0 ? Number(count) : 0), 0);
}

function buildShippingItemFromPacking(
  packingDetail: OrderPackingDetail,
  order?: OrderRecord,
  orderDetail?: OrderRecordDetail,
): ShippingNoteItem {
  const perCartonPairs = sumPackingSizeQuantities(packingDetail.sizeQuantities);
  const totalPairs = getPackingTotalPairs(packingDetail) || perCartonPairs;
  const combinedColorMaterial = [packingDetail.customerColor, packingDetail.material]
    .map((item) => item?.trim())
    .filter(Boolean)
    .join(" ");
  return {
    sourceDetailId: packingDetail.id,
    orderId: packingDetail.orderId,
    orderNo: order ? buildOrderLabel(order) : undefined,
    developmentNo: packingDetail.companyStyleNo,
    customerName: pickFirstText(packingDetail.customerName, order?.customerName),
    customerStyleNo: packingDetail.customerStyleNo,
    englishColor: pickFirstText(packingDetail.customerColor, orderDetail?.englishColor),
    englishMaterial: pickFirstText(packingDetail.material, orderDetail?.englishMaterial),
    colorMaterial: beforeFirstComma(pickFirstText(orderDetail?.upperMaterial, combinedColorMaterial, packingDetail.material, packingDetail.customerColor)),
    trademark: pickFirstText(packingDetail.trademark, orderDetail?.trademark),
    sizeQuantities: packingDetail.sizeQuantities || {},
    pairCount: perCartonPairs || totalPairs,
    cartonCount: packingDetail.cartonCount || 0,
    totalPairs,
    cartonStart: packingDetail.cartonStart,
    cartonEnd: packingDetail.cartonEnd,
  };
}

function buildShippingItemFromOrderDetail(
  detail: OrderRecordDetail,
  order: OrderRecord | undefined,
  packingDetails: OrderPackingDetail[],
): ShippingNoteItem {
  const pairCount = detail.quantity || sumSizeQuantities(detail.sizeQuantities);
  return {
    sourceDetailId: detail.id,
    orderId: detail.orderId,
    orderNo: order ? buildOrderLabel(order) : detail.customerOrderNo,
    developmentNo: detail.developmentNo,
    customerName: pickFirstText(detail.customerName, order?.customerName),
    sizeQuantities: detail.sizeQuantities || {},
    pairCount,
    cartonCount: detail.cartonCount || 0,
    totalPairs: pairCount,
    cartonStart: detail.cartonStart,
    cartonEnd: detail.cartonEnd,
    packingItems: getMatchingPackingDetails(detail, packingDetails).map((packingDetail) =>
      buildShippingItemFromPacking(packingDetail, order, detail),
    ),
  };
}

function getTaskPackingItems(task?: ShippingNoteTask | null) {
  return (task?.items || []).flatMap((item) => item.packingItems || []);
}

function hasMissingPackingItems(items: ShippingNoteItem[]) {
  return items.some((item) => !item.packingItems?.length);
}

function renderSizeQuantities(value?: Record<string, number>) {
  const entries = Object.entries(value || {})
    .filter(([, count]) => Number(count) > 0)
    .sort(([left], [right]) => left.localeCompare(right, "zh-CN", { numeric: true }));
  if (entries.length === 0) {
    return "-";
  }
  return (
    <div className="size-grid">
      {entries.map(([size, count]) => (
        <span key={size}>
          {size}: {count}
        </span>
      ))}
    </div>
  );
}

function renderDevelopmentNos(value?: string) {
  const values = (value || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  if (values.length === 0) {
    return "-";
  }
  return (
    <Space size={[4, 4]} wrap>
      {values.map((item) => (
        <Tag key={item}>{item}</Tag>
      ))}
    </Space>
  );
}

function includesKeyword(value: unknown, keyword: string) {
  return String(value || "")
    .toLocaleLowerCase()
    .includes(keyword.toLocaleLowerCase());
}

function parseDevelopmentNoParts(value: string) {
  const parts = value
    .trim()
    .split("-")
    .map((part) => part.trim())
    .filter(Boolean);
  if (parts.length >= 3) {
    return parts.slice(-3);
  }
  return parts;
}

function sortDevelopmentNoOptions(left: DevelopmentNoOption, right: DevelopmentNoOption) {
  return left.label.localeCompare(right.label, "zh-CN", { numeric: true });
}

function appendDevelopmentNoOption(nodes: DevelopmentNoOption[], parts: string[], path: string[] = []) {
  if (parts.length === 0) {
    return;
  }
  const [part, ...rest] = parts;
  const nextPath = [...path, part];
  let node = nodes.find((item) => item.label === part);
  if (!node) {
    node = {
      value: nextPath.join("-"),
      label: part,
      children: rest.length > 0 ? [] : undefined,
    };
    nodes.push(node);
    nodes.sort(sortDevelopmentNoOptions);
  }
  if (rest.length > 0) {
    if (!node.children) {
      node.children = [];
    }
    appendDevelopmentNoOption(node.children, rest, nextPath);
  }
}

function buildDevelopmentNoOptions(details: OrderRecordDetail[]) {
  const options: DevelopmentNoOption[] = [];
  const seen = new Set<string>();
  details.forEach((item) => {
    const developmentNo = item.developmentNo?.trim();
    if (!developmentNo || seen.has(developmentNo)) {
      return;
    }
    seen.add(developmentNo);
    appendDevelopmentNoOption(options, parseDevelopmentNoParts(developmentNo));
  });
  return options;
}

function applyShippingNotePrintSize() {
  const styleId = "shipping-note-print-page-size";
  let style = document.getElementById(styleId) as HTMLStyleElement | null;
  if (!style) {
    style = document.createElement("style");
    style.id = styleId;
    document.head.appendChild(style);
  }
  style.textContent = "@media print { @page { size: A4 landscape; margin: 0; } }";
}

export default function ShippingNotePrintPage() {
  const { message } = App.useApp();
  const [taskForm] = Form.useForm<ShippingNoteTaskQueryParams>();
  const [tasks, setTasks] = useState<ShippingNoteTask[]>([]);
  const [taskLoading, setTaskLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);

  const [createOpen, setCreateOpen] = useState(false);
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [selectedOrderId, setSelectedOrderId] = useState<number>();
  const [details, setDetails] = useState<OrderRecordDetail[]>([]);
  const [packingDetails, setPackingDetails] = useState<OrderPackingDetail[]>([]);
  const [selectedDetailIds, setSelectedDetailIds] = useState<Key[]>([]);
  const [selectedPrintIds, setSelectedPrintIds] = useState<Key[]>([]);
  const [printItems, setPrintItems] = useState<ShippingNoteItem[]>([]);
  const [developmentNoPaths, setDevelopmentNoPaths] = useState<string[][]>([]);
  const [recipientName, setRecipientName] = useState(defaultRecipient);
  const [shippingDate, setShippingDate] = useState(todayText());
  const [orderLoading, setOrderLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const [detailTask, setDetailTask] = useState<ShippingNoteTask | null>(null);
  const [previewTask, setPreviewTask] = useState<ShippingNoteTask | null>(null);
  const [previewPage, setPreviewPage] = useState(1);
  const [packingPreview, setPackingPreview] = useState<{
    title: string;
    recipientName?: string;
    shippingDate?: string;
    items: ShippingNoteItem[];
  } | null>(null);
  const [taskActionLoadingId, setTaskActionLoadingId] = useState<number>();

  const [editTask, setEditTask] = useState<ShippingNoteTask | null>(null);
  const [editRecipientName, setEditRecipientName] = useState("");
  const [editShippingDate, setEditShippingDate] = useState("");
  const [editSaving, setEditSaving] = useState(false);

  const selectedOrder = useMemo(
    () => orders.find((order) => order.id === selectedOrderId),
    [orders, selectedOrderId],
  );
  const previewItems = useMemo(() => getTaskPackingItems(previewTask), [previewTask]);
  const previewTotalPairs = useMemo(() => sumShippingNotePairs(previewItems), [previewItems]);
  const previewTotalCartonCount = useMemo(() => sumShippingNoteCartons(previewItems), [previewItems]);
  const previewPageCount = useMemo(() => getShippingNotePageCount(previewItems), [previewItems]);

  const loadTasks = useCallback(
    async (nextPage = page, nextSize = size) => {
      setTaskLoading(true);
      try {
        const values = taskForm.getFieldsValue();
        const result = await fetchShippingNoteTasks({
          ...values,
          page: nextPage,
          size: nextSize,
        });
        setTasks(result.records);
        setPage(result.page || nextPage);
        setSize(result.size || nextSize);
        setTotal(result.total);
      } catch (error) {
        setTasks([]);
        message.error(error instanceof Error ? error.message : "出货单任务加载失败");
      } finally {
        setTaskLoading(false);
      }
    },
    [message, page, size, taskForm],
  );

  useEffect(() => {
    void loadTasks(1, size);
  }, []);

  const loadOrders = useCallback(async () => {
    setOrderLoading(true);
    try {
      const result = await fetchOrders({ page: 1, size: 100 });
      setOrders(result.records);
      setSelectedOrderId((current) => current || result.records[0]?.id);
    } catch (error) {
      setOrders([]);
      message.error(error instanceof Error ? error.message : "订单加载失败");
    } finally {
      setOrderLoading(false);
    }
  }, [message]);

  const loadDetails = useCallback(async () => {
    if (!createOpen || !selectedOrderId) {
      setDetails([]);
      setPackingDetails([]);
      return;
    }
    setDetailLoading(true);
    try {
      const [orderDetails, nextPackingDetails] = await Promise.all([
        fetchOrderDetails(selectedOrderId),
        fetchOrderPackingDetails(selectedOrderId),
      ]);
      setDetails(orderDetails);
      setPackingDetails(nextPackingDetails);
      setSelectedDetailIds([]);
      setSelectedPrintIds([]);
      setDevelopmentNoPaths([]);
    } catch (error) {
      setDetails([]);
      setPackingDetails([]);
      message.error(error instanceof Error ? error.message : "订单明细加载失败");
    } finally {
      setDetailLoading(false);
    }
  }, [createOpen, message, selectedOrderId]);

  useEffect(() => {
    void loadDetails();
  }, [loadDetails]);

  const openCreateModal = () => {
    setRecipientName(defaultRecipient);
    setShippingDate(todayText());
    setSelectedDetailIds([]);
    setSelectedPrintIds([]);
    setPrintItems([]);
    setDevelopmentNoPaths([]);
    setCreateOpen(true);
    void loadOrders();
  };

  const closeCreateModal = () => {
    setCreateOpen(false);
    setSelectedDetailIds([]);
    setSelectedPrintIds([]);
    setPrintItems([]);
    setDevelopmentNoPaths([]);
  };

  const printItemKeys = useMemo<Key[]>(() => printItems.map((item) => item.sourceDetailId), [printItems]);
  const printItemKeySet = useMemo(() => new Set<Key>(printItemKeys), [printItemKeys]);
  const leftSelectedRowKeys = useMemo(
    () => Array.from(new Set<Key>([...printItemKeys, ...selectedDetailIds])),
    [printItemKeys, selectedDetailIds],
  );

  const developmentNoOptions = useMemo(() => buildDevelopmentNoOptions(details), [details]);
  const selectedDevelopmentNos = useMemo(() => {
    return new Set(
      developmentNoPaths
        .map((path) => path[path.length - 1])
        .filter((value): value is string => Boolean(value)),
    );
  }, [developmentNoPaths]);

  const filteredDetails = useMemo(() => {
    if (selectedDevelopmentNos.size === 0) {
      return details;
    }
    return details.filter((item) =>
      Array.from(selectedDevelopmentNos).some((developmentNo) =>
        includesKeyword(item.developmentNo, developmentNo),
      ),
    );
  }, [details, selectedDevelopmentNos]);

  const hasMatchedPacking = useCallback(
    (detail: OrderRecordDetail) => hasMatchingPackingDetails(detail, packingDetails),
    [packingDetails],
  );

  const addPrintItems = (items: OrderRecordDetail[]) => {
    const eligibleItems = items.filter(hasMatchedPacking);
    const blockedCount = items.length - eligibleItems.length;
    if (blockedCount > 0) {
      message.warning(`${blockedCount} 条订单明细没有对应的装箱单明细，不能移动到右侧`);
    }
    if (eligibleItems.length === 0) {
      setSelectedPrintIds([]);
      return;
    }
    setPrintItems((current) => {
      const currentIdSet = new Set(current.map((item) => item.sourceDetailId));
      const nextItems = eligibleItems
        .filter((item) => !currentIdSet.has(item.id))
        .map((item) => buildShippingItemFromOrderDetail(item, selectedOrder, packingDetails));
      if (nextItems.length === 0) {
        return current;
      }
      return [...current, ...nextItems];
    });
    setSelectedPrintIds([]);
  };

  const addSelectedPrintItems = () => {
    const selectedSet = new Set(selectedDetailIds);
    addPrintItems(details.filter((item) => selectedSet.has(item.id)));
    setSelectedDetailIds([]);
  };

  const removePrintItems = () => {
    const selectedSet = new Set(selectedPrintIds);
    setPrintItems((current) => current.filter((item) => !selectedSet.has(item.sourceDetailId)));
    setSelectedPrintIds([]);
  };

  const saveTask = async () => {
    if (printItems.length === 0) {
      message.warning("请先选择要保存的出货单明细");
      return;
    }
    if (printItems.some((item) => !item.packingItems?.length)) {
      message.warning("出货单明细必须有对应的装箱单明细，不能保存任务");
      return;
    }
    setSaving(true);
    try {
      await createShippingNoteTask({
        recipientName,
        shippingDate,
        items: printItems.map((item) => ({
          sourceDetailId: item.sourceDetailId,
          orderId: item.orderId,
        })),
      });
      message.success("出货单任务已保存");
      closeCreateModal();
      await loadTasks(1, size);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "出货单任务保存失败");
    } finally {
      setSaving(false);
    }
  };

  const loadTaskDetail = async (task: ShippingNoteTask) => {
    setTaskActionLoadingId(task.id);
    try {
      return await fetchShippingNoteTask(task.id);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "出货单任务详情加载失败");
      return null;
    } finally {
      setTaskActionLoadingId(undefined);
    }
  };

  const openEditModal = (task: ShippingNoteTask) => {
    setEditTask(task);
    setEditRecipientName(task.recipientName || "");
    setEditShippingDate(task.shippingDate || "");
  };

  const closeEditModal = () => {
    setEditTask(null);
    setEditRecipientName("");
    setEditShippingDate("");
    setEditSaving(false);
  };

  const saveEditTask = async () => {
    if (!editTask) {
      return;
    }
    setEditSaving(true);
    try {
      await updateShippingNoteTask(editTask.id, {
        recipientName: editRecipientName,
        shippingDate: editShippingDate || undefined,
      });
      message.success("出货单已更新");
      closeEditModal();
      await loadTasks(page, size);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "出货单更新失败");
    } finally {
      setEditSaving(false);
    }
  };

  const ensureShippingItemsCanPrint = (items: ShippingNoteItem[]) => {
    if (hasMissingPackingItems(items)) {
      message.warning("出货单明细必须有对应的装箱单明细，不能预览打印");
      return false;
    }
    return true;
  };

  const openTaskDetail = async (task: ShippingNoteTask) => {
    const detail = await loadTaskDetail(task);
    if (detail) {
      setDetailTask(detail);
    }
  };

  const openTaskPreview = async (task: ShippingNoteTask) => {
    const detail = await loadTaskDetail(task);
    if (detail) {
      if (!ensureShippingItemsCanPrint(detail.items || [])) {
        return;
      }
      setPreviewPage(1);
      setPreviewTask(detail);
    }
  };

  useEffect(() => {
    setPreviewPage((current) => Math.min(Math.max(current, 1), previewPageCount));
  }, [previewPageCount]);

  const recordShippingNotePrinted = async (items: ShippingNoteItem[]) => {
    const orderGroups = new Map<number, Set<number>>();
    items.forEach((item) => {
      const orderId = item.orderId;
      const detailId = item.sourceDetailId;
      if (!orderId || !detailId) {
        return;
      }
      if (!orderGroups.has(orderId)) {
        orderGroups.set(orderId, new Set());
      }
      orderGroups.get(orderId)!.add(detailId);
    });

    if (orderGroups.size === 0) {
      throw new Error("出货单明细缺少订单信息，不能记录打印状态");
    }

    await Promise.all(
      SHIPPING_NOTE_RELATED_PROCESS_TYPES.flatMap((processType) =>
        Array.from(orderGroups.entries()).map(([orderId, detailIds]) =>
          batchRecordPrintProcess(orderId, Array.from(detailIds), processType),
        ),
      ),
    );
  };

  const printPreviewTask = async () => {
    if (!previewTask) {
      return;
    }
    if (!ensureShippingItemsCanPrint(previewTask.items || [])) {
      return;
    }
    try {
      await recordShippingNotePrinted(previewTask.items || []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "出货单打印状态记录失败");
      return;
    }
    applyShippingNotePrintSize();
    window.setTimeout(() => window.print(), 0);
  };

  const openPackingPreview = (record: ShippingNoteItem) => {
    const items = record.packingItems || [];
    if (items.length === 0) {
      message.warning("这条订单明细暂未匹配到装箱单明细");
      return;
    }
    setPackingPreview({
      title: `${record.orderNo || "-"} / ${record.developmentNo || "-"}`,
      recipientName: detailTask?.recipientName || recipientName,
      shippingDate: detailTask?.shippingDate || shippingDate,
      items,
    });
  };

  const detailColumns = useMemo<ColumnsType<OrderRecordDetail>>(
    () => [
      { title: "发票编号", width: 130, render: () => formatEmpty(selectedOrder ? buildOrderLabel(selectedOrder) : "") },
      { title: "开发编号", dataIndex: "developmentNo", width: 140, render: formatEmpty },
      { title: "尺码数量", dataIndex: "sizeQuantities", width: 220, render: renderSizeQuantities },
      { title: "双数", dataIndex: "quantity", width: 80, align: "right", render: formatEmpty },
      { title: "件数", dataIndex: "cartonCount", width: 80, align: "right", render: formatEmpty },
      { title: "开始箱号", dataIndex: "cartonStart", width: 110, render: formatEmpty },
      { title: "结束箱号", dataIndex: "cartonEnd", width: 110, render: formatEmpty },
    ],
    [selectedOrder],
  );

  const printColumns = useMemo<ColumnsType<ShippingNoteItem>>(
    () => [
      { title: "发票编号", dataIndex: "orderNo", width: 130, render: formatEmpty },
      { title: "开发编号", dataIndex: "developmentNo", width: 140, render: formatEmpty },
      { title: "尺码数量", dataIndex: "sizeQuantities", width: 220, render: renderSizeQuantities },
      { title: "双数", dataIndex: "pairCount", width: 80, align: "right", render: formatEmpty },
      { title: "件数", dataIndex: "cartonCount", width: 80, align: "right", render: formatEmpty },
      { title: "开始箱号", dataIndex: "cartonStart", width: 110, render: formatEmpty },
      { title: "结束箱号", dataIndex: "cartonEnd", width: 110, render: formatEmpty },
      {
        title: "出货单",
        key: "shippingNote",
        width: 120,
        fixed: "right",
        render: (_, record) => (
          <Button
            size="small"
            icon={<EyeOutlined />}
            disabled={!record.packingItems?.length}
            onClick={() => openPackingPreview(record)}
          >
            查看
          </Button>
        ),
      },
    ],
    [detailTask, recipientName, shippingDate],
  );

  const packingColumns = useMemo<ColumnsType<ShippingNoteItem>>(
    () => [
      { title: "发票编号", dataIndex: "orderNo", width: 130, render: formatEmpty },
      { title: "开发编号", dataIndex: "developmentNo", width: 140, render: formatEmpty },
      { title: "颜色/材质", dataIndex: "colorMaterial", width: 180, render: formatEmpty },
      { title: "尺码数量", dataIndex: "sizeQuantities", width: 220, render: renderSizeQuantities },
      { title: "双数", dataIndex: "totalPairs", width: 80, align: "right", render: formatEmpty },
      { title: "件数", dataIndex: "cartonCount", width: 80, align: "right", render: formatEmpty },
      { title: "开始箱号", dataIndex: "cartonStart", width: 110, render: formatEmpty },
      { title: "结束箱号", dataIndex: "cartonEnd", width: 110, render: formatEmpty },
    ],
    [],
  );

  const taskColumns = useMemo<ColumnsType<ShippingNoteTask>>(
    () => [
      { title: "任务编号", dataIndex: "taskNo", width: 160, fixed: "left", render: (_, record) => record.taskNo || record.printNo || "-" },
      { title: "发票编号", dataIndex: "invoiceNos", width: 220, render: renderDevelopmentNos },
      { title: "收货单位", dataIndex: "recipientName", width: 140, render: formatEmpty },
      { title: "出货日期", dataIndex: "shippingDate", width: 120, render: formatEmpty },
      { title: "开发编号", dataIndex: "developmentNos", minWidth: 220, render: renderDevelopmentNos },
      { title: "明细行", dataIndex: "itemCount", width: 90, align: "right", render: formatEmpty },
      { title: "件数", dataIndex: "totalCartonCount", width: 90, align: "right", render: formatEmpty },
      { title: "双数", dataIndex: "totalPairs", width: 90, align: "right", render: formatEmpty },
      { title: "保存时间", dataIndex: "createdAt", width: 170, render: formatDateTime },
      {
        title: "操作",
        key: "actions",
        width: 280,
        fixed: "right",
        render: (_, record) => (
          <Space size={8}>
            <Button
              icon={<EditOutlined />}
              onClick={() => openEditModal(record)}
            >
              编辑
            </Button>
            <Button
              icon={<EyeOutlined />}
              loading={taskActionLoadingId === record.id}
              onClick={() => void openTaskDetail(record)}
            >
              查看明细
            </Button>
            <Button
              type="primary"
              icon={<PrinterOutlined />}
              loading={taskActionLoadingId === record.id}
              onClick={() => void openTaskPreview(record)}
            >
              预览
            </Button>
          </Space>
        ),
      },
    ],
    [taskActionLoadingId],
  );

  const leftRowSelection = {
    selectedRowKeys: leftSelectedRowKeys,
    getCheckboxProps: (record: OrderRecordDetail) => ({
      disabled: printItemKeySet.has(record.id) || !hasMatchedPacking(record),
    }),
    onChange: (nextKeys: Key[]) => {
      setSelectedDetailIds(nextKeys.filter((key) => {
        const detail = details.find((item) => item.id === key);
        return detail && !printItemKeySet.has(key) && hasMatchedPacking(detail);
      }));
    },
  };

  const leftRowClassName = (record: OrderRecordDetail) => {
    return printItemKeySet.has(record.id) || !hasMatchedPacking(record)
      ? "print-transfer-row-disabled"
      : "print-transfer-row-clickable";
  };

  const getLeftRowProps = (record: OrderRecordDetail) => ({
    onClick: (event: MouseEvent<HTMLElement>) => {
      const target = event.target as HTMLElement;
      if (target.closest(".ant-checkbox-wrapper, .ant-table-selection-column, button, a")) {
        return;
      }
      if (printItemKeySet.has(record.id)) {
        return;
      }
      if (!hasMatchedPacking(record)) {
        message.warning("这条订单明细没有对应的装箱单明细，不能移动到右侧");
        return;
      }
      setSelectedDetailIds((current) =>
        current.includes(record.id) ? current.filter((key) => key !== record.id) : [...current, record.id],
      );
    },
  });

  return (
    <div className="workspace">
      <div className="toolbar-band">
        <div>
          <Typography.Title level={3}>打印出货单</Typography.Title>
          <Typography.Text type="secondary">
            保存每次出货单打印任务，可查看明细并按任务快照预览打印。
          </Typography.Text>
        </div>
        <Space wrap>
          <Button type="primary" icon={<FileAddOutlined />} onClick={openCreateModal}>
            新建出货单
          </Button>
          <Button icon={<ReloadOutlined />} onClick={() => void loadTasks(1, size)}>
            刷新
          </Button>
        </Space>
      </div>

      <div className="page-panel">
        <Form
          className="filter-form"
          form={taskForm}
          layout="vertical"
          onFinish={() => void loadTasks(1, size)}
        >
          <Form.Item label="发票编号" name="orderNo">
            <Input allowClear placeholder="输入发票编号" />
          </Form.Item>
          <Form.Item label="开发编号" name="developmentNo">
            <Input allowClear placeholder="输入开发编号" />
          </Form.Item>
          <Form.Item label=" " colon={false}>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
              查询
            </Button>
          </Form.Item>
        </Form>

        <Table
          rowKey="id"
          loading={taskLoading}
          columns={taskColumns}
          dataSource={tasks}
          pagination={{
            current: page,
            pageSize: size,
            total,
            showSizeChanger: true,
            onChange: (nextPage, nextSize) => void loadTasks(nextPage, nextSize),
          }}
          scroll={{ x: 1520 }}
          className="data-table"
        />
      </div>

      <Modal
        open={createOpen}
        title="新建出货单"
        onCancel={closeCreateModal}
        width={1500}
        footer={[
          <Button key="close" onClick={closeCreateModal}>
            取消
          </Button>,
          <Button key="save" type="primary" loading={saving} disabled={!printItems.length} onClick={() => void saveTask()}>
            保存任务
          </Button>,
        ]}
        destroyOnClose
      >
        <div className="shipping-note-task-form">
          <Space wrap>
            <Input
              addonBefore="收货单位"
              value={recipientName}
              onChange={(event) => setRecipientName(event.target.value)}
            />
            <Input
              className="shipping-note-date-input"
              type="date"
              value={shippingDate}
              onChange={(event) => setShippingDate(event.target.value)}
            />
          </Space>
        </div>
        <div className="print-transfer-layout">
          <div className="page-panel print-transfer-panel">
            <div className="print-transfer-heading">
              <div className="print-transfer-heading-main">
                <Typography.Title level={4}>订单明细</Typography.Title>
                <Space className="print-transfer-filters" wrap>
                  <Select
                    className="print-order-select"
                    loading={orderLoading}
                    placeholder="订单号"
                    showSearch
                    optionFilterProp="label"
                    value={selectedOrderId}
                    options={orders.map((order) => ({ value: order.id, label: buildOrderLabel(order) }))}
                    onChange={(value) => setSelectedOrderId(value)}
                  />
                  <Cascader
                    allowClear
                    className="print-development-cascader"
                    displayRender={(labels) => labels.join("-")}
                    maxTagCount="responsive"
                    multiple
                    options={developmentNoOptions}
                    placeholder="开发编号"
                    showCheckedStrategy={Cascader.SHOW_CHILD}
                    showSearch
                    value={developmentNoPaths}
                    onChange={(value) => setDevelopmentNoPaths(value as string[][])}
                  />
                  <Button icon={<ReloadOutlined />} onClick={() => void loadDetails()}>
                    刷新明细
                  </Button>
                </Space>
              </div>
              <Typography.Text type="secondary">{filteredDetails.length} 条明细</Typography.Text>
            </div>
            <Table
              rowKey="id"
              loading={detailLoading}
              columns={detailColumns}
              dataSource={filteredDetails}
              pagination={{ pageSize: 10, showSizeChanger: false }}
              scroll={{ x: 920 }}
              className="data-table"
              rowClassName={leftRowClassName}
              rowSelection={leftRowSelection}
              onRow={getLeftRowProps}
            />
          </div>

          <div className="print-transfer-actions">
            <Button
              type="primary"
              icon={<ArrowRightOutlined />}
              disabled={!selectedDetailIds.length}
              onClick={addSelectedPrintItems}
            />
            <Button
              icon={<ArrowLeftOutlined />}
              disabled={!selectedPrintIds.length}
              onClick={removePrintItems}
            />
          </div>

          <div className="page-panel print-transfer-panel">
            <div className="print-transfer-heading">
              <div className="print-transfer-heading-main">
                <Typography.Title level={4}>待保存出货单</Typography.Title>
              </div>
              <Typography.Text type="secondary">
                {countShippingNoteRows(printItems)} 行，{sumShippingNoteCartons(printItems)} 件，{sumShippingNotePairs(printItems)} 双
              </Typography.Text>
            </div>
            <Table
              rowKey="sourceDetailId"
              columns={printColumns}
              dataSource={printItems}
              pagination={{ pageSize: 10, showSizeChanger: false }}
              scroll={{ x: 1040 }}
              className="data-table"
              rowSelection={{
                selectedRowKeys: selectedPrintIds,
                onChange: setSelectedPrintIds,
              }}
            />
          </div>
        </div>
      </Modal>

      <Modal
        open={Boolean(detailTask)}
        title={detailTask ? `出货单明细：${detailTask.taskNo || detailTask.printNo}` : "出货单明细"}
        onCancel={() => setDetailTask(null)}
        width={1220}
        footer={[
          <Button key="close" onClick={() => setDetailTask(null)}>
            关闭
          </Button>,
        ]}
        destroyOnClose
      >
        <Table
          rowKey={(record, index) => `${record.sourceDetailId}-${index}`}
          columns={printColumns}
          dataSource={detailTask?.items || []}
          pagination={{ pageSize: 10, showSizeChanger: false }}
          scroll={{ x: 1040 }}
          className="data-table"
        />
      </Modal>

      <Modal
        open={Boolean(packingPreview)}
        title={packingPreview ? `装箱单明细：${packingPreview.title}` : "装箱单明细"}
        onCancel={() => setPackingPreview(null)}
        width={1220}
        footer={[
          <Button key="close" onClick={() => setPackingPreview(null)}>
            关闭
          </Button>,
        ]}
        destroyOnClose
      >
        <Table
          rowKey={(record, index) => `${record.sourceDetailId}-${index}`}
          columns={packingColumns}
          dataSource={packingPreview?.items || []}
          pagination={false}
          scroll={{ x: 1050 }}
          size="small"
          className="data-table"
        />
      </Modal>

      <Modal
        open={Boolean(editTask)}
        title={editTask ? `编辑出货单：${editTask.taskNo || editTask.printNo}` : "编辑出货单"}
        onCancel={closeEditModal}
        width={520}
        footer={[
          <Button key="cancel" onClick={closeEditModal}>
            取消
          </Button>,
          <Button key="save" type="primary" loading={editSaving} onClick={() => void saveEditTask()}>
            保存
          </Button>,
        ]}
        destroyOnClose
      >
        <Form layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="收货单位">
            <Input
              value={editRecipientName}
              onChange={(event) => setEditRecipientName(event.target.value)}
            />
          </Form.Item>
          <Form.Item label="出货日期">
            <Input
              type="date"
              value={editShippingDate}
              onChange={(event) => setEditShippingDate(event.target.value)}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={Boolean(previewTask)}
        title={previewTask ? `出货单预览：${previewTask.taskNo || previewTask.printNo}` : "出货单预览"}
        onCancel={() => setPreviewTask(null)}
        width={1220}
        footer={[
          <Button key="close" onClick={() => setPreviewTask(null)}>
            关闭
          </Button>,
          <Button key="print" type="primary" icon={<PrinterOutlined />} onClick={() => void printPreviewTask()}>
            打印
          </Button>,
        ]}
        destroyOnClose
      >
        {previewTask ? (
          <div className="shipping-note-preview">
            <ShippingNoteSheet
              recipientName={previewTask.recipientName}
              shippingDate={previewTask.shippingDate}
              items={previewItems}
              totalPairs={previewTotalPairs}
              totalCartonCount={previewTotalCartonCount}
              pageIndex={previewPage - 1}
            />
          </div>
        ) : null}
        {previewTask && previewPageCount > 1 ? (
          <div className="label-preview-pagination">
            <Pagination
              current={previewPage}
              pageSize={1}
              total={previewPageCount}
              showSizeChanger={false}
              onChange={setPreviewPage}
            />
          </div>
        ) : null}
      </Modal>

      <div className="shipping-note-print-root" aria-hidden>
        {previewTask ? (
          <ShippingNoteSheet
            recipientName={previewTask.recipientName}
            shippingDate={previewTask.shippingDate}
            items={previewItems}
            totalPairs={previewTotalPairs}
            totalCartonCount={previewTotalCartonCount}
          />
        ) : null}
      </div>
    </div>
  );
}
