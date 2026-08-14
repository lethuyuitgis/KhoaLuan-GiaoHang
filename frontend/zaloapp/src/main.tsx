import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './i18n';
import './styles/globals.css';

// Zalo runtime KHÔNG dùng index.html của app → có thể không có #root; tự tạo nếu thiếu.
let rootEl = document.getElementById('root');
if (!rootEl) {
  rootEl = document.createElement('div');
  rootEl.id = 'root';
  document.body.appendChild(rootEl);
}

createRoot(rootEl).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
