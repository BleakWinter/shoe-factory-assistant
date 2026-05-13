import { createBrowserRouter, Navigate } from "react-router-dom";
import App from "../App";
import OrderWorkspacePage from "../pages/OrderWorkspacePage";
import PrintTaskListPage from "../pages/PrintTaskListPage";

export const router = createBrowserRouter([
  {
    path: "/",
    element: <App />,
    children: [
      // 默认进入订单列表；爸妈常用的上传入口在 /tasks。
      { index: true, element: <Navigate to="/orders" replace /> },
      { path: "orders", element: <OrderWorkspacePage /> },
      { path: "tasks", element: <PrintTaskListPage /> },
      { path: "*", element: <Navigate to="/orders" replace /> },
    ],
  },
]);
