import { createBrowserRouter, Navigate } from "react-router-dom";
import App from "../App";
import OrderDetailPage from "../pages/OrderDetailPage";
import OrderWorkspacePage from "../pages/OrderWorkspacePage";
import PrintTaskListPage from "../pages/PrintTaskListPage";
import ShoePriceConfigPage from "../pages/ShoePriceConfigPage";
import StyleConfigPage from "../pages/StyleConfigPage";

export const router = createBrowserRouter([
  {
    path: "/",
    element: <App />,
    children: [
      // 默认进入订单列表；爸妈常用的上传入口在 /tasks。
      { index: true, element: <Navigate to="/orders" replace /> },
      { path: "orders", element: <OrderWorkspacePage /> },
      { path: "orders/:id/details", element: <OrderDetailPage /> },
      { path: "tasks", element: <PrintTaskListPage /> },
      { path: "style-configs", element: <StyleConfigPage /> },
      { path: "price-configs", element: <ShoePriceConfigPage /> },
      { path: "*", element: <Navigate to="/orders" replace /> },
    ],
  },
]);
