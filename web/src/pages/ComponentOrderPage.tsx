import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  FileExcelOutlined,
  ReloadOutlined,
} from "@ant-design/icons";
import { App, Button, Cascader, Select, Space, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { Key, MouseEvent } from "react";
import { fetchOrderDetails, fetchOrders } from "../api/orderApi";
import type { DevelopmentNoOption, OrderRecord, OrderRecordDetail } from "../types/order";
import { formatEmpty } from "../utils/format";

interface ComponentOrderPageProps {
  title: string;
  processType: 1 | 2 | 3 | 4;
}

type ComponentOrderItem = OrderRecordDetail & {
  componentOrderNo?: string;
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

export default function ComponentOrderPage({ title, processType }: ComponentOrderPageProps) {
  const { message } = App.useApp();
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [selectedOrderId, setSelectedOrderId] = useState<number>();
  const [details, setDetails] = useState<OrderRecordDetail[]>([]);
  const [selectedDetailIds, setSelectedDetailIds] = useState<Key[]>([]);
  const [selectedComponentOrderIds, setSelectedComponentOrderIds] = useState<Key[]>([]);
  const [componentOrderItems, setComponentOrderItems] = useState<ComponentOrderItem[]>([]);
  const [developmentNoPaths, setDevelopmentNoPaths] = useState<string[][]>([]);
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
      setSelectedDetailIds([]);
      setSelectedComponentOrderIds([]);
      setComponentOrderItems([]);
      setDevelopmentNoPaths([]);
      return;
    }
    setDetailLoading(true);
    try {
      const data = await fetchOrderDetails(selectedOrderId);
      setDetails(data);
      setSelectedDetailIds([]);
      setSelectedComponentOrderIds([]);
      setComponentOrderItems([]);
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

  const selectedOrder = useMemo(
    () => orders.find((order) => order.id === selectedOrderId),
    [orders, selectedOrderId],
  );
  const selectedOrderNo = selectedOrder ? buildOrderLabel(selectedOrder) : selectedOrderId ? `订单 ${selectedOrderId}` : "-";

  const componentOrderItemKeys = useMemo<Key[]>(
    () => componentOrderItems.map((item) => item.id),
    [componentOrderItems],
  );
  const componentOrderItemKeySet = useMemo(
    () => new Set<Key>(componentOrderItemKeys),
    [componentOrderItemKeys],
  );
  const leftSelectedRowKeys = useMemo(
    () => Array.from(new Set<Key>([...componentOrderItemKeys, ...selectedDetailIds])),
    [componentOrderItemKeys, selectedDetailIds],
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

  const addComponentOrderItems = (items: OrderRecordDetail[]) => {
    const componentOrderNo = selectedOrder ? buildOrderLabel(selectedOrder) : selectedOrderId ? `订单 ${selectedOrderId}` : "-";
    setComponentOrderItems((current) => {
      const currentIdSet = new Set(current.map((item) => item.id));
      const nextItems = items
        .filter((item) => !currentIdSet.has(item.id))
        .map((item) => ({ ...item, componentOrderNo }));
      if (nextItems.length === 0) {
        return current;
      }
      return [...current, ...nextItems];
    });
    setSelectedComponentOrderIds([]);
  };

  const addSelectedComponentOrderItems = () => {
    const selectedSet = new Set(selectedDetailIds);
    addComponentOrderItems(details.filter((item) => selectedSet.has(item.id)));
    setSelectedDetailIds([]);
  };

  const removeComponentOrderItems = () => {
    const selectedSet = new Set(selectedComponentOrderIds);
    setComponentOrderItems((current) => current.filter((item) => !selectedSet.has(item.id)));
    setSelectedComponentOrderIds([]);
  };

  const generateExcel = () => {
    message.info(`模板接口待接入，稍后生成 ${title} Excel`);
  };

  const leftRowSelection = {
    selectedRowKeys: leftSelectedRowKeys,
    getCheckboxProps: (record: OrderRecordDetail) => ({
      disabled: componentOrderItemKeySet.has(record.id),
    }),
    onChange: (nextKeys: Key[]) => {
      setSelectedDetailIds(nextKeys.filter((key) => !componentOrderItemKeySet.has(key)));
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
    return componentOrderItemKeySet.has(record.id) ? "print-transfer-row-disabled" : "print-transfer-row-clickable";
  };

  const getLeftRowProps = (record: OrderRecordDetail) => ({
    onClick: (event: MouseEvent<HTMLElement>) => {
      const target = event.target as HTMLElement;
      if (target.closest(".ant-checkbox-wrapper, .ant-table-selection-column, button, a")) {
        return;
      }
      if (!componentOrderItemKeySet.has(record.id)) {
        setSelectedDetailIds((current) =>
          current.includes(record.id) ? current.filter((key) => key !== record.id) : [...current, record.id],
        );
      }
    },
  });

  const detailColumns = useMemo<ColumnsType<OrderRecordDetail>>(
    () => [
      { title: "开发编号", dataIndex: "developmentNo", width: 150, render: formatEmpty },
      { title: "楦头", dataIndex: "lastNo", width: 110, render: formatEmpty },
      { title: "尺码数量", dataIndex: "sizeQuantities", width: 230, render: renderSizeQuantities },
      { title: "双数", dataIndex: "quantity", width: 80, align: "right", render: formatEmpty },
      { title: "箱数", dataIndex: "cartonCount", width: 80, align: "right", render: formatEmpty },
      { title: "开始箱号", dataIndex: "cartonStart", width: 110, render: formatEmpty },
      { title: "结束箱号", dataIndex: "cartonEnd", width: 110, render: formatEmpty },
    ],
    [],
  );

  const leftColumns = useMemo<ColumnsType<OrderRecordDetail>>(
    () => [
      { title: "订单流水号", key: "orderNo", width: 140, render: () => formatEmpty(selectedOrderNo) },
      ...detailColumns,
    ],
    [detailColumns, selectedOrderNo],
  );

  const componentOrderColumns = useMemo<ColumnsType<ComponentOrderItem>>(
    () => [
      { title: "订单流水号", dataIndex: "componentOrderNo", width: 140, render: formatEmpty },
      ...detailColumns,
    ],
    [detailColumns],
  );

  return (
    <div className="workspace component-order-page">
      <div className="toolbar-band">
        <div>
          <Typography.Title level={3}>{title}</Typography.Title>
          <Typography.Text type="secondary">
            当前订单：{selectedOrderNo} / 工序编号：{processType}
          </Typography.Text>
        </div>
        <Space wrap>
          <Button
            type="primary"
            icon={<FileExcelOutlined />}
            disabled={componentOrderItems.length === 0}
            onClick={generateExcel}
          >
            生成Excel
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
            <Typography.Text type="secondary" className="component-order-count">
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
            scroll={{ x: 1010 }}
            className="data-table"
            rowClassName={leftRowClassName}
            rowSelection={leftRowSelection}
            onRow={getLeftRowProps}
            locale={{ emptyText: "暂无订单明细，请先在订单列表点击“识别订单”" }}
          />
        </div>

        <div className="print-transfer-actions">
          <Button
            type="primary"
            icon={<ArrowRightOutlined />}
            disabled={!selectedDetailIds.length}
            onClick={addSelectedComponentOrderItems}
          />
          <Button
            icon={<ArrowLeftOutlined />}
            disabled={!selectedComponentOrderIds.length}
            onClick={removeComponentOrderItems}
          />
        </div>

        <div className="page-panel print-transfer-panel">
          <div className="print-transfer-heading">
            <div className="print-transfer-heading-main">
              <Typography.Title level={4}>待下单明细</Typography.Title>
            </div>
            <Typography.Text type="secondary">{componentOrderItems.length} 条待下单</Typography.Text>
          </div>
          <Table
            rowKey="id"
            columns={componentOrderColumns}
            dataSource={componentOrderItems}
            pagination={{ pageSize: 10, showSizeChanger: false }}
            scroll={{ x: 1010 }}
            className="data-table"
            rowSelection={{
              selectedRowKeys: selectedComponentOrderIds,
              onChange: setSelectedComponentOrderIds,
            }}
            locale={{ emptyText: "请从左侧选择明细加入待下单列表" }}
          />
        </div>
      </div>
    </div>
  );
}
