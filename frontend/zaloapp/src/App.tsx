import { BrowserRouter, HashRouter, Route, Routes } from 'react-router-dom';
import { ZaloProvider } from './providers/ZaloProvider';
import { QueryProvider } from './providers/QueryProvider';
import { ThemeProvider } from './providers/ThemeProvider';
import { ErrorBoundary } from './components/ErrorBoundary';
import { Layout } from './components/Layout';
import { ToastProvider } from './components/Toast';
import { SplashPage } from './pages/SplashPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { CatalogPage } from './pages/CatalogPage';
import { CartPage } from './pages/CartPage';
import { CheckoutPage } from './pages/CheckoutPage';
import { OrdersPage } from './pages/OrdersPage';
import { OrderDetailPage } from './pages/OrderDetailPage';
import { ShipperLayout } from './components/ShipperLayout';
import { ShipperAssignmentsPage } from './pages/ShipperAssignmentsPage';
import { ShipperAssignmentDetailPage } from './pages/ShipperAssignmentDetailPage';
import { EarningsPage } from './pages/shipper/EarningsPage';
import { EarningsDetailPage } from './pages/shipper/EarningsDetailPage';
import { WalletPage } from './pages/shipper/WalletPage';
import { ShipperProfilePage } from './pages/shipper/ShipperProfilePage';

export default function App() {
  // Zalo Mini App webview không dùng path/basename như web thường → dùng HashRouter
  // (route qua #/…), tránh trắng trang do basename sai. Dev/prod web giữ BrowserRouter.
  const isZalo = import.meta.env.MODE === 'zalo';
  const Router = isZalo ? HashRouter : BrowserRouter;
  return (
    <ErrorBoundary>
      <QueryProvider>
        <ThemeProvider>
        <ZaloProvider>
          <ToastProvider>
            {/* basename theo Vite base: '/' khi dev, '/zaloapp/' khi build prod —
                thiếu nó thì mở app tại /zaloapp/ là rơi thẳng vào route 404. */}
            <Router basename={isZalo ? undefined : import.meta.env.BASE_URL}>
              <Routes>
                <Route element={<Layout />}>
                  <Route index element={<SplashPage />} />
                  <Route path="customer/shop" element={<CatalogPage />} />
                  <Route path="customer/cart" element={<CartPage />} />
                  <Route path="customer/checkout" element={<CheckoutPage />} />
                  <Route path="customer/orders" element={<OrdersPage />} />
                  <Route path="customer/orders/:id" element={<OrderDetailPage />} />
                </Route>
                <Route element={<ShipperLayout />}>
                  <Route path="shipper/assignments" element={<ShipperAssignmentsPage />} />
                  <Route path="shipper/assignments/:id" element={<ShipperAssignmentDetailPage />} />
                  <Route path="shipper/earnings" element={<EarningsPage />} />
                  <Route path="shipper/earnings/history" element={<EarningsDetailPage />} />
                  <Route path="shipper/wallet" element={<WalletPage />} />
                  <Route path="shipper/profile" element={<ShipperProfilePage />} />
                </Route>
                <Route path="*" element={<NotFoundPage />} />
              </Routes>
            </Router>
          </ToastProvider>
        </ZaloProvider>
        </ThemeProvider>
      </QueryProvider>
    </ErrorBoundary>
  );
}
