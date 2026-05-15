import {
  DollarOutlined,
  FileDoneOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SettingOutlined,
  TableOutlined,
} from "@ant-design/icons";
import { Breadcrumb, Button, Layout, Menu, Tabs, Typography } from "antd";
import type { MenuProps, TabsProps } from "antd";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";

const { Header, Content, Sider } = Layout;

// 左侧导航固定常用工作台和配置入口。
const navItems = [
  { path: "/orders", label: "订单列表", icon: <TableOutlined /> },
  { path: "/tasks", label: "打印列表", icon: <FileDoneOutlined /> },
  { path: "/style-configs", label: "盒规配置", icon: <SettingOutlined /> },
  { path: "/price-configs", label: "价格配置", icon: <DollarOutlined /> },
];

interface WorkspaceTab {
  key: string;
  path: string;
  label: string;
  icon: ReactNode;
  state?: unknown;
}

export default function App() {
  const location = useLocation();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);
  const [workspaceTabs, setWorkspaceTabs] = useState<WorkspaceTab[]>([]);

  const currentItem = navItems.find((item) => location.pathname.startsWith(item.path)) || navItems[0];
  const isOrderDetailPage = /^\/orders\/[^/]+\/details/.test(location.pathname);
  const routeState = location.state as { order?: { orderNo?: string; id?: number } } | null;
  const pageTitle = isOrderDetailPage ? "订单明细" : currentItem.label;
  const currentTab = useMemo<WorkspaceTab>(() => {
    if (isOrderDetailPage) {
      const orderLabel = routeState?.order?.orderNo || routeState?.order?.id;
      return {
        key: "order-detail",
        path: location.pathname,
        label: orderLabel ? `订单明细 ${orderLabel}` : "订单明细",
        icon: <TableOutlined />,
        state: location.state,
      };
    }
    return {
      key: currentItem.path,
      path: currentItem.path,
      label: currentItem.label,
      icon: currentItem.icon,
    };
  }, [currentItem.icon, currentItem.label, currentItem.path, isOrderDetailPage, location.pathname, location.state, routeState?.order?.id, routeState?.order?.orderNo]);

  useEffect(() => {
    setWorkspaceTabs((prev) => {
      const existing = prev.find((item) => item.key === currentTab.key);
      if (existing) {
        return prev.map((item) => (item.key === currentTab.key ? currentTab : item));
      }
      return [...prev, currentTab];
    });
  }, [currentTab]);

  const breadcrumbItems = isOrderDetailPage
    ? [
        { title: "首页" },
        { title: <a onClick={() => navigate("/orders")}>订单列表</a> },
        { title: "订单明细" },
      ]
    : [
        { title: "首页" },
        { title: currentItem.label },
      ];

  const menuItems = useMemo<MenuProps["items"]>(
    () =>
      navItems.map((item) => ({
        key: item.path,
        icon: item.icon,
        label: item.label,
      })),
    [],
  );

  const tabItems = useMemo<TabsProps["items"]>(
    () =>
      workspaceTabs.map((item) => ({
        key: item.key,
        label: item.label,
        icon: item.icon,
        closable: workspaceTabs.length > 1,
      })),
    [workspaceTabs],
  );

  const closeWorkspaceTab = (targetKey: string) => {
    setWorkspaceTabs((prev) => {
      if (prev.length <= 1) {
        return prev;
      }
      const targetIndex = prev.findIndex((item) => item.key === targetKey);
      const nextTabs = prev.filter((item) => item.key !== targetKey);
      if (targetKey === currentTab.key) {
        const nextActive = nextTabs[Math.max(0, targetIndex - 1)] || nextTabs[0];
        navigate(nextActive.path, { state: nextActive.state });
      }
      return nextTabs;
    });
  };

  const switchWorkspaceTab = (targetKey: string) => {
    const target = workspaceTabs.find((item) => item.key === targetKey);
    if (target) {
      navigate(target.path, { state: target.state });
    }
  };

  return (
    <Layout className="app-shell">
      <Sider
        className="app-sider"
        width={248}
        collapsedWidth={68}
        breakpoint="lg"
        collapsed={collapsed}
        onBreakpoint={setCollapsed}
        onCollapse={setCollapsed}
      >
        <div className="brand">
          <div className="brand-mark">印</div>
          <div className="brand-copy">
            <Typography.Title level={4} className="brand-title">
              鞋厂打印助手
            </Typography.Title>
            <Typography.Text className="brand-subtitle">
              订单与装箱单台账
            </Typography.Text>
          </div>
        </div>

        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[currentItem.path]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>

      <Layout className="main-shell">
        <Header className="app-topbar">
          <div className="topbar-left">
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed((value) => !value)}
            />
            <Breadcrumb items={breadcrumbItems} />
          </div>
          <Typography.Text className="topbar-title">{pageTitle}</Typography.Text>
        </Header>

        <div className="app-tabs">
          <Tabs
            hideAdd
            type="editable-card"
            activeKey={currentTab.key}
            items={tabItems}
            onChange={switchWorkspaceTab}
            onEdit={(targetKey, action) => {
              if (action === "remove" && typeof targetKey === "string") {
                closeWorkspaceTab(targetKey);
              }
            }}
          />
        </div>

        <Content className="app-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
