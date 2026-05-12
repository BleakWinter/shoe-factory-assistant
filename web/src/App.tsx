import { FileDoneOutlined, TableOutlined } from "@ant-design/icons";
import { Button, Layout, Space, Typography } from "antd";
import { NavLink, Outlet, useLocation } from "react-router-dom";

const { Header, Content } = Layout;

const navItems = [
  { path: "/orders", label: "订单列表", icon: <TableOutlined /> },
  { path: "/tasks", label: "打印列表", icon: <FileDoneOutlined /> },
];

export default function App() {
  const location = useLocation();

  return (
    <Layout className="app-shell">
      <Header className="app-header">
        <div className="brand">
          <div className="brand-mark">印</div>
          <div>
            <Typography.Title level={4} className="brand-title">
              鞋厂订单打印助手
            </Typography.Title>
            <Typography.Text className="brand-subtitle">
              上传订单，自动整理格式，预览后打印订单和装箱单
            </Typography.Text>
          </div>
        </div>

        <Space wrap size={8} className="top-nav">
          {navItems.map((item) => (
            <NavLink key={item.path} to={item.path}>
              <Button
                type={location.pathname === item.path ? "primary" : "default"}
                icon={item.icon}
              >
                {item.label}
              </Button>
            </NavLink>
          ))}
        </Space>
      </Header>

      <Content className="app-content">
        <Outlet />
      </Content>
    </Layout>
  );
}
