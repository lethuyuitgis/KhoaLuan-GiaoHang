import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { TelegramProvider } from './providers/TelegramProvider';
import { QueryProvider } from './providers/QueryProvider';
import { ThemeProvider } from './providers/ThemeProvider';
import { ErrorBoundary } from './components/ErrorBoundary';
import { Layout } from './components/Layout';
import { ShipperLayout } from './components/ShipperLayout';
import { ToastProvider } from './components/Toast';
import { SplashPage } from './pages/SplashPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { CatalogPage } from './pages/CatalogPage';
import { CartPage } from './pages/CartPage';
import { CheckoutPage } from './pages/CheckoutPage';
import { OrdersPage } from './pages/OrdersPage';
import { OrderDetailPage } from './pages/OrderDetailPage';
import { ShipperAssignmentsPage } from './pages/ShipperAssignmentsPage';
import { ShipperAssignmentDetailPage } from './pages/ShipperAssignmentDetailPage';
import { EarningsPage } from './pages/shipper/EarningsPage';
import { EarningsDetailPage } from './pages/shipper/EarningsDetailPage';
import { WalletPage } from './pages/shipper/WalletPage';
import { ShipperProfilePage } from './pages/shipper/ShipperProfilePage';

export default function App() {
  return (
    <ErrorBoundary>
      <QueryProvider>
        <ThemeProvider>
          <TelegramProvider>
            <ToastProvider>
              <BrowserRouter>
                <Routes>
                  <Route element={<Layout />}>
                    <Route index element={<SplashPage />} />
                    <Route path="customer/shop" element={<CatalogPage />} />
                    <Route path="customer/cart" element={<CartPage />} />
                    <Route path="customer/checkout" element={<CheckoutPage />} />
                    <Route path="customer/orders" element={<OrdersPage />} />
                    <Route path="customer/orders/:id" element={<OrderDetailPage />} />
                    <Route path="*" element={<NotFoundPage />} />
                  </Route>
                  <Route element={<ShipperLayout />}>
                    <Route path="shipper/earnings"         element={<EarningsPage />} />
                    <Route path="shipper/earnings/history" element={<EarningsDetailPage />} />
                    <Route path="shipper/wallet"           element={<WalletPage />} />
                    <Route path="shipper/profile"          element={<ShipperProfilePage />} />
                    <Route path="shipper/assignments"      element={<ShipperAssignmentsPage />} />
                    <Route path="shipper/assignments/:id"  element={<ShipperAssignmentDetailPage />} />
                  </Route>
                </Routes>
              </BrowserRouter>
            </ToastProvider>
          </TelegramProvider>
        </ThemeProvider>
      </QueryProvider>
    </ErrorBoundary>
  );
}
