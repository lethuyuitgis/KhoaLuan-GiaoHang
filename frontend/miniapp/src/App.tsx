import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { TelegramProvider } from './providers/TelegramProvider';
import { QueryProvider } from './providers/QueryProvider';
import { ErrorBoundary } from './components/ErrorBoundary';
import { Layout } from './components/Layout';
import { SplashPage } from './pages/SplashPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { CatalogPage } from './pages/CatalogPage';
import { CartPage } from './pages/CartPage';

export default function App() {
  return (
    <ErrorBoundary>
      <QueryProvider>
        <TelegramProvider>
          <BrowserRouter>
            <Routes>
              <Route element={<Layout />}>
                <Route index element={<SplashPage />} />
                <Route path="customer/shop" element={<CatalogPage />} />
                <Route path="customer/cart" element={<CartPage />} />
                <Route path="*" element={<NotFoundPage />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </TelegramProvider>
      </QueryProvider>
    </ErrorBoundary>
  );
}
