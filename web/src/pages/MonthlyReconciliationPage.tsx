import { ReloadOutlined } from "@ant-design/icons";
import { Alert, App, Button, Input, Select, Statistic, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  fetchMonthlyReconciliation,
  fetchReconciliationCustomers,
} from "../api/reconciliationApi";
import type {
  MonthlyReconciliation,
  MonthlyReconciliationDetail,
} from "../types/reconciliation";

function currentMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function formatAmount(value?: number) {
  return new Intl.NumberFormat("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number(value) || 0);
}

function formatCount(value?: number) {
  return new Intl.NumberFormat("zh-CN").format(Number(value) || 0);
}

function formatText(value?: string) {
  return value?.trim() || "-";
}

export default function MonthlyReconciliationPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [month, setMonth] = useState(currentMonth());
  const [customerName, setCustomerName] = useState("");
  const [customers, setCustomers] = useState<string[]>([]);
  const [reconciliation, setReconciliation] = useState<MonthlyReconciliation>();
  const [loading, setLoading] = useState(false);

  const loadCustomers = useCallback(async () => {
    try {
      const values = await fetchReconciliationCustomers();
      const nextCustomers = values.length > 0 ? values : ["达为鞋业"];
      setCustomers(nextCustomers);
      setCustomerName((current) =>
        current && nextCustomers.includes(current) ? current : nextCustomers[0],
      );
    } catch (error) {
      message.error(error instanceof Error ? error.message : "客户加载失败");
    }
  }, [message]);

  const loadReconciliation = useCallback(async () => {
    if (!month || !customerName) {
      return;
    }
    setLoading(true);
    try {
      setReconciliation(await fetchMonthlyReconciliation(month, customerName));
    } catch (error) {
      message.error(error instanceof Error ? error.message : "月度对账加载失败");
    } finally {
      setLoading(false);
    }
  }, [customerName, message, month]);

  useEffect(() => {
    void loadCustomers();
  }, [loadCustomers]);

  useEffect(() => {
    void loadReconciliation();
  }, [loadReconciliation]);

  const detailColumns = useMemo<ColumnsType<MonthlyReconciliationDetail>>(
    () => [
      {
        title: "出货日期",
        dataIndex: "shippingDate",
        key: "shippingDate",
        width: 110,
        fixed: "left",
        render: formatText,
      },
      {
        title: "订单号",
        dataIndex: "orderNo",
        key: "orderNo",
        width: 140,
        fixed: "left",
        render: formatText,
      },
      {
        title: "工厂款号",
        dataIndex: "developmentNo",
        key: "developmentNo",
        width: 150,
        render: formatText,
      },
      {
        title: "颜色/材料",
        dataIndex: "colorMaterial",
        key: "colorMaterial",
        width: 210,
        render: formatText,
      },
      {
        title: "总订单数量（双）",
        dataIndex: "orderQuantity",
        key: "orderQuantity",
        width: 140,
        align: "right",
        render: formatCount,
      },
      {
        title: "出货数量（双）",
        dataIndex: "shippingQuantity",
        key: "shippingQuantity",
        width: 130,
        align: "right",
        render: formatCount,
      },
      {
        title: "成品/半成品",
        dataIndex: "productType",
        key: "productType",
        width: 120,
        align: "center",
        render: formatText,
      },
      {
        title: "单价（元）",
        dataIndex: "unitPrice",
        key: "unitPrice",
        width: 115,
        align: "right",
        render: (value?: number, record?: MonthlyReconciliationDetail) =>
          record?.missingPrice ? (
            <Typography.Text type="danger">未配置</Typography.Text>
          ) : (
            formatAmount(value)
          ),
      },
      {
        title: "总金额（元）",
        dataIndex: "amount",
        key: "amount",
        width: 130,
        align: "right",
        render: (value?: number, record?: MonthlyReconciliationDetail) =>
          record?.missingPrice ? "-" : formatAmount(value),
      },
      {
        title: "备注",
        dataIndex: "remark",
        key: "remark",
        width: 180,
        render: formatText,
      },
    ],
    [],
  );

  return (
    <div className="workspace reconciliation-page">
      <div className="toolbar-band reconciliation-toolbar">
        <div>
          <Typography.Title level={3}>月度对账</Typography.Title>
          <Typography.Text type="secondary">
            按出货日期查询每月出货明细和货款，方便手工填写 Excel 对账单。
          </Typography.Text>
        </div>
        <div className="reconciliation-filters">
          <label>
            <span>月份</span>
            <Input
              type="month"
              value={month}
              onChange={(event) => setMonth(event.target.value)}
            />
          </label>
          <label>
            <span>客户</span>
            <Select
              showSearch
              value={customerName || undefined}
              options={customers.map((value) => ({ label: value, value }))}
              onChange={setCustomerName}
            />
          </label>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadReconciliation()}>
            刷新
          </Button>
        </div>
      </div>

      <Alert
        type="info"
        showIcon
        message="本页只读取现有出货单和价格配置，不会创建对账记录，也不会修改数据库中的业务数据。"
      />

      <div className="reconciliation-summary-grid">
        <div className="reconciliation-metric">
          <Statistic title="订单数量合计" value={reconciliation?.totalOrderQuantity || 0} suffix="双" />
        </div>
        <div className="reconciliation-metric">
          <Statistic title="本月出货合计" value={reconciliation?.totalShippingQuantity || 0} suffix="双" />
        </div>
        <div className="reconciliation-metric reconciliation-metric-total">
          <Statistic title="货款合计" value={reconciliation?.goodsAmount || 0} precision={2} prefix="¥" />
        </div>
      </div>

      {(reconciliation?.missingPriceCount || 0) > 0 ? (
        <Alert
          type="warning"
          showIcon
          message={`${reconciliation?.missingPriceCount || 0} 条出货明细尚未配置单价，暂未计入货款合计`}
          action={
            <Button size="small" onClick={() => navigate("/price-configs")}>
              去配置价格
            </Button>
          }
        />
      ) : null}

      <div className="page-panel reconciliation-panel">
        <div className="reconciliation-panel-heading">
          <div>
            <Typography.Title level={4}>出货明细</Typography.Title>
            <Typography.Text type="secondary">
              共 {formatCount(reconciliation?.details?.length)} 条，金额按“出货数量 × 鞋价”自动计算。
            </Typography.Text>
          </div>
        </div>
        <Table<MonthlyReconciliationDetail>
          rowKey="taskItemId"
          loading={loading}
          columns={detailColumns}
          dataSource={reconciliation?.details || []}
          pagination={{ defaultPageSize: 20, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }}
          scroll={{ x: 1400 }}
          rowClassName={(record) => (record.missingPrice ? "reconciliation-row-missing-price" : "")}
          summary={() => (
            <Table.Summary fixed>
              <Table.Summary.Row>
                <Table.Summary.Cell index={0} colSpan={4}>
                  <Typography.Text strong>总计</Typography.Text>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={4} align="right">
                  <Typography.Text strong>{formatCount(reconciliation?.totalOrderQuantity)}</Typography.Text>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={5} align="right">
                  <Typography.Text strong>{formatCount(reconciliation?.totalShippingQuantity)}</Typography.Text>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={6} />
                <Table.Summary.Cell index={7} />
                <Table.Summary.Cell index={8} align="right">
                  <Typography.Text strong>{formatAmount(reconciliation?.goodsAmount)}</Typography.Text>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={9} />
              </Table.Summary.Row>
            </Table.Summary>
          )}
        />
      </div>
    </div>
  );
}
