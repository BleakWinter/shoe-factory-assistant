import {
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from "@ant-design/icons";
import {
  App,
  Button,
  Cascader,
  Checkbox,
  Form,
  Image,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from "antd";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  createStyleConfig,
  fetchStyleConfigs,
  fetchStyleConfigDevelopmentNoOptions,
  fetchUnconfiguredDevelopmentNos,
  updateStyleConfig,
} from "../api/styleConfigApi";
import { toAssetUrl } from "../api/orderApi";
import type { DevelopmentNoOption } from "../types/order";
import type {
  StyleConfig,
  StyleConfigQueryParams,
  StyleConfigSavePayload,
} from "../types/styleConfig";
import { formatDateTime, formatEmpty } from "../utils/format";

interface FilterValues {
  developmentNoPaths?: string[][];
  incompleteOnly?: boolean;
}

interface EditorValues {
  developmentNo?: string;
  boxSpec?: string;
  netWeightPerPair?: number | null;
  grossWeightPerPair?: number | null;
}

function renderCompletionStatus(record: StyleConfig) {
  return record.complete ? <Tag color="green">已完善</Tag> : <Tag color="gold">待完善</Tag>;
}

function normalizeWeight(value?: number | null) {
  return value === undefined ? null : value;
}

function buildPayload(values: EditorValues): StyleConfigSavePayload {
  return {
    developmentNo: values.developmentNo,
    boxSpec: values.boxSpec?.trim() || null,
    netWeightPerPair: normalizeWeight(values.netWeightPerPair),
    grossWeightPerPair: normalizeWeight(values.grossWeightPerPair),
  };
}

export default function StyleConfigPage() {
  const { message } = App.useApp();
  const [filterForm] = Form.useForm<FilterValues>();
  const [editorForm] = Form.useForm<EditorValues>();
  const [configs, setConfigs] = useState<StyleConfig[]>([]);
  const [query, setQuery] = useState<StyleConfigQueryParams>({ page: 1, size: 20 });
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingConfig, setEditingConfig] = useState<StyleConfig | null>(null);
  const [developmentNoOptions, setDevelopmentNoOptions] = useState<DevelopmentNoOption[]>([]);
  const [unconfiguredDevelopmentNos, setUnconfiguredDevelopmentNos] = useState<string[]>([]);
  const [optionLoading, setOptionLoading] = useState(false);

  const loadConfigs = useCallback(async (params: StyleConfigQueryParams) => {
    setLoading(true);
    try {
      const page = await fetchStyleConfigs(params);
      setConfigs(page.records);
      setTotal(page.total);
    } catch (error) {
      setConfigs([]);
      setTotal(0);
      message.error(error instanceof Error ? error.message : "配置列表加载失败");
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void loadConfigs(query);
  }, [loadConfigs, query]);

  useEffect(() => {
    fetchStyleConfigDevelopmentNoOptions()
      .then(setDevelopmentNoOptions)
      .catch(() => setDevelopmentNoOptions([]));
  }, []);

  const loadUnconfiguredOptions = useCallback(async () => {
    setOptionLoading(true);
    try {
      setUnconfiguredDevelopmentNos(await fetchUnconfiguredDevelopmentNos());
    } catch (error) {
      setUnconfiguredDevelopmentNos([]);
      message.error(error instanceof Error ? error.message : "未配置开发编号加载失败");
    } finally {
      setOptionLoading(false);
    }
  }, [message]);

  const submitFilters = (values: FilterValues) => {
    const developmentNos = (values.developmentNoPaths || [])
      .map((path) => path[path.length - 1])
      .filter(Boolean)
      .join(",");
    setQuery({
      developmentNos,
      incompleteOnly: values.incompleteOnly,
      page: 1,
      size: query.size ?? 20,
    });
  };

  const resetFilters = () => {
    filterForm.resetFields();
    setQuery({ page: 1, size: query.size ?? 20 });
  };

  const openCreateEditor = async () => {
    setEditingConfig(null);
    editorForm.resetFields();
    setEditorOpen(true);
    await loadUnconfiguredOptions();
  };

  const openEditEditor = (record: StyleConfig) => {
    setEditingConfig(record);
    setEditorOpen(true);
    editorForm.setFieldsValue({
      developmentNo: record.developmentNo,
      boxSpec: record.boxSpec,
      netWeightPerPair: record.netWeightPerPair,
      grossWeightPerPair: record.grossWeightPerPair,
    });
  };

  const closeEditor = () => {
    setEditorOpen(false);
    setEditingConfig(null);
    editorForm.resetFields();
  };

  const saveEditor = async () => {
    const values = await editorForm.validateFields();
    setSaving(true);
    try {
      const payload = buildPayload(values);
      if (editingConfig) {
        await updateStyleConfig(editingConfig.id, payload);
        message.success("配置已更新");
      } else {
        await createStyleConfig(payload);
        message.success("配置已新建");
      }
      closeEditor();
      await loadConfigs(query);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "配置保存失败");
    } finally {
      setSaving(false);
    }
  };

  const columns = useMemo<ColumnsType<StyleConfig>>(
    () => [
      {
        title: "图片",
        dataIndex: "imageUrl",
        width: 86,
        fixed: "left",
        render: (value?: string) =>
          value ? (
            <Image
              src={toAssetUrl(value)}
              width={58}
              height={44}
              className="order-image"
              preview={{ mask: "查看" }}
            />
          ) : (
            <Tag>无图</Tag>
          ),
      },
      { title: "开发编号", dataIndex: "developmentNo", width: 190, fixed: "left", render: formatEmpty },
      { title: "盒规", dataIndex: "boxSpec", minWidth: 180, render: formatEmpty },
      {
        title: "净重/双 kg",
        dataIndex: "netWeightPerPair",
        width: 130,
        align: "right",
        render: formatEmpty,
      },
      {
        title: "毛重/双 kg",
        dataIndex: "grossWeightPerPair",
        width: 130,
        align: "right",
        render: formatEmpty,
      },
      { title: "状态", key: "complete", width: 110, render: (_, record) => renderCompletionStatus(record) },
      { title: "更新时间", dataIndex: "updatedAt", width: 170, render: formatDateTime },
      {
        title: "操作",
        key: "actions",
        width: 110,
        fixed: "right",
        render: (_, record) => (
          <Button icon={<EditOutlined />} onClick={() => openEditEditor(record)}>
            编辑
          </Button>
        ),
      },
    ],
    [],
  );

  const pagination: TablePaginationConfig = {
    current: query.page,
    pageSize: query.size,
    total,
    showSizeChanger: true,
    showTotal: (count) => `共 ${count} 条`,
  };

  return (
    <div className="workspace">
      <div className="toolbar-band">
        <div>
          <Typography.Title level={3}>盒规配置</Typography.Title>
          <Typography.Text type="secondary">
            按开发编号维护盒规、净重/双、毛重/双；订单上传和识别会自动补待完善配置。
          </Typography.Text>
        </div>
        <Space wrap>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => void openCreateEditor()}>
            新建
          </Button>
          <Button icon={<ReloadOutlined />} onClick={() => void loadConfigs(query)}>
            刷新
          </Button>
        </Space>
      </div>

      <div className="page-panel">
        <Form form={filterForm} layout="inline" className="filter-form" onFinish={submitFilters}>
          <Form.Item name="developmentNoPaths" label="开发编号">
            <Cascader
              allowClear
              className="style-cascader"
              displayRender={(labels) => labels.join("-")}
              maxTagCount="responsive"
              multiple
              options={developmentNoOptions}
              placeholder="开发编号：253 / 1 / 20"
              showCheckedStrategy={Cascader.SHOW_CHILD}
              showSearch
            />
          </Form.Item>
          <Form.Item name="incompleteOnly" valuePropName="checked">
            <Checkbox>只看待完善</Checkbox>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                查询
              </Button>
              <Button onClick={resetFilters}>重置</Button>
            </Space>
          </Form.Item>
        </Form>

        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={configs}
          pagination={pagination}
          scroll={{ x: 1096 }}
          className="data-table"
          onChange={(nextPagination) => {
            setQuery((prev) => ({
              ...prev,
              page: nextPagination.current ?? 1,
              size: nextPagination.pageSize ?? 20,
            }));
          }}
        />
      </div>

      <Modal
        open={editorOpen}
        title={editingConfig ? `编辑配置：${editingConfig.developmentNo}` : "新建配置"}
        onCancel={closeEditor}
        onOk={() => void saveEditor()}
        confirmLoading={saving}
        destroyOnClose
      >
        <Form form={editorForm} layout="vertical">
          <Form.Item
            name="developmentNo"
            label="开发编号"
            rules={[{ required: true, message: "请选择开发编号" }]}
          >
            {editingConfig ? (
              <Input disabled />
            ) : (
              <Select
                allowClear
                showSearch
                loading={optionLoading}
                placeholder="选择未配置开发编号"
                options={unconfiguredDevelopmentNos.map((developmentNo) => ({
                  label: developmentNo,
                  value: developmentNo,
                }))}
              />
            )}
          </Form.Item>
          <Form.Item name="boxSpec" label="盒规">
            <Input allowClear placeholder="例如 30x20x10" />
          </Form.Item>
          <Form.Item name="netWeightPerPair" label="净重/双 kg">
            <InputNumber min={0} precision={3} step={0.001} className="full-width-control" />
          </Form.Item>
          <Form.Item name="grossWeightPerPair" label="毛重/双 kg">
            <InputNumber min={0} precision={3} step={0.001} className="full-width-control" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
