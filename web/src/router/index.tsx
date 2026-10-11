import { createBrowserRouter, Navigate } from "react-router-dom";
import App from "../App";
import ComponentOrderPage from "../pages/ComponentOrderPage";
import CustomerConfigPage from "../pages/CustomerConfigPage";
import OrderDetailsRoute from "../pages/OrderDetailsRoute";
import ManualOrderPage from "../pages/ManualOrderPage";
import OrderWorkspacePage from "../pages/OrderWorkspacePage";
import PackingOrderPage from "../pages/PackingOrderPage";
import MonthlyReconciliationPage from "../pages/MonthlyReconciliationPage";
import UnpricedStylesPage from "../pages/UnpricedStylesPage";
import ShippingNotePrintPage from "../pages/ShippingNotePrintPage";
import PrintSelectionPage from "../pages/PrintSelectionPage";
import PrintTaskListPage from "../pages/PrintTaskListPage";
import ShoePriceConfigPage from "../pages/ShoePriceConfigPage";
import QuotationListPage from "../pages/QuotationListPage";
import QuotationEditorPage from "../pages/QuotationEditorPage";
import ShoeStatisticsPage from "../pages/ShoeStatisticsPage";
import StyleConfigPage from "../pages/StyleConfigPage";
import InnerBoxTypeConfigPage from "../pages/InnerBoxTypeConfigPage";

export const router = createBrowserRouter([
  {
    path: "/",
    element: <App />,
    children: [
      // 默认进入订单列表；爸妈常用的上传入口在 /tasks。
      { index: true, element: <Navigate to="/orders" replace /> },
      { path: "orders", element: <OrderWorkspacePage /> },
      { path: "statistics", element: <ShoeStatisticsPage /> },
      { path: "monthly-reconciliation", element: <MonthlyReconciliationPage /> },
      { path: "unpriced-styles", element: <UnpricedStylesPage /> },
      { path: "orders/:id/details", element: <OrderDetailsRoute /> },
      { path: "orders/:id/manual", element: <ManualOrderPage /> },
      { path: "tasks", element: <Navigate to="/tasks/order" replace /> },
      { path: "tasks/order", element: <PrintTaskListPage /> },
      { path: "tasks/outer-carton-label", element: <PrintSelectionPage key="outer-carton-label" title="打印外箱贴标" /> },
      { path: "tasks/inner-box-label", element: <PrintSelectionPage key="inner-box-label" title="打印内盒贴标" /> },
      { path: "tasks/shipping-note", element: <ShippingNotePrintPage /> },
      { path: "shipping-notes", element: <Navigate to="/tasks/shipping-note" replace /> },
      { path: "component-orders", element: <Navigate to="/component-orders/packing" replace /> },
      { path: "component-orders/packing", element: <PackingOrderPage /> },
      { path: "component-orders/outsole", element: <ComponentOrderPage key="component-order-outsole" title="订大底" processType={2} /> },
      { path: "component-orders/insole", element: <ComponentOrderPage key="component-order-insole" title="订中底" processType={3} /> },
      { path: "component-orders/heel", element: <ComponentOrderPage key="component-order-heel" title="订鞋跟" processType={4} /> },
      { path: "customer-configs", element: <CustomerConfigPage /> },
      { path: "style-configs", element: <StyleConfigPage /> },
      { path: "inner-box-types", element: <InnerBoxTypeConfigPage /> },
      { path: "price-configs", element: <ShoePriceConfigPage /> },
      { path: "price-configs/quotations", element: <QuotationListPage /> },
      { path: "price-configs/quotations/new", element: <QuotationEditorPage /> },
      { path: "price-configs/quotations/:id", element: <QuotationEditorPage /> },
      { path: "*", element: <Navigate to="/orders" replace /> },
    ],
  },
]);
