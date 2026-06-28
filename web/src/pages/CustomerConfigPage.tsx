import {
  EditOutlined,
  PlusOutlined,
  SearchOutlined,
} from "@ant-design/icons";
import {
  App,
  Button,
  Checkbox,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from "antd";
import type { FormInstance } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo, useState } from "react";

type FeatureGroupKey = "orderFeatures" | "printFeatures" | "componentFeatures" | "configFeatures";

interface FeatureOption {
  label: string;
  value: string;
}

interface FeatureGroup {
  key: FeatureGroupKey;
  title: string;
  options: FeatureOption[];
}

interface CustomerConfig {
  id: number;
  code: string;
  name: string;
  enabled: boolean;
  featureKeys: string[];
  updatedAt: string;
}

interface CustomerEditorValues {
  name?: string;
  code?: string;
  enabled?: boolean;
  orderFeatures?: string[];
  printFeatures?: string[];
  componentFeatures?: string[];
  configFeatures?: string[];
}

interface FilterValues {
  keyword?: string;
  enabled?: boolean;
}

const featureGroups: FeatureGroup[] = [
  {
    key: "orderFeatures",
    title: "订单与数据",
    options: [
      { label: "订单列表", value: "order-list" },
      { label: "手工新增订单", value: "manual-order" },
      { label: "文件上传", value: "file-upload" },
      { label: "订单识别", value: "order-recognition" },
      { label: "装箱单识别", value: "packing-recognition" },
      { label: "数据统计", value: "statistics" },
    ],
  },
  {
    key: "printFeatures",
    title: "打印中心",
    options: [
      { label: "打印订单和装箱单", value: "print-order" },
      { label: "打印外箱贴标", value: "print-outer-label" },
      { label: "打印内盒贴标", value: "print-inner-label" },
      { label: "打印出货单", value: "print-shipping-note" },
    ],
  },
  {
    key: "componentFeatures",
    title: "下单中心",
    options: [
      { label: "订包装", value: "component-packing" },
      { label: "订大底", value: "component-outsole" },
      { label: "订中底", value: "component-insole" },
      { label: "订鞋跟", value: "component-heel" },
    ],
  },
  {
    key: "configFeatures",
    title: "配置中心",
    options: [
      { label: "价格配置", value: "price-config" },
      { label: "盒规配置", value: "style-config" },
    ],
  },
];

const allFeatureOptions = featureGroups.flatMap((group) => group.options);
const featureLabelMap = new Map(allFeatureOptions.map((option) => [option.value, option.label]));

const daweiFeatures = allFeatureOptions
  .map((option) => option.value)
  .filter((featureKey) => featureKey !== "manual-order");

const commonManualFeatures = [
  "order-list",
  "manual-order",
  "statistics",
  "print-outer-label",
  "print-inner-label",
  "print-shipping-note",
  "price-config",
];

const initialCustomers: CustomerConfig[] = [
  {
    id: 1,
    code: "DAWEI",
    name: "达为",
    enabled: true,
    featureKeys: daweiFeatures,
    updatedAt: "2026-06-28 10:30",
  },
  {
    id: 2,
    code: "BAOXIANG",
    name: "宝翔",
    enabled: true,
    featureKeys: commonManualFeatures,
    updatedAt: "2026-06-28 10:30",
  },
  {
    id: 3,
    code: "TIANTUZI",
    name: "天兔子",
    enabled: true,
    featureKeys: commonManualFeatures,
    updatedAt: "2026-06-28 10:30",
  },
];

function getEditorValues(customer?: CustomerConfig | null): CustomerEditorValues {
  const featureKeys = customer?.featureKeys ?? ["order-list", "statistics"];
  return {
    name: customer?.name,
    code: customer?.code,
    enabled: customer?.enabled ?? true,
    ...Object.fromEntries(
      featureGroups.map((group) => [
        group.key,
        group.options
          .filter((option) => featureKeys.includes(option.value))
          .map((option) => option.value),
      ]),
    ),
  };
}

function flattenFeatureKeys(values: CustomerEditorValues) {
  return featureGroups.flatMap((group) => values[group.key] ?? []);
}

function FeatureGroupField({
  form,
  group,
}: {
  form: FormInstance<CustomerEditorValues>;
  group: FeatureGroup;
}) {
  const selectedValues = Form.useWatch(group.key, form) ?? [];
  const allValues = group.options.map((option) => option.value);
  const allChecked = selectedValues.length === allValues.length;
  const partlyChecked = selectedValues.length > 0 && !allChecked;

  return (
    <div className="customer-feature-group">
      <div className="customer-feature-heading">
        <Typography.Text strong>{group.title}</Typography.Text>
        <Checkbox
          checked={allChecked}
          indeterminate={partlyChecked}
          onChange={(event) => {
            form.setFieldValue(group.key, event.target.checked ? allValues : []);
          }}
        >
          全选
        </Checkbox>
      </div>
      <Form.Item name={group.key} noStyle>
        <Checkbox.Group className="customer-feature-options" options={group.options} />
      </Form.Item>
    </div>
  );
}

export default function CustomerConfigPage() {
  const { message } = App.useApp();
  const [filterForm] = Form.useForm<FilterValues>();
  const [editorForm] = Form.useForm<CustomerEditorValues>();
  const [customers, setCustomers] = useState<CustomerConfig[]>(initialCustomers);
  const [filters, setFilters] = useState<FilterValues>({});
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingCustomer, setEditingCustomer] = useState<CustomerConfig | null>(null);

  const visibleCustomers = useMemo(() => {
    const keyword = filters.keyword?.trim().toLowerCase();
    return customers.filter((customer) => {
      const keywordMatched =
        !keyword ||
        customer.name.toLowerCase().includes(keyword) ||
        customer.code.toLowerCase().includes(keyword);
      const statusMatched = filters.enabled === undefined || customer.enabled === filters.enabled;
      return keywordMatched && statusMatched;
    });
  }, [customers, filters]);

  const openCreateEditor = () => {
    setEditingCustomer(null);
    editorForm.setFieldsValue(getEditorValues());
    setEditorOpen(true);
  };

  const openEditEditor = (customer: CustomerConfig) => {
    setEditingCustomer(customer);
    editorForm.setFieldsValue(getEditorValues(customer));
    setEditorOpen(true);
  };

  const closeEditor = () => {
    setEditorOpen(false);
    setEditingCustomer(null);
    editorForm.resetFields();
  };

  const saveEditor = async () => {
    const values = await editorForm.validateFields();
    const featureKeys = flattenFeatureKeys(values);
    if (featureKeys.length === 0) {
      message.warning("请至少选择一个功能");
      return;
    }

    const normalizedCode = values.code!.trim().toUpperCase();
    const duplicate = customers.some(
      (customer) => customer.code === normalizedCode && customer.id !== editingCustomer?.id,
    );
    if (duplicate) {
      message.error("客户编码已存在");
      return;
    }

    const nextCustomer: CustomerConfig = {
      id: editingCustomer?.id ?? Math.max(0, ...customers.map((customer) => customer.id)) + 1,
      name: values.name!.trim(),
      code: normalizedCode,
      enabled: values.enabled ?? true,
      featureKeys,
      updatedAt: "刚刚",
    };

    setCustomers((current) =>
      editingCustomer
        ? current.map((customer) => (customer.id === editingCustomer.id ? nextCustomer : customer))
        : [nextCustomer, ...current],
    );
    message.success(editingCustomer ? "客户配置已更新" : "客户已新增");
    closeEditor();
  };

  const toggleCustomer = (customer: CustomerConfig, enabled: boolean) => {
    setCustomers((current) =>
      current.map((item) => (item.id === customer.id ? { ...item, enabled, updatedAt: "刚刚" } : item)),
    );
    message.success(`${customer.name}已${enabled ? "启用" : "停用"}`);
  };

  const columns = useMemo<ColumnsType<CustomerConfig>>(
    () => [
      {
        title: "客户名称",
        dataIndex: "name",
        width: 125,
        render: (name: string, customer) => (
          <div className="customer-name-cell">
            <span className="customer-avatar">{name.slice(0, 1)}</span>
            <Typography.Text strong>{customer.name}</Typography.Text>
          </div>
        ),
      },
      {
        title: "客户编码",
        dataIndex: "code",
        width: 120,
        render: (code: string) => <Typography.Text code>{code}</Typography.Text>,
      },
      {
        title: "已开通功能",
        dataIndex: "featureKeys",
        width: 340,
        render: (featureKeys: string[]) => {
          const visibleFeatures = featureKeys.slice(0, 4);
          return (
            <Space size={[4, 6]} wrap>
              {visibleFeatures.map((featureKey) => (
                <Tag key={featureKey}>{featureLabelMap.get(featureKey)}</Tag>
              ))}
              {featureKeys.length > visibleFeatures.length && (
                <Tag color="blue">+{featureKeys.length - visibleFeatures.length} 项</Tag>
              )}
            </Space>
          );
        },
      },
      {
        title: "功能数",
        dataIndex: "featureKeys",
        width: 70,
        align: "center",
        render: (featureKeys: string[]) => `${featureKeys.length} 项`,
      },
      {
        title: "状态",
        dataIndex: "enabled",
        width: 105,
        render: (enabled: boolean, customer) => (
          <Space size={8}>
            <Switch
              size="small"
              checked={enabled}
              onChange={(checked) => toggleCustomer(customer, checked)}
            />
            <Typography.Text type={enabled ? undefined : "secondary"}>
              {enabled ? "启用" : "停用"}
            </Typography.Text>
          </Space>
        ),
      },
      {
        title: "更新时间",
        dataIndex: "updatedAt",
        width: 135,
      },
      {
        title: "操作",
        key: "actions",
        width: 75,
        render: (_, customer) => (
          <Button type="link" icon={<EditOutlined />} onClick={() => openEditEditor(customer)}>
            编辑
          </Button>
        ),
      },
    ],
    [],
  );

  return (
    <div className="workspace customer-config-page">
      <div className="toolbar-band">
        <div>
          <Typography.Title level={3}>客户配置</Typography.Title>
          <Typography.Text type="secondary">
            共 {customers.length} 个客户，{customers.filter((customer) => customer.enabled).length} 个已启用
          </Typography.Text>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreateEditor}>
          新增客户
        </Button>
      </div>

      <div className="page-panel">
        <Form
          form={filterForm}
          layout="inline"
          className="customer-filter-form"
          onFinish={setFilters}
        >
          <Form.Item name="keyword" label="客户">
            <Input allowClear placeholder="客户名称或编码" />
          </Form.Item>
          <Form.Item name="enabled" label="状态">
            <Select
              allowClear
              placeholder="全部状态"
              options={[
                { label: "启用", value: true },
                { label: "停用", value: false },
              ]}
            />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                查询
              </Button>
              <Button
                onClick={() => {
                  filterForm.resetFields();
                  setFilters({});
                }}
              >
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>

        <Table
          rowKey="id"
          className="data-table customer-config-table"
          columns={columns}
          dataSource={visibleCustomers}
          pagination={false}
        />
      </div>

      <Modal
        open={editorOpen}
        width={760}
        title={editingCustomer ? `编辑客户：${editingCustomer.name}` : "新增客户"}
        okText="保存"
        cancelText="取消"
        onCancel={closeEditor}
        onOk={() => void saveEditor()}
        forceRender
      >
        <Form
          form={editorForm}
          layout="vertical"
          initialValues={getEditorValues()}
          className="customer-editor-form"
        >
          <div className="customer-editor-basics">
            <Form.Item
              name="name"
              label="客户名称"
              rules={[{ required: true, whitespace: true, message: "请输入客户名称" }]}
            >
              <Input allowClear maxLength={50} placeholder="例如：达为" />
            </Form.Item>
            <Form.Item
              name="code"
              label="客户编码"
              rules={[
                { required: true, whitespace: true, message: "请输入客户编码" },
                {
                  pattern: /^[A-Za-z0-9_-]+$/,
                  message: "仅支持字母、数字、下划线和短横线",
                },
              ]}
            >
              <Input allowClear maxLength={30} placeholder="例如：DAWEI" />
            </Form.Item>
            <Form.Item name="enabled" label="客户状态" valuePropName="checked">
              <Switch checkedChildren="启用" unCheckedChildren="停用" />
            </Form.Item>
          </div>

          <div className="customer-feature-title">
            <Typography.Text strong>功能范围</Typography.Text>
            <Form.Item noStyle shouldUpdate>
              {({ getFieldsValue }) => (
                <Typography.Text type="secondary">
                  已选择 {flattenFeatureKeys(getFieldsValue()).length} 项
                </Typography.Text>
              )}
            </Form.Item>
          </div>
          <div className="customer-feature-list">
            {featureGroups.map((group) => (
              <FeatureGroupField key={group.key} form={editorForm} group={group} />
            ))}
          </div>
        </Form>
      </Modal>
    </div>
  );
}
