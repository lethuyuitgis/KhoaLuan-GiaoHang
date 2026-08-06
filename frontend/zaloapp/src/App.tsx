import { BrowserRouter, Route, Routes } from 'react-router-dom';
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

export default function App() {
  return (
    <ErrorBoundary>
      <QueryProvider>
        <ThemeProvider>
        <ZaloProvider>
          <ToastProvider>
            {/* basename theo Vite base: '/' khi dev, '/zaloapp/' khi build prod —
                thiếu nó thì mở app tại /zaloapp/ là rơi thẳng vào route 404. */}
            <BrowserRouter basename={import.meta.env.BASE_URL}>
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
              </Routes>
            </BrowserRouter>
          </ToastProvider>
        </ZaloProvider>
        </ThemeProvider>
      </QueryProvider>
    </ErrorBoundary>
  );
}
