import { lazy, Suspense } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { QueryProvider } from './providers/QueryProvider';
import { ErrorBoundary } from './components/ErrorBoundary';
import { AuthGuard } from './components/AuthGuard';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { DashboardPage } from './pages/DashboardPage';
import { OrdersPage } from './pages/OrdersPage';
import { OrderDetailPage } from './pages/OrderDetailPage';
import { ProductsPage } from './pages/ProductsPage';
import { ProductFormPage } from './pages/ProductFormPage';
import { ShippersPage } from './pages/ShippersPage';
import { SettingsPage } from './pages/SettingsPage';

// P8 IM-03 follow-up: lazy-load /reports (recharts ~80 kB gzipped).
// Most sessions never open /reports → ship a smaller initial bundle.
const ReportsPage = lazy(() =>
  import('./pages/ReportsPage').then((mod) => ({ default: mod.ReportsPage }))
);

const ReportsFallback = () => (
  <div className="p-6 text-gray-500">Đang tải báo cáo…</div>
);

export default function App() {
  return (
    <ErrorBoundary>
      <QueryProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<AuthGuard />}>
              <Route element={<Layout />}>
                <Route index element={<DashboardPage />} />
                <Route path="orders" element={<OrdersPage />} />
                <Route path="orders/:id" element={<OrderDetailPage />} />
                <Route path="products" element={<ProductsPage />} />
                <Route path="products/new" element={<ProductFormPage />} />
                <Route path="products/:id/edit" element={<ProductFormPage />} />
                <Route path="shippers" element={<ShippersPage />} />
                <Route path="settings" element={<SettingsPage />} />
                <Route
                  path="reports"
                  element={
                    <Suspense fallback={<ReportsFallback />}>
                      <ReportsPage />
                    </Suspense>
                  }
                />
                <Route path="*" element={<NotFoundPage />} />
              </Route>
            </Route>
            <Route path="*" element={<Navigate to="/login" replace />} />
          </Routes>
        </BrowserRouter>
      </QueryProvider>
    </ErrorBoundary>
  );
}
