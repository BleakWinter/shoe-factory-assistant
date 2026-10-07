import { Alert, Button, Image, Input, Select, Space, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useState } from "react";
import type { Key } from "react";
import { toAssetUrl } from "../api/orderApi";
import type { PackingOrderItem } from "../types/packingOrder";
import type { InnerBoxType } from "../types/innerBoxType";
import { formatEmpty } from "../utils/format";
import BoxSpecInput from "./BoxSpecInput";

type EditableField = "boxSpec" | "remark";

interface Props {
  items: PackingOrderItem[];
  onChange?: (key: string, field: EditableField, value: string) => void;
  onRemove?: (key: string) => void;
  innerBoxTypes?: InnerBoxType[];
  onTypeChange?: (key: string, id: number) => void;
  onReloadTypes?: () => void;
}

export default function PackingOrderItems({ items, onChange, onRemove, innerBoxTypes = [], onTypeChange, onReloadTypes }: Props) {
  const [expandedKeys, setExpandedKeys] = useState<Key[]>([]);
  const toggle = (key: string) => setExpandedKeys((keys) => keys.includes(key)
    ? keys.filter((value) => value !== key) : [...keys, key]);
  const columns: ColumnsType<PackingOrderItem> = [
    { title: "开发款号 / 订单号", width: 200, render: (_, item) => (
      <Space align="start">
        {item.imageUrl && <Image width={48} height={42} style={{ objectFit: "contain" }} src={toAssetUrl(item.imageUrl)} />}
        <div><div>{formatEmpty(item.developmentNo)}</div><Typography.Text type="secondary">{formatEmpty(item.orderNo)}</Typography.Text></div>
      </Space>
    ) },
    { title: "内盒规格", dataIndex: "boxSpec", width: 150, render: formatEmpty },
    { title: "总数量（双）", dataIndex: "quantity", width: 110, align: "right", render: formatEmpty },
    { title: "装箱情况", width: 170, render: (_, item) => (
      <div className="packing-capacity-summary">
        {Object.entries(item.packingCapacities).filter(([, boxes]) => boxes > 0)
          .sort(([a], [b]) => Number(b) - Number(a))
          .map(([capacity, boxes]) => <span key={capacity}>{capacity}装：{boxes}箱</span>)}
        {item.packingProblems?.length ? <Typography.Text type="danger">需要核对装箱明细</Typography.Text> : null}
      </div>
    ) },
    { title: "内盒类型", dataIndex: "innerBoxTypeName", width: 180, render: (name) => name || "未设置" },
    { title: "备注", dataIndex: "remark", width: 220, render: formatEmpty },
    { title: "操作", width: onRemove ? 140 : 80, fixed: "right", render: (_, item) => (
      <Space><Button type="link" onClick={() => toggle(item.key)}>{expandedKeys.includes(item.key) ? "收起" : "展开"}</Button>
        {onRemove && <Button type="link" danger onClick={() => onRemove(item.key)}>移除</Button>}
      </Space>
    ) },
  ];

  return <Table<PackingOrderItem>
    rowKey="key"
    className="data-table packing-order-items"
    columns={columns}
    dataSource={items}
    pagination={false}
    scroll={{ x: 1120 }}
    locale={{ emptyText: "请先选择订单款号" }}
    expandable={{
      showExpandColumn: false,
      expandedRowKeys: expandedKeys,
      expandedRowRender: (item) => {
        const capacities = [...new Set([
          ...Array.from({ length: 13 }, (_, index) => String(14 - index)),
          ...Object.keys(item.packingCapacities || {}),
        ])].sort((a, b) => Number(b) - Number(a));
        const field = (key: EditableField, label: string) => <label className="packing-editor-field">
          <span>{label}</span>
          {onChange ? <Input value={item[key] || ""} maxLength={key === "remark" ? 500 : 100}
            onChange={(event) => onChange(item.key, key, event.target.value)}
            placeholder={key === "boxSpec" ? "填写内盒规格" : "按需要填写"} /> : <span>{formatEmpty(item[key])}</span>}
        </label>;
        return <div className="packing-item-details">
          {item.packingProblems?.length ? <Alert type="error" showIcon message="请先核对装箱明细"
            description={[...new Set(item.packingProblems)].map((problem) => <div key={problem}>{problem}</div>)} /> : null}
          <div className="packing-editor-fields">
            <div className="packing-editor-field"><span>内盒规格</span>
              {onChange ? <BoxSpecInput value={item.boxSpec} onChange={(value) => onChange(item.key, "boxSpec", value)} />
                : <span>{formatEmpty(item.boxSpec)}</span>}
            </div>
            <label className="packing-editor-field"><span>内盒类型</span>
              {onTypeChange ? <Select showSearch optionFilterProp="label" value={item.innerBoxTypeId ?? undefined}
                placeholder="选择内盒类型 / 版本" onChange={(id: number) => onTypeChange(item.key, id)}
                onDropdownVisibleChange={(open) => { if (open) onReloadTypes?.(); }}
                notFoundContent="暂无类型，请到配置中心新增" options={[
                  ...(item.innerBoxTypeId && !innerBoxTypes.some((type) => type.id === item.innerBoxTypeId)
                    ? [{ value: item.innerBoxTypeId, label: item.innerBoxTypeName || "类型不存在，请重新选择", disabled: true }] : []),
                  ...innerBoxTypes.map((type) => ({ value: type.id, label: `${type.name}${type.enabled ? "" : "（已停用）"}`, disabled: !type.enabled })),
                ]} /> : <span>{item.innerBoxTypeName || "未设置"}</span>}
            </label>
          </div>
          <>
            <Typography.Text type="secondary">各装法箱数（按装箱明细的箱数汇总）</Typography.Text>
            <div className="packing-capacity-grid">{capacities.map((capacity) => <div key={capacity}
              className={`packing-capacity-cell${item.packingCapacities?.[capacity] ? " packing-capacity-used" : ""}`}>
              <span>{capacity}装</span><strong>{item.packingCapacities?.[capacity] || 0} 箱</strong>
            </div>)}</div>
          </>
          {field("remark", "备注")}
          {item.imageUrl && <Image className="packing-detail-image" width={120} src={toAssetUrl(item.imageUrl)} />}
        </div>;
      },
    }}
  />;
}
