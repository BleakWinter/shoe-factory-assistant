import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  PrinterOutlined,
  ReloadOutlined,
} from "@ant-design/icons";
import { App, Button, Cascader, Input, Modal, Select, Space, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { Key, MouseEvent } from "react";
import { fetchOrderDetails, fetchOrders } from "../api/orderApi";
import { createShippingNote } from "../api/shippingNoteApi";
import ShippingNoteSheet, {
  sumShippingNoteCartons,
  sumShippingNotePairs,
} from "../components/ShippingNoteSheet";
import type {
  DevelopmentNoOption,
  OrderRecord,
  OrderRecordDetail,
  ShippingNoteItem,
  ShippingNoteRecord,
} from "../types/order";
import { formatEmpty } from "../utils/format";

const defaultRecipient = "达为鞋业";

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

function sumSizeQuantities(value?: Record<string, number>) {
  return Object.values(value || {}).reduce((total, count) => total + (Number(count) > 0 ? Number(count) : 0), 0);
}

function buildShippingItem(detail: OrderRecordDetail, order?: OrderRecord): ShippingNoteItem {
  const pairCount = detail.quantity || sumSizeQuantities(detail.sizeQuantities);
  return {
    sourceDetailId: detail.id,
    orderNo: order ? buildOrderLabel(order) : undefined,
    developmentNo: detail.developmentNo,
    customerName: pickFirstText(detail.customerName, order?.customerName),
    customerStyleNo: detail.customerStyleNo,
    englishColor: detail.englishColor,
    englishMaterial: detail.englishMaterial,
    colorMaterial: pickFirstText(detail.upperMaterial, detail.englishColor),
    trademark: detail.trademark,
    sizeQuantities: detail.sizeQuantities || {},
    pairCount,
    cartonCount: detail.cartonCount || 0,
    totalPairs: pairCount,
    cartonStart: detail.cartonStart,
    cartonEnd: detail.cartonEnd,
  };
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
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [selectedOrderId, setSelectedOrderId] = useState<number>();
  const [details, setDetails] = useState<OrderRecordDetail[]>([]);
  const [selectedDetailIds, setSelectedDetailIds] = useState<Key[]>([]);
  const [selectedPrintIds, setSelectedPrintIds] = useState<Key[]>([]);
  const [printItems, setPrintItems] = useState<ShippingNoteItem[]>([]);
  const [developmentNoPaths, setDevelopmentNoPaths] = useState<string[][]>([]);
  const [recipientName, setRecipientName] = useState(defaultRecipient);
  const [shippingDate, setShippingDate] = useState(todayText());
  const [previewOpen, setPreviewOpen] = useState(false);
  const [orderLoading, setOrderLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [savedPrintRecord, setSavedPrintRecord] = useState<ShippingNoteRecord | null>(null);

  const selectedOrder = useMemo(
    () => orders.find((order) => order.id === selectedOrderId),
    [orders, selectedOrderId],
  );

  const loadOrders = useCallback(async () => {
    setOrderLoading(true);
    try {
      const page = await fetchOrders({ page: 1, size: 100 });
      setOrders(page.records);
      setSelectedOrderId((current) => current || page.records[0]?.id);
    } catch (error) {
      setOrders([]);
      message.error(error instanceof Error ? error.message : "订单加载失败");
    } finally {
      setOrderLoading(false);
    }
  }, [message]);

  const loadDetails = useCallback(async () => {
    if (!selectedOrderId) {
      setDetails([]);
      return;
    }
    setDetailLoading(true);
    try {
      const data = await fetchOrderDetails(selectedOrderId);
      setDetails(data);
      setSelectedDetailIds([]);
      setDevelopmentNoPaths([]);
    } catch (error) {
      setDetails([]);
      message.error(error instanceof Error ? error.message : "订单明细加载失败");
    } finally {
      setDetailLoading(false);
    }
  }, [message, selectedOrderId]);

  useEffect(() => {
    void loadOrders();
  }, [loadOrders]);

  useEffect(() => {
    void loadDetails();
  }, [loadDetails]);

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

  const addPrintItems = (items: OrderRecordDetail[]) => {
    setPrintItems((current) => {
      const currentIdSet = new Set(current.map((item) => item.sourceDetailId));
      const nextItems = items
        .filter((item) => !currentIdSet.has(item.id))
        .map((item) => buildShippingItem(item, selectedOrder));
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

  const saveAndPrint = async () => {
    if (!selectedOrderId || printItems.length === 0) {
      message.warning("请先选择要打印的出货单明细");
      return;
    }
    setSaving(true);
    try {
      const record = await createShippingNote({
        orderId: selectedOrderId,
        recipientName,
        shippingDate,
        items: printItems,
      });
      setSavedPrintRecord(record);
      message.success("出货单数据已保存，正在打开打印窗口");
      setPreviewOpen(false);
      applyShippingNotePrintSize();
      window.setTimeout(() => window.print(), 0);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "出货单保存失败");
    } finally {
      setSaving(false);
    }
  };

  const detailColumns = useMemo<ColumnsType<OrderRecordDetail>>(
    () => [
      { title: "订单号", width: 130, render: () => formatEmpty(selectedOrder ? buildOrderLabel(selectedOrder) : "") },
      { title: "开发编号", dataIndex: "developmentNo", width: 140, render: formatEmpty },
      { title: "客人型体", dataIndex: "customerStyleNo", width: 120, render: formatEmpty },
      { title: "英文颜色", dataIndex: "englishColor", width: 150, render: formatEmpty },
      { title: "英文材质", dataIndex: "englishMaterial", width: 140, render: formatEmpty },
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
      { title: "订单号", dataIndex: "orderNo", width: 130, render: formatEmpty },
      { title: "开发编号", dataIndex: "developmentNo", width: 140, render: formatEmpty },
      { title: "客人型体", dataIndex: "customerStyleNo", width: 120, render: formatEmpty },
      { title: "英文颜色", dataIndex: "englishColor", width: 150, render: formatEmpty },
      { title: "英文材质", dataIndex: "englishMaterial", width: 140, render: formatEmpty },
      { title: "尺码数量", dataIndex: "sizeQuantities", width: 220, render: renderSizeQuantities },
      { title: "双数", dataIndex: "pairCount", width: 80, align: "right", render: formatEmpty },
      { title: "件数", dataIndex: "cartonCount", width: 80, align: "right", render: formatEmpty },
      { title: "开始箱号", dataIndex: "cartonStart", width: 110, render: formatEmpty },
      { title: "结束箱号", dataIndex: "cartonEnd", width: 110, render: formatEmpty },
    ],
    [],
  );

  const leftRowSelection = {
    selectedRowKeys: leftSelectedRowKeys,
    getCheckboxProps: (record: OrderRecordDetail) => ({
      disabled: printItemKeySet.has(record.id),
    }),
    onChange: (nextKeys: Key[]) => {
      setSelectedDetailIds(nextKeys.filter((key) => !printItemKeySet.has(key)));
    },
  };

  const leftRowClassName = (record: OrderRecordDetail) => {
    return printItemKeySet.has(record.id) ? "print-transfer-row-disabled" : "print-transfer-row-clickable";
  };

  const getLeftRowProps = (record: OrderRecordDetail) => ({
    onClick: (event: MouseEvent<HTMLElement>) => {
      const target = event.target as HTMLElement;
      if (target.closest(".ant-checkbox-wrapper, .ant-table-selection-column, button, a")) {
        return;
      }
      if (!printItemKeySet.has(record.id)) {
        setSelectedDetailIds((current) =>
          current.includes(record.id) ? current.filter((key) => key !== record.id) : [...current, record.id],
        );
      }
    },
  });

  const printRootItems = savedPrintRecord?.items?.length ? savedPrintRecord.items : printItems;
  const printRootRecipient = savedPrintRecord?.recipientName || recipientName;
  const printRootDate = savedPrintRecord?.shippingDate || shippingDate;

  return (
    <div className="workspace">
      <div className="toolbar-band">
        <div>
          <Typography.Title level={3}>打印出货单</Typography.Title>
          <Typography.Text type="secondary">
            从订单明细中选择本次出货内容，前端直接绘制出货单；点击打印时保存一份数据快照。
          </Typography.Text>
        </div>
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
          <Button
            type="primary"
            icon={<PrinterOutlined />}
            disabled={!printItems.length}
            onClick={() => setPreviewOpen(true)}
          >
            预览出货单
          </Button>
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
            scroll={{ x: 1370 }}
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
              <Typography.Title level={4}>待打印出货单</Typography.Title>
            </div>
            <Typography.Text type="secondary">
              {printItems.length} 行，{sumShippingNoteCartons(printItems)} 件，{sumShippingNotePairs(printItems)} 双
            </Typography.Text>
          </div>
          <Table
            rowKey="sourceDetailId"
            columns={printColumns}
            dataSource={printItems}
            pagination={{ pageSize: 10, showSizeChanger: false }}
            scroll={{ x: 1290 }}
            className="data-table"
            rowSelection={{
              selectedRowKeys: selectedPrintIds,
              onChange: setSelectedPrintIds,
            }}
          />
        </div>
      </div>

      <Modal
        open={previewOpen}
        title="出货单预览"
        onCancel={() => setPreviewOpen(false)}
        width={1220}
        footer={[
          <Button key="close" onClick={() => setPreviewOpen(false)}>
            取消
          </Button>,
          <Button key="print" type="primary" icon={<PrinterOutlined />} loading={saving} onClick={() => void saveAndPrint()}>
            保存并打印
          </Button>,
        ]}
        destroyOnClose
      >
        <div className="shipping-note-preview">
          <ShippingNoteSheet
            recipientName={recipientName}
            shippingDate={shippingDate}
            items={printItems}
          />
        </div>
      </Modal>

      <div className="shipping-note-print-root" aria-hidden>
        <ShippingNoteSheet
          recipientName={printRootRecipient}
          shippingDate={printRootDate}
          items={printRootItems}
        />
      </div>
    </div>
  );
}
