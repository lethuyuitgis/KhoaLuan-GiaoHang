import { Outlet, useLocation } from 'react-router-dom';
import { BottomNav } from './BottomNav';

/**
 * Mobile-first shell with cream page background. The bottom navigation lives
 * outside <main> so it stays fixed; we reserve `pb-20` to keep the last
 * card from being hidden behind it.
 */
export function Layout() {
  const { pathname } = useLocation();
  // Splash page wants the hero to span the full viewport with no padding.
  const isSplash = pathname === '/' || pathname === '';
  // Shipper screens preserve their original flat layout.
  const isShipper = pathname.startsWith('/shipper');

  return (
    <div className="min-h-screen bg-brand-50 text-brand-800">
      <main
        className={
          'max-w-md mx-auto min-h-screen ' +
          (isSplash ? '' : isShipper ? 'px-4 pt-4 pb-6' : 'pb-24')
        }
      >
        <Outlet />
      </main>
      <BottomNav />
    </div>
  );
}
