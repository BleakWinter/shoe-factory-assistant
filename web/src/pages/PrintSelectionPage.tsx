import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  PrinterOutlined,
  ReloadOutlined,
} from "@ant-design/icons";
import { App, Button, Cascader, Modal, Radio, Select, Space, Table, Typography } from "antd";
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

type PrintFormatKey = "outer-carton-a4" | "outer-carton-dual-size";

interface CartonLabelTemplateData {
  customerName: string;
  storeLine: string;
  cartonNumber: string;
  factoryOrderNo: string;
  style: string;
  material: string;
  color: string;
  number: string;
  po: string;
  orderNumber: string;
  sizeColumns: string[];
  sizeValues: string[];
  grossWeight: string;
  netWeight: string;
}

const defaultCartonLabelData: CartonLabelTemplateData = {
  customerName: "客人",
  storeLine: "仓库号/店铺号",
  cartonNumber: "开始箱号",
  factoryOrderNo: "订单流水号",
  style: "STYLE/客人款号",
  material: "MATERIAL/面料材质",
  color: "COLOR/客人颜色",
  number: "总对数",
  po: "PO号",
  orderNumber: "客人订单号",
  sizeColumns: ["WIDTH", "5", "5.5", "6", "6.5", "7", "7.5", "8", "8.5", "9", "9.5", "10", "10.5", "11", "12", "TOTAL"],
  sizeValues: ["M", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""],
  grossWeight: "毛重",
  netWeight: "净重",
};

const printFormatTemplates: Record<PrintFormatKey, { label: string; data: CartonLabelTemplateData }> = {
  "outer-carton-a4": {
    label: "外箱贴标-单码-通用版",
    data: defaultCartonLabelData,
  },
  "outer-carton-dual-size": {
    label: "外箱贴标-双码-通用版",
    data: {
      ...defaultCartonLabelData,
      sizeColumns: ["WIDTH", "", "", "", "35", "36", "37", "38", "39", "40", "41", "", "", "", "", "TOTAL"],
      sizeValues: ["M", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""],
    },
  },
};

const printFormatOptions = Object.entries(printFormatTemplates).map(([value, item]) => ({
  label: item.label,
  value: value as PrintFormatKey,
}));

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
  const [formatModalOpen, setFormatModalOpen] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [selectedPrintFormat, setSelectedPrintFormat] = useState<PrintFormatKey>("outer-carton-a4");
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

  const openPrintFormatModal = () => {
    setFormatModalOpen(true);
  };

  const printSelectedFormat = () => {
    setFormatModalOpen(false);
    window.setTimeout(() => window.print(), 0);
  };

  const previewSelectedFormat = (format: PrintFormatKey = selectedPrintFormat) => {
    setSelectedPrintFormat(format);
    setFormatModalOpen(false);
    setPreviewOpen(true);
  };

  const backToPrintFormatModal = () => {
    setPreviewOpen(false);
    setFormatModalOpen(true);
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
      { title: "开始箱号", dataIndex: "cartonStart", width: 110, render: formatEmpty },
      { title: "结束箱号", dataIndex: "cartonEnd", width: 110, render: formatEmpty },
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
  const selectedPrintFormatLabel = printFormatTemplates[selectedPrintFormat].label;

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
          <Button type="primary" icon={<PrinterOutlined />} onClick={openPrintFormatModal}>
            打印
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
            scroll={{ x: 870 }}
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
            scroll={{ x: 870 }}
            className="data-table"
            rowSelection={{
              selectedRowKeys: selectedPrintIds,
              onChange: setSelectedPrintIds,
            }}
          />
        </div>
      </div>

      <Modal
        open={formatModalOpen}
        title="选择打印格式"
        onCancel={() => setFormatModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setFormatModalOpen(false)}>
            取消
          </Button>,
          <Button key="print" type="primary" icon={<PrinterOutlined />} onClick={printSelectedFormat}>
            打印
          </Button>,
        ]}
        destroyOnClose
      >
        <Radio.Group
          className="print-format-options"
          value={selectedPrintFormat}
          onChange={(event) => setSelectedPrintFormat(event.target.value)}
        >
          {printFormatOptions.map((option) => (
            <div
              className={`print-format-option${selectedPrintFormat === option.value ? " print-format-option-selected" : ""}`}
              key={option.value}
              onClick={() => setSelectedPrintFormat(option.value)}
            >
              <Radio value={option.value}>{option.label}</Radio>
              <Button
                size="small"
                onClick={(event) => {
                  event.stopPropagation();
                  previewSelectedFormat(option.value);
                }}
              >
                预览
              </Button>
            </div>
          ))}
        </Radio.Group>
      </Modal>

      <Modal
        open={previewOpen}
        title={`${selectedPrintFormatLabel}预览`}
        onCancel={backToPrintFormatModal}
        width={1120}
        footer={null}
        destroyOnClose
      >
        <div className="carton-label-preview">
          <CartonLabelA4 format={selectedPrintFormat} />
        </div>
      </Modal>

      <div className="carton-label-print-root" aria-hidden>
        <div className="carton-label-preview">
          <CartonLabelA4 format={selectedPrintFormat} />
        </div>
      </div>
    </div>
  );
}

function CartonLabelA4({ format }: { format: PrintFormatKey }) {
  const data = printFormatTemplates[format].data;

  return (
    <div className="carton-label-a4">
      <CartonLabelTemplate data={data} />
      <CartonLabelTemplate data={data} />
    </div>
  );
}

function CartonLabelTemplate({ data }: { data: CartonLabelTemplateData }) {
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
          <div className="carton-label-customer">{data.customerName}</div>
          <div className="carton-label-empty-line">{data.storeLine}</div>
          <div className="carton-label-caption">CARTON NUMBER</div>
          <div className="carton-label-carton-number">{data.cartonNumber}</div>
        </div>
      </div>

      <CartonInfoRow label="Factory order NO:" value={data.factoryOrderNo} />
      <CartonInfoRow label="STYLE:" value={data.style} />
      <CartonInfoRow label="MATERIAL:" value={data.material} />
      <CartonInfoRow label="COLOR:" value={data.color} />
      <CartonInfoRow label="Number:" value={data.number} />
      <CartonInfoRow label="PO#:" value={data.po} />
      <CartonInfoRow label="ORDER NUMBER:" value={data.orderNumber} />

      <div className="carton-label-size-grid">
        {data.sizeColumns.map((value, index) => (
          <div key={`size-${index}`} className="carton-label-size-cell">
            {value}
          </div>
        ))}
        {data.sizeValues.map((value, index) => (
          <div key={`value-${index}`} className="carton-label-size-cell">
            {value}
          </div>
        ))}
        <div className="carton-label-weight-cell carton-label-weight-gross">G.W: {data.grossWeight}</div>
        <div className="carton-label-weight-cell carton-label-weight-net">N.W:{data.netWeight}</div>
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
