import { Outlet, useLocation } from 'react-router-dom';
import { BottomNav } from './BottomNav';

export function Layout() {
  const { pathname } = useLocation();
  const isSplash = pathname === '/' || pathname === '';

  return (
    <div className="min-h-screen bg-brand-50 text-brand-800">
      <main
        className={
          'max-w-md mx-auto min-h-screen ' +
          (isSplash ? '' : 'pb-24')
        }
      >
        <Outlet />
      </main>
      <BottomNav />
    </div>
  );
}
