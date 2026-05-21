import { Outlet } from 'react-router-dom';

export function Layout() {
  return (
    <div className="min-h-screen bg-tg-bg text-tg-text">
      <main className="max-w-md mx-auto px-4 pt-4 pb-24">
        <Outlet />
      </main>
    </div>
  );
}
