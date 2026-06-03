import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';

type ToastVariant = 'success' | 'error' | 'info';

interface ToastMessage {
  id: number;
  text: string;
  variant: ToastVariant;
}

interface ToastApi {
  show: (text: string, variant?: ToastVariant) => void;
  success: (text: string) => void;
  error: (text: string) => void;
  info: (text: string) => void;
}

const ToastCtx = createContext<ToastApi | null>(null);

let counter = 0;

/**
 * Lightweight top-banner toast — auto-dismisses after 2.5s. Identical surface
 * to the Telegram miniapp Toast so pages can be copy-pasted across the two
 * platforms without touching feedback calls.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastMessage[]>([]);

  const show = useCallback((text: string, variant: ToastVariant = 'info') => {
    const id = ++counter;
    setItems(curr => [...curr, { id, text, variant }]);
    setTimeout(() => setItems(curr => curr.filter(i => i.id !== id)), 2500);
  }, []);

  const api: ToastApi = {
    show,
    success: text => show(text, 'success'),
    error: text => show(text, 'error'),
    info: text => show(text, 'info'),
  };

  return (
    <ToastCtx.Provider value={api}>
      {children}
      <div className="fixed top-4 left-4 right-4 max-w-md mx-auto z-50 pointer-events-none space-y-2">
        {items.map(t => (
          <ToastItem key={t.id} message={t} />
        ))}
      </div>
    </ToastCtx.Provider>
  );
}

function ToastItem({ message }: { message: ToastMessage }) {
  const [enter, setEnter] = useState(false);
  useEffect(() => {
    const r = requestAnimationFrame(() => setEnter(true));
    return () => cancelAnimationFrame(r);
  }, []);
  const palette =
    message.variant === 'success' ? 'bg-emerald-500 text-white shadow-emerald-500/30' :
    message.variant === 'error'   ? 'bg-red-500 text-white shadow-red-500/30' :
                                    'bg-gray-900 text-white shadow-black/20';
  return (
    <div
      role="status"
      className={
        `rounded-xl px-4 py-3 text-sm font-medium shadow-lg transform transition ` +
        `${palette} ` +
        (enter ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2')
      }
    >
      {message.text}
    </div>
  );
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastCtx);
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>');
  return ctx;
}
