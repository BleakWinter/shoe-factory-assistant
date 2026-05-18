import { createBrowserRouter, Navigate } from "react-router-dom";
import App from "../App";
import ComponentOrderPage from "../pages/ComponentOrderPage";
import OrderDetailPage from "../pages/OrderDetailPage";
import OrderWorkspacePage from "../pages/OrderWorkspacePage";
import ShippingNoteHistoryPage from "../pages/ShippingNoteHistoryPage";
import ShippingNotePrintPage from "../pages/ShippingNotePrintPage";
import PrintSelectionPage from "../pages/PrintSelectionPage";
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
      { path: "tasks", element: <Navigate to="/tasks/order" replace /> },
      { path: "tasks/order", element: <PrintTaskListPage /> },
      { path: "tasks/outer-carton-label", element: <PrintSelectionPage key="outer-carton-label" title="打印外箱贴标" /> },
      { path: "tasks/inner-box-label", element: <PrintSelectionPage key="inner-box-label" title="打印内盒贴标" /> },
      { path: "tasks/shipping-note", element: <ShippingNotePrintPage /> },
      { path: "shipping-notes", element: <ShippingNoteHistoryPage /> },
      { path: "component-orders", element: <Navigate to="/component-orders/packing" replace /> },
      { path: "component-orders/packing", element: <ComponentOrderPage key="component-order-packing" title="订包装" processType={1} /> },
      { path: "component-orders/outsole", element: <ComponentOrderPage key="component-order-outsole" title="订大底" processType={2} /> },
      { path: "component-orders/insole", element: <ComponentOrderPage key="component-order-insole" title="订中底" processType={3} /> },
      { path: "component-orders/heel", element: <ComponentOrderPage key="component-order-heel" title="订鞋跟" processType={4} /> },
      { path: "style-configs", element: <StyleConfigPage /> },
      { path: "price-configs", element: <ShoePriceConfigPage /> },
      { path: "*", element: <Navigate to="/orders" replace /> },
    ],
  },
]);
