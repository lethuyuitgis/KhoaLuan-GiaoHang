import { Outlet } from 'react-router-dom';

export function Layout() {
  // On desktop dev mode we frame the mobile-width page in a soft surround so
  // the centered 28rem column doesn't float on a vast empty canvas.
  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-100 to-slate-200 sm:py-6">
      <main className="max-w-md mx-auto min-h-screen sm:min-h-0 bg-white text-gray-900 sm:rounded-3xl sm:shadow-xl sm:ring-1 sm:ring-black/5 overflow-hidden px-4 pt-4 pb-24">
        <Outlet />
      </main>
    </div>
  );
}
