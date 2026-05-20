import {
  DollarOutlined,
  FileExcelOutlined,
  FileDoneOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SettingOutlined,
  ShoppingCartOutlined,
  TableOutlined,
  UnorderedListOutlined,
} from "@ant-design/icons";
import { Breadcrumb, Button, Layout, Menu, Tabs, Typography } from "antd";
import type { MenuProps, TabsProps } from "antd";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";

const { Header, Content, Sider } = Layout;

interface NavLeafItem {
  path: string;
  label: string;
  icon: ReactNode;
  parentKey?: string;
  parentLabel?: string;
}

// 左侧导航固定常用工作台、打印中心和配置中心入口。
const navLeafItems: NavLeafItem[] = [
  { path: "/orders", label: "订单列表", icon: <TableOutlined /> },
  {
    path: "/tasks/order",
    label: "打印订单和装箱单",
    icon: <FileDoneOutlined />,
    parentKey: "print-center",
    parentLabel: "打印中心",
  },
  {
    path: "/tasks/outer-carton-label",
    label: "打印外箱贴标",
    icon: <FileDoneOutlined />,
    parentKey: "print-center",
    parentLabel: "打印中心",
  },
  {
    path: "/tasks/inner-box-label",
    label: "打印内盒贴标",
    icon: <FileDoneOutlined />,
    parentKey: "print-center",
    parentLabel: "打印中心",
  },
  {
    path: "/tasks/shipping-note",
    label: "打印出货单",
    icon: <FileDoneOutlined />,
    parentKey: "print-center",
    parentLabel: "打印中心",
  },
  {
    path: "/shipping-notes",
    label: "出货单记录",
    icon: <UnorderedListOutlined />,
    parentKey: "print-center",
    parentLabel: "打印中心",
  },
  {
    path: "/component-orders/packing",
    label: "订包装",
    icon: <FileExcelOutlined />,
    parentKey: "component-order-center",
    parentLabel: "下单中心",
  },
  {
    path: "/component-orders/outsole",
    label: "订大底",
    icon: <FileExcelOutlined />,
    parentKey: "component-order-center",
    parentLabel: "下单中心",
  },
  {
    path: "/component-orders/insole",
    label: "订中底",
    icon: <FileExcelOutlined />,
    parentKey: "component-order-center",
    parentLabel: "下单中心",
  },
  {
    path: "/component-orders/heel",
    label: "订鞋跟",
    icon: <FileExcelOutlined />,
    parentKey: "component-order-center",
    parentLabel: "下单中心",
  },
  {
    path: "/style-configs",
    label: "盒规配置",
    icon: <SettingOutlined />,
    parentKey: "config-center",
    parentLabel: "配置中心",
  },
  {
    path: "/price-configs",
    label: "价格配置",
    icon: <DollarOutlined />,
    parentKey: "config-center",
    parentLabel: "配置中心",
  },
];

const menuItems: MenuProps["items"] = [
  { key: "/orders", label: "订单列表", icon: <TableOutlined /> },
  {
    key: "print-center",
    label: "打印中心",
    icon: <FileDoneOutlined />,
    children: [
      { key: "/tasks/order", label: "打印订单和装箱单" },
      { key: "/tasks/outer-carton-label", label: "打印外箱贴标" },
      { key: "/tasks/inner-box-label", label: "打印内盒贴标" },
      { key: "/tasks/shipping-note", label: "打印出货单" },
      { key: "/shipping-notes", label: "出货单记录" },
    ],
  },
  {
    key: "component-order-center",
    label: "下单中心",
    icon: <ShoppingCartOutlined />,
    children: [
      { key: "/component-orders/packing", label: "订包装" },
      { key: "/component-orders/outsole", label: "订大底" },
      { key: "/component-orders/insole", label: "订中底" },
      { key: "/component-orders/heel", label: "订鞋跟" },
    ],
  },
  {
    key: "config-center",
    label: "配置中心",
    icon: <SettingOutlined />,
    children: [
      { key: "/style-configs", label: "盒规配置", icon: <SettingOutlined /> },
      { key: "/price-configs", label: "价格配置", icon: <DollarOutlined /> },
    ],
  },
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
  const [openMenuKeys, setOpenMenuKeys] = useState<string[]>([]);

  const currentItem =
    navLeafItems.find(
      (item) => location.pathname === item.path || location.pathname.startsWith(`${item.path}/`),
    ) || navLeafItems[0];
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

  useEffect(() => {
    if (!currentItem.parentKey) {
      return;
    }
    setOpenMenuKeys((prev) =>
      prev.includes(currentItem.parentKey as string) ? prev : [...prev, currentItem.parentKey as string],
    );
  }, [currentItem.parentKey]);

  const breadcrumbItems = isOrderDetailPage
    ? [
        { title: "首页" },
        { title: <a onClick={() => navigate("/orders")}>订单列表</a> },
        { title: "订单明细" },
      ]
    : currentItem.parentLabel
      ? [
          { title: "首页" },
          { title: currentItem.parentLabel },
          { title: currentItem.label },
        ]
    : [
        { title: "首页" },
        { title: currentItem.label },
      ];

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
        width={200}
        collapsedWidth={60}
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
          openKeys={collapsed ? [] : openMenuKeys}
          items={menuItems}
          onClick={({ key }) => {
            if (typeof key === "string" && key.startsWith("/")) {
              navigate(key);
            }
          }}
          onOpenChange={setOpenMenuKeys}
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
