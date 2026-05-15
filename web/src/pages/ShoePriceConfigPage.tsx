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
  createShoePriceConfig,
  fetchShoePriceConfigDevelopmentNoOptions,
  fetchShoePriceConfigs,
  fetchUnpricedDevelopmentNos,
  updateShoePriceConfig,
} from "../api/priceConfigApi";
import type { DevelopmentNoOption } from "../types/order";
import type {
  ShoePriceConfig,
  ShoePriceConfigQueryParams,
  ShoePriceConfigSavePayload,
} from "../types/priceConfig";
import { formatDateTime, formatEmpty } from "../utils/format";

interface FilterValues {
  developmentNoPaths?: string[][];
  incompleteOnly?: boolean;
}

interface EditorValues {
  developmentNo?: string;
  shoePrice?: number | null;
}

function renderCompletionStatus(record: ShoePriceConfig) {
  return record.complete ? <Tag color="green">已配置</Tag> : <Tag color="gold">待配置</Tag>;
}

function normalizePrice(value?: number | null) {
  return value === undefined ? null : value;
}

function buildPayload(values: EditorValues): ShoePriceConfigSavePayload {
  return {
    developmentNo: values.developmentNo,
    shoePrice: normalizePrice(values.shoePrice),
  };
}

export default function ShoePriceConfigPage() {
  const { message } = App.useApp();
  const [filterForm] = Form.useForm<FilterValues>();
  const [editorForm] = Form.useForm<EditorValues>();
  const [configs, setConfigs] = useState<ShoePriceConfig[]>([]);
  const [query, setQuery] = useState<ShoePriceConfigQueryParams>({ page: 1, size: 20 });
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingConfig, setEditingConfig] = useState<ShoePriceConfig | null>(null);
  const [developmentNoOptions, setDevelopmentNoOptions] = useState<DevelopmentNoOption[]>([]);
  const [unpricedDevelopmentNos, setUnpricedDevelopmentNos] = useState<string[]>([]);
  const [optionLoading, setOptionLoading] = useState(false);

  const loadConfigs = useCallback(async (params: ShoePriceConfigQueryParams) => {
    setLoading(true);
    try {
      const page = await fetchShoePriceConfigs(params);
      setConfigs(page.records);
      setTotal(page.total);
    } catch (error) {
      setConfigs([]);
      setTotal(0);
      message.error(error instanceof Error ? error.message : "价格配置列表加载失败");
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void loadConfigs(query);
  }, [loadConfigs, query]);

  useEffect(() => {
    fetchShoePriceConfigDevelopmentNoOptions()
      .then(setDevelopmentNoOptions)
      .catch(() => setDevelopmentNoOptions([]));
  }, []);

  const loadUnpricedOptions = useCallback(async () => {
    setOptionLoading(true);
    try {
      setUnpricedDevelopmentNos(await fetchUnpricedDevelopmentNos());
    } catch (error) {
      setUnpricedDevelopmentNos([]);
      message.error(error instanceof Error ? error.message : "未配置价格开发编号加载失败");
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
    await loadUnpricedOptions();
  };

  const openEditEditor = (record: ShoePriceConfig) => {
    setEditingConfig(record);
    setEditorOpen(true);
    editorForm.setFieldsValue({
      developmentNo: record.developmentNo,
      shoePrice: record.shoePrice,
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
        await updateShoePriceConfig(editingConfig.id, payload);
        message.success("价格配置已更新");
      } else {
        await createShoePriceConfig(payload);
        message.success("价格配置已新建");
      }
      closeEditor();
      await loadConfigs(query);
      fetchShoePriceConfigDevelopmentNoOptions()
        .then(setDevelopmentNoOptions)
        .catch(() => setDevelopmentNoOptions([]));
    } catch (error) {
      message.error(error instanceof Error ? error.message : "价格配置保存失败");
    } finally {
      setSaving(false);
    }
  };

  const columns = useMemo<ColumnsType<ShoePriceConfig>>(
    () => [
      { title: "开发编号", dataIndex: "developmentNo", width: 220, fixed: "left", render: formatEmpty },
      {
        title: "鞋子单价",
        dataIndex: "shoePrice",
        minWidth: 180,
        align: "right",
        render: formatEmpty,
      },
      { title: "状态", key: "complete", width: 120, render: (_, record) => renderCompletionStatus(record) },
      { title: "更新时间", dataIndex: "updatedAt", width: 180, render: formatDateTime },
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
          <Typography.Title level={3}>价格配置</Typography.Title>
          <Typography.Text type="secondary">
            按开发编号维护鞋子单价；订单上传和识别会自动补待配置的开发编号。
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
            <Checkbox>只看待配置</Checkbox>
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
          scroll={{ x: 810 }}
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
        title={editingConfig ? `编辑价格：${editingConfig.developmentNo}` : "新建价格配置"}
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
                placeholder="选择未配置价格的开发编号"
                options={unpricedDevelopmentNos.map((developmentNo) => ({
                  label: developmentNo,
                  value: developmentNo,
                }))}
              />
            )}
          </Form.Item>
          <Form.Item name="shoePrice" label="鞋子单价">
            <InputNumber min={0} precision={2} step={0.01} className="full-width-control" placeholder="例如 128.00" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
