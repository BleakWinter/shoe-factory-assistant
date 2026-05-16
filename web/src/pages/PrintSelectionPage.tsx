import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  PrinterOutlined,
  ReloadOutlined,
} from "@ant-design/icons";
import { App, Button, Cascader, Modal, Select, Space, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { Key, MouseEvent } from "react";
import { fetchOrderDetails, fetchOrders } from "../api/orderApi";
import type { DevelopmentNoOption, OrderRecord, OrderRecordDetail } from "../types/order";
import { formatEmpty } from "../utils/format";

interface PrintSelectionPageProps {
  title: string;
}

type PrintSelectionItem = OrderRecordDetail & {
  printOrderNo?: string;
};

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

function buildOrderLabel(order: OrderRecord) {
  return order.orderNo || `订单 ${order.id}`;
}

function includesKeyword(value: unknown, keyword: string) {
  return String(value || "")
    .toLocaleLowerCase()
    .includes(keyword.toLocaleLowerCase());
}

function renderCountText(filteredCount: number, totalCount: number, suffix: string, isFiltered: boolean) {
  if (!isFiltered) {
    return `${totalCount} 条${suffix}`;
  }
  return `${filteredCount} / ${totalCount} 条${suffix}`;
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

export default function PrintSelectionPage({ title }: PrintSelectionPageProps) {
  const { message } = App.useApp();
  const printTargetTitle = title.replace(/^打印/, "");
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [selectedOrderId, setSelectedOrderId] = useState<number>();
  const [details, setDetails] = useState<OrderRecordDetail[]>([]);
  const [selectedDetailIds, setSelectedDetailIds] = useState<Key[]>([]);
  const [selectedPrintIds, setSelectedPrintIds] = useState<Key[]>([]);
  const [printItems, setPrintItems] = useState<PrintSelectionItem[]>([]);
  const [developmentNoPaths, setDevelopmentNoPaths] = useState<string[][]>([]);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [orderLoading, setOrderLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);

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

  const printItemKeys = useMemo<Key[]>(() => printItems.map((item) => item.id), [printItems]);

  const printItemKeySet = useMemo(() => new Set<Key>(printItemKeys), [printItemKeys]);

  const selectedOrder = useMemo(
    () => orders.find((order) => order.id === selectedOrderId),
    [orders, selectedOrderId],
  );
  const selectedOrderNo = selectedOrder ? buildOrderLabel(selectedOrder) : selectedOrderId ? `订单 ${selectedOrderId}` : "-";

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
    const printOrderNo = selectedOrder ? buildOrderLabel(selectedOrder) : selectedOrderId ? `订单 ${selectedOrderId}` : "-";
    setPrintItems((current) => {
      const currentIdSet = new Set(current.map((item) => item.id));
      const nextItems = items
        .filter((item) => !currentIdSet.has(item.id))
        .map((item) => ({ ...item, printOrderNo }));
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
    setPrintItems((current) => current.filter((item) => !selectedSet.has(item.id)));
    setSelectedPrintIds([]);
  };

  const printCartonLabel = () => {
    window.print();
  };

  const leftRowSelection = {
    selectedRowKeys: leftSelectedRowKeys,
    getCheckboxProps: (record: OrderRecordDetail) => ({
      disabled: printItemKeySet.has(record.id),
    }),
    onChange: (nextKeys: Key[]) => {
      setSelectedDetailIds(nextKeys.filter((key) => !printItemKeySet.has(key)));
    },
    onSelectAll: (selected: boolean, _selectedRows: OrderRecordDetail[], changedRows: OrderRecordDetail[]) => {
      const changedIds = new Set(changedRows.map((item) => item.id));
      setSelectedDetailIds((current) => {
        if (selected) {
          return Array.from(new Set<Key>([...current, ...changedRows.map((item) => item.id)]));
        }
        return current.filter((key) => !changedIds.has(key as number));
      });
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

  const detailColumns = useMemo<ColumnsType<OrderRecordDetail>>(
    () => [
      { title: "开发编号", dataIndex: "developmentNo", width: 140, render: formatEmpty },
      { title: "尺码数量", dataIndex: "sizeQuantities", width: 220, render: renderSizeQuantities },
      { title: "双数", dataIndex: "quantity", width: 80, align: "right", render: formatEmpty },
      { title: "箱数", dataIndex: "cartonCount", width: 80, align: "right", render: formatEmpty },
    ],
    [],
  );

  const leftColumns = useMemo<ColumnsType<OrderRecordDetail>>(
    () => [
      { title: "订单流水号", key: "orderNo", width: 130, render: () => formatEmpty(selectedOrderNo) },
      ...detailColumns,
    ],
    [detailColumns, selectedOrderNo],
  );

  const printColumns = useMemo<ColumnsType<PrintSelectionItem>>(
    () => [
      { title: "订单流水号", dataIndex: "printOrderNo", width: 130, render: formatEmpty },
      ...detailColumns,
    ],
    [detailColumns],
  );

  return (
    <div className="workspace">
      <div className="toolbar-band">
        <div>
          <Typography.Title level={3}>{title}</Typography.Title>
          <Typography.Text type="secondary">
            从左侧订单明细中选择要打印的数据，移动到右侧待打印列表。
          </Typography.Text>
        </div>
        <Space wrap>
          <Button type="primary" icon={<PrinterOutlined />} onClick={() => setPreviewOpen(true)}>
            打印预览
          </Button>
        </Space>
      </div>

      <div className="print-transfer-layout">
        <div className="page-panel print-transfer-panel">
          <div className="print-transfer-heading">
            <div className="print-transfer-heading-main">
              <Typography.Title level={4}>订单明细表数据</Typography.Title>
              <Space className="print-transfer-filters" wrap>
                <Select
                  className="print-order-select"
                  placeholder="订单号"
                  loading={orderLoading}
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
            <Typography.Text type="secondary">
              {renderCountText(
                filteredDetails.length,
                details.length,
                "明细",
                selectedDevelopmentNos.size > 0,
              )}
            </Typography.Text>
          </div>
          <Table
            rowKey="id"
            loading={detailLoading}
            columns={leftColumns}
            dataSource={filteredDetails}
            pagination={{ pageSize: 10, showSizeChanger: false }}
            scroll={{ x: 650 }}
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
              <Typography.Title level={4}>待打印{printTargetTitle}</Typography.Title>
            </div>
            <Typography.Text type="secondary">{printItems.length} 条待打印</Typography.Text>
          </div>
          <Table
            rowKey="id"
            columns={printColumns}
            dataSource={printItems}
            pagination={{ pageSize: 10, showSizeChanger: false }}
            scroll={{ x: 650 }}
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
        title={`${printTargetTitle}预览`}
        onCancel={() => setPreviewOpen(false)}
        width={1120}
        footer={[
          <Button key="close" onClick={() => setPreviewOpen(false)}>
            关闭
          </Button>,
          <Button key="print" type="primary" icon={<PrinterOutlined />} onClick={printCartonLabel}>
            打印
          </Button>,
        ]}
        destroyOnClose
      >
        <div className="carton-label-preview">
          <div className="carton-label-a4">
            <CartonLabelTemplate />
            <CartonLabelTemplate />
          </div>
        </div>
      </Modal>
    </div>
  );
}

function CartonLabelTemplate() {
  const sizeColumns = ["WIDTH", "5", "5.5", "6", "6.5", "7", "7.5", "8", "8.5", "9", "9.5", "10", "10.5", "11", "12", "TOTAL"];
  const sizeValues = ["M", "", "", "", "3", "6", "3", "", "", "", "", "", "", "", "", "12"];

  return (
    <section className="carton-label-sheet">
      <div className="carton-label-brand">JEFFREY CAMPBELL</div>
      <div className="carton-label-top">
        <div className="carton-label-side-brand">
          <span>
            JEFFREY
            <br />
            CAMPBELL
          </span>
        </div>
        <div className="carton-label-carton-box">
          <div className="carton-label-customer">Blue Rose Shoe Group</div>
          <div className="carton-label-empty-line" />
          <div className="carton-label-caption">CARTON NUMBER</div>
          <div className="carton-label-carton-number">22531</div>
        </div>
      </div>

      <CartonInfoRow label="Factory order NO:" value="26050" />
      <CartonInfoRow label="STYLE:" value="TRUSTEE" />
      <CartonInfoRow label="MATERIAL:" value="KID SKIN" />
      <CartonInfoRow label="COLOR:" value="BLACK" />
      <CartonInfoRow label="Number:" value="12" />
      <CartonInfoRow label="PO#:" value="" />
      <CartonInfoRow label="ORDER NUMBER:" value="CIU07403" />

      <div className="carton-label-size-grid">
        {sizeColumns.map((value, index) => (
          <div key={`size-${index}`} className="carton-label-size-cell">
            {value}
          </div>
        ))}
        {sizeValues.map((value, index) => (
          <div key={`value-${index}`} className="carton-label-size-cell">
            {value}
          </div>
        ))}
      </div>

      <div className="carton-label-weight-row">
        <div>G.W: 10.32</div>
        <div>N.W:6.12</div>
      </div>
      <div className="carton-label-origin">MADE IN CHINA</div>
    </section>
  );
}

function CartonInfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="carton-label-info-row">
      <div className="carton-label-info-label">{label}</div>
      <div className="carton-label-info-value">{value}</div>
    </div>
  );
}
