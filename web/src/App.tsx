import {
  FileDoneOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  TableOutlined,
} from "@ant-design/icons";
import { Breadcrumb, Button, Layout, Menu, Tabs, Typography } from "antd";
import type { MenuProps, TabsProps } from "antd";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { useMemo, useState } from "react";

const { Header, Content, Sider } = Layout;

// 左侧导航固定两个核心工作台：订单台账和打印任务。
const navItems = [
  { path: "/orders", label: "订单列表", icon: <TableOutlined /> },
  { path: "/tasks", label: "打印列表", icon: <FileDoneOutlined /> },
];

export default function App() {
  const location = useLocation();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);

  const currentItem = navItems.find((item) => location.pathname.startsWith(item.path)) || navItems[0];

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
      navItems.map((item) => ({
        key: item.path,
        label: item.label,
        icon: item.icon,
      })),
    [],
  );

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
            <Breadcrumb
              items={[
                { title: "首页" },
                { title: currentItem.label },
              ]}
            />
          </div>
          <Typography.Text className="topbar-title">{currentItem.label}</Typography.Text>
        </Header>

        <div className="app-tabs">
          <Tabs
            activeKey={currentItem.path}
            items={tabItems}
            onChange={(key) => navigate(key)}
          />
        </div>

        <Content className="app-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
