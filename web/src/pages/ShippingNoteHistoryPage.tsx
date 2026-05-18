import { EyeOutlined, PrinterOutlined, ReloadOutlined, SearchOutlined } from "@ant-design/icons";
import { App, Button, Form, Input, Modal, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import { fetchShippingNotes } from "../api/shippingNoteApi";
import ShippingNoteSheet from "../components/ShippingNoteSheet";
import type { ShippingNoteQueryParams, ShippingNoteRecord } from "../types/order";
import { formatDateTime, formatEmpty } from "../utils/format";

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

export default function ShippingNoteHistoryPage() {
  const { message } = App.useApp();
  const [form] = Form.useForm<ShippingNoteQueryParams>();
  const [records, setRecords] = useState<ShippingNoteRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [activeRecord, setActiveRecord] = useState<ShippingNoteRecord | null>(null);

  const loadRecords = useCallback(
    async (nextPage = page, nextSize = size) => {
      setLoading(true);
      try {
        const values = form.getFieldsValue();
        const result = await fetchShippingNotes({
          ...values,
          page: nextPage,
          size: nextSize,
        });
        setRecords(result.records);
        setPage(result.page || nextPage);
        setSize(result.size || nextSize);
        setTotal(result.total);
      } catch (error) {
        setRecords([]);
        message.error(error instanceof Error ? error.message : "出货单记录加载失败");
      } finally {
        setLoading(false);
      }
    },
    [form, message, page, size],
  );

  useEffect(() => {
    void loadRecords(1, size);
  }, []);

  const printActiveRecord = () => {
    if (!activeRecord) {
      return;
    }
    applyShippingNotePrintSize();
    window.setTimeout(() => window.print(), 0);
  };

  const columns = useMemo<ColumnsType<ShippingNoteRecord>>(
    () => [
      { title: "打印编号", dataIndex: "printNo", width: 150, render: formatEmpty },
      { title: "订单号", dataIndex: "orderNo", width: 150, render: formatEmpty },
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
        width: 150,
        fixed: "right",
        render: (_, record) => (
          <Button icon={<EyeOutlined />} onClick={() => setActiveRecord(record)}>
            查看
          </Button>
        ),
      },
    ],
    [],
  );

  return (
    <div className="workspace">
      <div className="toolbar-band">
        <div>
          <Typography.Title level={3}>出货单记录</Typography.Title>
          <Typography.Text type="secondary">
            查询哪些订单已经保存过出货单打印数据，并可按原快照重新查看或打印。
          </Typography.Text>
        </div>
        <Space wrap>
          <Button icon={<ReloadOutlined />} onClick={() => void loadRecords(1, size)}>
            刷新
          </Button>
        </Space>
      </div>

      <div className="page-panel">
        <Form
          className="filter-form"
          form={form}
          layout="vertical"
          onFinish={() => void loadRecords(1, size)}
        >
          <Form.Item label="订单号" name="orderNo">
            <Input allowClear placeholder="输入订单号" />
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
          loading={loading}
          columns={columns}
          dataSource={records}
          pagination={{
            current: page,
            pageSize: size,
            total,
            showSizeChanger: true,
            onChange: (nextPage, nextSize) => void loadRecords(nextPage, nextSize),
          }}
          scroll={{ x: 1420 }}
          className="data-table"
        />
      </div>

      <Modal
        open={Boolean(activeRecord)}
        title={activeRecord ? `出货单记录：${activeRecord.printNo}` : "出货单记录"}
        onCancel={() => setActiveRecord(null)}
        width={1220}
        footer={[
          <Button key="close" onClick={() => setActiveRecord(null)}>
            关闭
          </Button>,
          <Button key="print" type="primary" icon={<PrinterOutlined />} onClick={printActiveRecord}>
            重新打印
          </Button>,
        ]}
        destroyOnClose
      >
        {activeRecord ? (
          <div className="shipping-note-preview">
            <ShippingNoteSheet
              recipientName={activeRecord.recipientName}
              shippingDate={activeRecord.shippingDate}
              items={activeRecord.items || []}
              totalPairs={activeRecord.totalPairs}
              totalCartonCount={activeRecord.totalCartonCount}
            />
          </div>
        ) : null}
      </Modal>

      <div className="shipping-note-print-root" aria-hidden>
        {activeRecord ? (
          <ShippingNoteSheet
            recipientName={activeRecord.recipientName}
            shippingDate={activeRecord.shippingDate}
            items={activeRecord.items || []}
            totalPairs={activeRecord.totalPairs}
            totalCartonCount={activeRecord.totalCartonCount}
          />
        ) : null}
      </div>
    </div>
  );
}
