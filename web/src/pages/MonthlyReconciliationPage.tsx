import { ReloadOutlined } from "@ant-design/icons";
import { Alert, App, Button, Input, Select, Statistic, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  fetchMonthlyReconciliation,
} from "../api/reconciliationApi";
import { useCustomers } from "../hooks/useCustomers";
import { localToday } from "../api/manualOrderApi";
import type {
  MonthlyReconciliation,
  MonthlyReconciliationDetail,
} from "../types/reconciliation";

function currentMonth() {
  return localToday().slice(0,7);
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
  const [customerId,setCustomerId]=useState<number>();
  const customers=useCustomers();
  const [reconciliation,setReconciliation]=useState<MonthlyReconciliation>();
  const [loading,setLoading]=useState(false);
  const requestSequence=useRef(0);
  useEffect(()=>{
    if(customers.length) setCustomerId(current=>current ?? customers[0].id);
  },[customers]);

  const loadReconciliation = useCallback(async () => {
    if (!month || !customerId) {
      return;
    }
    setLoading(true);
    const sequence=++requestSequence.current;
    setReconciliation(undefined);
    try {
      const result=await fetchMonthlyReconciliation(month, customerId);
      if(sequence===requestSequence.current)setReconciliation(result);
    } catch (error) {
      if(sequence!==requestSequence.current)return;
      message.error(error instanceof Error ? error.message : "月度对账加载失败");
    } finally {
      if(sequence===requestSequence.current)setLoading(false);
    }
  }, [customerId, message, month]);

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
              value={customerId}
              options={customers.map(c=>({label:c.recipientName,value:c.id}))}
              onChange={setCustomerId}
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
        message="按实际出货日期归入月份，出货数量取对应订单明细的全部数量；价格按当前客户配置计算，修改价格后历史对账同步更新。"
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
            <Button size="small" onClick={() => navigate(`/unpriced-styles?month=${month}&customerId=${customerId}`)}>
              查看待定价款号
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
