import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  FileAddOutlined,
  PrinterOutlined,
  ReloadOutlined,
  SearchOutlined,
} from "@ant-design/icons";
import { App, Button, Cascader, Checkbox, Form, Input, Modal, Pagination, Popover, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { Key, MouseEvent } from "react";
import { batchRecordPrintProcess, fetchOrderDetails, fetchOrderPackingDetails, fetchOrders } from "../api/orderApi";
import {
  createShippingNoteTask,
  deleteShippingNoteTask,
  fetchShippingNoteGeneratedSummary,
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
  ShippingNoteGeneratedSummary,
  ShippingNoteTask,
  ShippingNoteTaskQueryParams,
} from "../types/order";
import { formatDateTime, formatEmpty } from "../utils/format";
import { getMatchingPackingDetails, hasMatchingPackingDetails } from "../utils/orderMatching";
import { getPackingTotalPairs, sumSizeQuantities as sumPackingSizeQuantities } from "../utils/packingTotals";

const defaultRecipient = "达为鞋业";
const SHIPPING_NOTE_RELATED_PROCESS_TYPES = [5, 6, 7];
const CODE_TAG_PREVIEW_LIMIT = 1;
const emptyGeneratedSummary: ShippingNoteGeneratedSummary = {
  generatedDetailIds: [],
  fullyGeneratedOrderIds: [],
};

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
  const previewValues = values.slice(0, CODE_TAG_PREVIEW_LIMIT);
  const hiddenCount = Math.max(values.length - previewValues.length, 0);
  const shouldShowPopover = hiddenCount > 0 || values.some((item) => item.length > 14);
  const preview = (
    <Space size={[4, 4]} wrap className="development-no-preview">
      {previewValues.map((item) => (
        <Tag key={item} className="development-no-tag">
          {item}
        </Tag>
      ))}
      {hiddenCount > 0 ? (
        <Tag color="blue" className="development-no-count-tag">
          +{hiddenCount}
        </Tag>
      ) : null}
    </Space>
  );

  if (!shouldShowPopover) {
    return preview;
  }

  return (
    <Popover
      content={
        <Space size={[4, 4]} wrap className="development-no-popover">
          {values.map((item) => (
            <Tag key={item} className="development-no-tag">
              {item}
            </Tag>
          ))}
        </Space>
      }
      mouseEnterDelay={0.2}
      title={`编号（${values.length}）`}
    >
      {preview}
    </Popover>
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
  const { message, modal } = App.useApp();
  const [taskForm] = Form.useForm<ShippingNoteTaskQueryParams>();
  const [tasks, setTasks] = useState<ShippingNoteTask[]>([]);
  const [taskLoading, setTaskLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);

  const [taskEditorOpen, setTaskEditorOpen] = useState(false);
  const [editTask, setEditTask] = useState<ShippingNoteTask | null>(null);
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [selectedOrderId, setSelectedOrderId] = useState<number>();
  const [details, setDetails] = useState<OrderRecordDetail[]>([]);
  const [packingDetails, setPackingDetails] = useState<OrderPackingDetail[]>([]);
  const [selectedDetailIds, setSelectedDetailIds] = useState<Key[]>([]);
  const [selectedPrintIds, setSelectedPrintIds] = useState<Key[]>([]);
  const [printItems, setPrintItems] = useState<ShippingNoteItem[]>([]);
  const [developmentNoPaths, setDevelopmentNoPaths] = useState<string[][]>([]);
  const [hideGeneratedShippingNotes, setHideGeneratedShippingNotes] = useState(true);
  const [generatedSummary, setGeneratedSummary] = useState<ShippingNoteGeneratedSummary>(emptyGeneratedSummary);
  const [recipientName, setRecipientName] = useState(defaultRecipient);
  const [shippingDate, setShippingDate] = useState(todayText());
  const [orderLoading, setOrderLoading] = useState(false);
  const [generatedSummaryLoading, setGeneratedSummaryLoading] = useState(false);
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
  const [deletingTaskId, setDeletingTaskId] = useState<number>();

  const selectedOrder = useMemo(
    () => orders.find((order) => order.id === selectedOrderId),
    [orders, selectedOrderId],
  );
  const generatedDetailIdSet = useMemo(
    () => new Set(generatedSummary.generatedDetailIds || []),
    [generatedSummary.generatedDetailIds],
  );
  const fullyGeneratedOrderIdSet = useMemo(
    () => new Set(generatedSummary.fullyGeneratedOrderIds || []),
    [generatedSummary.fullyGeneratedOrderIds],
  );
  const originalDetailIdSet = useMemo(
    () => new Set((editTask?.items || []).map((item) => item.sourceDetailId)),
    [editTask],
  );
  const originalOrderIdSet = useMemo(
    () => new Set((editTask?.items || []).map((item) => item.orderId)),
    [editTask],
  );
  const visibleOrders = useMemo(() => {
    if (!hideGeneratedShippingNotes) {
      return orders;
    }
    return orders.filter((order) => {
      const partOrderIds = order.partOrderIds?.length ? order.partOrderIds : [order.id];
      return partOrderIds.some((orderId) => originalOrderIdSet.has(orderId))
        || !partOrderIds.every((orderId) => fullyGeneratedOrderIdSet.has(orderId));
    });
  }, [fullyGeneratedOrderIdSet, hideGeneratedShippingNotes, orders, originalOrderIdSet]);
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

  const loadOrders = useCallback(async (initialOrderId?: number) => {
    setOrderLoading(true);
    try {
      const result = await fetchOrders({ page: 1, size: 100, sourceType: 1 });
      setOrders(result.records);
      if (initialOrderId) {
        const initialOrder = result.records.find((order) =>
          order.id === initialOrderId || order.partOrderIds?.includes(initialOrderId),
        );
        setSelectedOrderId(initialOrder?.id);
      }
    } catch (error) {
      setOrders([]);
      message.error(error instanceof Error ? error.message : "订单加载失败");
    } finally {
      setOrderLoading(false);
    }
  }, [message]);

  const loadGeneratedSummary = useCallback(
    async (nextOrders = orders) => {
      if (!hideGeneratedShippingNotes) {
        setGeneratedSummaryLoading(false);
        setGeneratedSummary(emptyGeneratedSummary);
        return;
      }
      const orderIds = Array.from(new Set(
        nextOrders.flatMap((order) => order.partOrderIds?.length ? order.partOrderIds : [order.id]),
      )).filter((id): id is number => Boolean(id));
      if (orderIds.length === 0) {
        setGeneratedSummaryLoading(false);
        setGeneratedSummary(emptyGeneratedSummary);
        return;
      }

      setGeneratedSummaryLoading(true);
      try {
        setGeneratedSummary(await fetchShippingNoteGeneratedSummary(orderIds));
      } catch (error) {
        setGeneratedSummary(emptyGeneratedSummary);
        message.error(error instanceof Error ? error.message : "出货单生成情况加载失败");
      } finally {
        setGeneratedSummaryLoading(false);
      }
    },
    [hideGeneratedShippingNotes, message, orders],
  );

  useEffect(() => {
    if (!taskEditorOpen) {
      return;
    }
    void loadGeneratedSummary();
  }, [taskEditorOpen, hideGeneratedShippingNotes, loadGeneratedSummary, orders]);

  useEffect(() => {
    if (!taskEditorOpen || generatedSummaryLoading) {
      return;
    }
    setSelectedOrderId((current) => {
      if (current && visibleOrders.some((order) => order.id === current)) {
        return current;
      }
      return visibleOrders[0]?.id;
    });
  }, [taskEditorOpen, generatedSummaryLoading, visibleOrders]);

  const loadDetails = useCallback(async () => {
    if (!taskEditorOpen || !selectedOrderId) {
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
  }, [taskEditorOpen, message, selectedOrderId]);

  useEffect(() => {
    void loadDetails();
  }, [loadDetails]);

  const openTaskEditor = (task: ShippingNoteTask | null = null) => {
    setEditTask(task);
    setRecipientName(task?.recipientName || defaultRecipient);
    setShippingDate(task?.shippingDate || todayText());
    setOrders([]);
    setSelectedOrderId(undefined);
    setDetails([]);
    setPackingDetails([]);
    setSelectedDetailIds([]);
    setSelectedPrintIds([]);
    setPrintItems(task?.items || []);
    setDevelopmentNoPaths([]);
    setHideGeneratedShippingNotes(true);
    setGeneratedSummary(emptyGeneratedSummary);
    setTaskEditorOpen(true);
    void loadOrders(task?.items[0]?.orderId);
  };

  const closeTaskEditor = () => {
    if (saving) {
      return;
    }
    resetTaskEditor();
  };

  const resetTaskEditor = () => {
    setTaskEditorOpen(false);
    setEditTask(null);
    setSelectedDetailIds([]);
    setSelectedPrintIds([]);
    setPrintItems([]);
    setDevelopmentNoPaths([]);
    setGeneratedSummary(emptyGeneratedSummary);
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
    const baseDetails = hideGeneratedShippingNotes
      ? details.filter((item) => originalDetailIdSet.has(item.id) || !generatedDetailIdSet.has(item.id))
      : details;
    if (selectedDevelopmentNos.size === 0) {
      return baseDetails;
    }
    return baseDetails.filter((item) =>
      Array.from(selectedDevelopmentNos).some((developmentNo) =>
        includesKeyword(item.developmentNo, developmentNo),
      ),
    );
  }, [details, generatedDetailIdSet, hideGeneratedShippingNotes, originalDetailIdSet, selectedDevelopmentNos]);

  useEffect(() => {
    const visibleDetailIdSet = new Set(filteredDetails.map((item) => item.id));
    setSelectedDetailIds((current) => {
      const next = current.filter((key) => visibleDetailIdSet.has(Number(key)));
      return next.length === current.length ? current : next;
    });
  }, [filteredDetails]);

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
      const payload = {
        recipientName,
        shippingDate,
        items: printItems.map((item) => ({
          sourceDetailId: item.sourceDetailId,
          orderId: item.orderId,
        })),
      };
      if (editTask) {
        await updateShippingNoteTask(editTask.id, payload);
      } else {
        await createShippingNoteTask(payload);
      }
      message.success(editTask ? "出货单已更新" : "出货单任务已保存");
      resetTaskEditor();
      await loadTasks(editTask ? page : 1, size);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "出货单任务保存失败");
    } finally {
      setSaving(false);
    }
  };

  const confirmDeleteTask = useCallback((task: ShippingNoteTask) => {
    modal.confirm({
      title: "确认删除出货单？",
      content: (
        <div>
          <p>出货单号：{task.taskNo || task.printNo || task.id}</p>
          <p>删除后无法撤销，出货统计和月度对账将同步更新。原始订单和装箱单会保留。</p>
          <p>未关联其他出货单的明细将同步清除“出货单打印”处理记录，内盒、外箱贴标记录保留。</p>
        </div>
      ),
      okText: "确认删除",
      cancelText: "取消",
      okButtonProps: { danger: true },
      onOk: async () => {
        setDeletingTaskId(task.id);
        try {
          await deleteShippingNoteTask(task.id);
          message.success("出货单已删除");
          const nextPage = tasks.length === 1 && page > 1 ? page - 1 : page;
          await loadTasks(nextPage, size);
        } catch (error) {
          message.error(error instanceof Error ? error.message : "出货单删除失败");
          throw error;
        } finally {
          setDeletingTaskId(undefined);
        }
      },
    });
  }, [loadTasks, message, modal, page, size, tasks.length]);

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

  const openEditModal = async (task: ShippingNoteTask) => {
    const fullTask = await loadTaskDetail(task);
    if (!fullTask) {
      return;
    }
    setDetailTask(null);
    setPreviewTask(null);
    openTaskEditor(fullTask);
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
      { title: "出货日期", dataIndex: "shippingDate", width: 120, fixed: "left", render: formatEmpty },
      { title: "发票编号", dataIndex: "invoiceNos", width: 170, render: renderDevelopmentNos },
      { title: "收货单位", dataIndex: "recipientName", width: 140, render: formatEmpty },
      { title: "开发编号", dataIndex: "developmentNos", width: 170, render: renderDevelopmentNos },
      { title: "明细行", dataIndex: "itemCount", width: 90, align: "right", render: formatEmpty },
      { title: "件数", dataIndex: "totalCartonCount", width: 90, align: "right", render: formatEmpty },
      { title: "双数", dataIndex: "totalPairs", width: 90, align: "right", render: formatEmpty },
      { title: "保存时间", dataIndex: "createdAt", width: 170, render: formatDateTime },
      {
        title: "操作",
        key: "actions",
        width: 390,
        fixed: "right",
        render: (_, record) => (
          <Space size={8}>
            <Button
              icon={<EditOutlined />}
              loading={taskActionLoadingId === record.id}
              disabled={deletingTaskId !== undefined}
              onClick={() => void openEditModal(record)}
            >
              编辑
            </Button>
            <Button
              icon={<EyeOutlined />}
              loading={taskActionLoadingId === record.id}
              disabled={deletingTaskId !== undefined}
              onClick={() => void openTaskDetail(record)}
            >
              查看明细
            </Button>
            <Button
              type="primary"
              icon={<PrinterOutlined />}
              loading={taskActionLoadingId === record.id}
              disabled={deletingTaskId !== undefined}
              onClick={() => void openTaskPreview(record)}
            >
              预览
            </Button>
            <Button
              danger
              icon={<DeleteOutlined />}
              loading={deletingTaskId === record.id}
              disabled={deletingTaskId !== undefined || taskActionLoadingId !== undefined}
              onClick={() => confirmDeleteTask(record)}
            >
              删除
            </Button>
          </Space>
        ),
      },
    ],
    [confirmDeleteTask, deletingTaskId, taskActionLoadingId],
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
            保存每次出货单打印任务，可编辑出货明细并预览打印。
          </Typography.Text>
        </div>
        <Space wrap>
          <Button type="primary" icon={<FileAddOutlined />} onClick={() => openTaskEditor()}>
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
          <Form.Item label="开始时间" name="startDate">
            <Input allowClear type="date" />
          </Form.Item>
          <Form.Item label="结束时间" name="endDate">
            <Input allowClear type="date" />
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
          scroll={{ x: 1470 }}
          className="data-table"
        />
      </div>

      <Modal
        open={taskEditorOpen}
        title={editTask ? `编辑出货单：${editTask.taskNo || editTask.printNo}` : "新建出货单"}
        onCancel={closeTaskEditor}
        closable={!saving}
        maskClosable={!saving}
        width={1500}
        footer={[
          <Button key="close" disabled={saving} onClick={closeTaskEditor}>
            取消
          </Button>,
          <Button key="save" type="primary" loading={saving} disabled={!printItems.length} onClick={() => void saveTask()}>
            {editTask ? "保存修改" : "保存任务"}
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
                    loading={orderLoading || generatedSummaryLoading}
                    placeholder="订单号"
                    showSearch
                    optionFilterProp="label"
                    value={selectedOrderId}
                    options={visibleOrders.map((order) => ({ value: order.id, label: buildOrderLabel(order) }))}
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
                  <Checkbox
                    checked={hideGeneratedShippingNotes}
                    onChange={(event) => {
                      setHideGeneratedShippingNotes(event.target.checked);
                      setSelectedDetailIds([]);
                      setDevelopmentNoPaths([]);
                    }}
                  >
                    隐藏已生成出货单
                  </Checkbox>
                  <Button
                    icon={<ReloadOutlined />}
                    onClick={() => {
                      void loadGeneratedSummary();
                      void loadDetails();
                    }}
                  >
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
                <Typography.Title level={4}>{editTask ? "本次出货明细" : "待保存出货单"}</Typography.Title>
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
          <Button
            key="edit"
            icon={<EditOutlined />}
            loading={taskActionLoadingId === detailTask?.id}
            onClick={() => detailTask && void openEditModal(detailTask)}
          >
            编辑出货单
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
        open={Boolean(previewTask)}
        title={previewTask ? `出货单预览：${previewTask.taskNo || previewTask.printNo}` : "出货单预览"}
        onCancel={() => setPreviewTask(null)}
        width={1220}
        footer={[
          <Button key="close" onClick={() => setPreviewTask(null)}>
            关闭
          </Button>,
          <Button
            key="edit"
            icon={<EditOutlined />}
            loading={taskActionLoadingId === previewTask?.id}
            onClick={() => previewTask && void openEditModal(previewTask)}
          >
            编辑出货单
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
