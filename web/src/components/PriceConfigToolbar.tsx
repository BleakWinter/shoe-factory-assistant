import { PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import { Button, Tabs, Typography } from "antd";
import { useNavigate } from "react-router-dom";

interface Props {
  activeTab: "prices" | "quotations";
  loading: boolean;
  onRefresh: () => void;
  onCreate: () => void;
}

export default function PriceConfigToolbar({ activeTab, loading, onRefresh, onCreate }: Props) {
  const navigate = useNavigate();
  const isQuotation = activeTab === "quotations";
  const createLabel = isQuotation ? "新建报价" : "新建价格";
  return <div className="toolbar-band price-config-toolbar">
    <div className="price-config-heading">
      <Typography.Title level={3}>价格配置</Typography.Title>
      <Typography.Text type="secondary">{isQuotation
        ? "在系统里算成本、保存报价；确认采用后才会更新客户标价。"
        : "同一款号按客户分别定价。出货查询使用当前价，已确认的结算单保留当时的价格。"}</Typography.Text>
    </div>
    <div className="price-config-actions">
      <Tabs className="price-config-tabs" activeKey={activeTab}
        items={[{ key: "prices", label: "客户标价" }, { key: "quotations", label: "报价管理" }]}
        onChange={key => navigate(key === "quotations" ? "/price-configs/quotations" : "/price-configs")}/>
      <div className="price-config-action-group" role="group" aria-label={isQuotation ? "报价操作" : "价格操作"}>
        <Button aria-label="刷新" icon={<ReloadOutlined/>} loading={loading} onClick={onRefresh}>刷新</Button>
        <Button aria-label={createLabel} type="primary" icon={<PlusOutlined/>} onClick={onCreate}>{createLabel}</Button>
      </div>
    </div>
  </div>;
}
