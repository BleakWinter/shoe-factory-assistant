import { EditOutlined, PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import { App, Button, Form, Input, Modal, Space, Switch, Table, Typography } from "antd";
import { useCallback, useEffect, useState } from "react";
import { fetchInnerBoxTypes, saveInnerBoxType } from "../api/innerBoxTypeApi";
import type { InnerBoxType, InnerBoxTypePayload } from "../types/innerBoxType";
import { formatDateTime, formatEmpty } from "../utils/format";

export default function InnerBoxTypeConfigPage() {
  const { message } = App.useApp();
  const [types, setTypes] = useState<InnerBoxType[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<InnerBoxType>();
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [changingId, setChangingId] = useState<number>();
  const [form] = Form.useForm<InnerBoxTypePayload>();
  const load = useCallback(async () => {
    setLoading(true);
    try { setTypes(await fetchInnerBoxTypes()); }
    catch (error) { message.error(error instanceof Error ? error.message : "内盒类型加载失败"); }
    finally { setLoading(false); }
  }, [message]);
  useEffect(() => { void load(); }, [load]);

  function edit(type?: InnerBoxType) {
    setEditing(type);
    form.resetFields();
    form.setFieldsValue(type || { enabled: true });
    setOpen(true);
  }
  async function save() {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await saveInnerBoxType(editing?.id, { ...values, name: values.name.trim(), remark: values.remark?.trim() });
      setOpen(false);
      message.success("内盒类型已保存");
      await load();
    } catch (error) { message.error(error instanceof Error ? error.message : "内盒类型保存失败"); }
    finally { setSaving(false); }
  }
  async function toggle(type: InnerBoxType, enabled: boolean) {
    setChangingId(type.id);
    try { await saveInnerBoxType(type.id, { ...type, enabled }); await load(); }
    catch (error) { message.error(error instanceof Error ? error.message : "状态修改失败"); }
    finally { setChangingId(undefined); }
  }

  return <div className="workspace inner-box-type-config-page">
    <div className="toolbar-band">
      <div><Typography.Title level={3}>内盒类型</Typography.Title>
        <Typography.Text type="secondary">每种内盒版本分别建一个类型，订包装时选择对应版本。</Typography.Text></div>
      <Space><Button icon={<ReloadOutlined />} loading={loading} onClick={() => void load()}>刷新</Button>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => edit()}>新增内盒类型</Button></Space>
    </div>
    <div className="page-panel">
      <Input.Search allowClear placeholder="搜索类型名称或备注" value={keyword} onChange={(event) => setKeyword(event.target.value)}
        style={{ maxWidth: 320, marginBottom: 16 }} />
      <Table<InnerBoxType> rowKey="id" loading={loading} className="data-table" scroll={{ x: 850 }}
        dataSource={types.filter((type) => `${type.name} ${type.remark || ""}`.toLowerCase().includes(keyword.trim().toLowerCase()))}
        pagination={{ pageSize: 20, showSizeChanger: true }} locale={{ emptyText: "暂无内盒类型，请先新增" }} columns={[
          { title: "内盒类型 / 版本", dataIndex: "name", width: 240 },
          { title: "备注", dataIndex: "remark", render: formatEmpty },
          { title: "状态", width: 110, render: (_, type) => <Switch checked={type.enabled} loading={changingId === type.id}
            disabled={changingId !== undefined} checkedChildren="启用" unCheckedChildren="停用" onChange={(enabled) => void toggle(type, enabled)} /> },
          { title: "更新时间", dataIndex: "updatedAt", width: 180, render: formatDateTime },
          { title: "操作", width: 100, render: (_, type) => <Button icon={<EditOutlined />} onClick={() => edit(type)}>编辑</Button> },
        ]} />
    </div>
    <Modal open={open} title={editing ? "编辑内盒类型" : "新增内盒类型"} okText="保存" cancelText="取消"
      confirmLoading={saving} onCancel={() => { if (!saving) setOpen(false); }} onOk={() => void save()} forceRender>
      <Form name="inner-box-type-editor" form={form} layout="vertical">
        <Form.Item name="name" label="内盒类型 / 版本" rules={[{ required: true, whitespace: true, message: "请输入内盒类型名称" }]}>
          <Input maxLength={100} placeholder="例如 标准盒第一版、标准盒第二版" />
        </Form.Item>
        <Form.Item name="remark" label="备注"><Input.TextArea maxLength={500} rows={3} /></Form.Item>
        <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
      </Form>
    </Modal>
  </div>;
}
