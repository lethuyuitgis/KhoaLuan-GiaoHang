import { Link, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useCart } from '@/features/cart/use-cart';

/**
 * Fixed 4-tab bottom navigation in the TCH style — Zalo build mirrors the
 * miniapp design 1:1 so the brand reads as the same shop.
 */
export function BottomNav() {
  const { t } = useTranslation();
  const { pathname } = useLocation();
  const cart = useCart();
  const itemCount = cart.totalItems();

  if (pathname === '/' || pathname === '') return null;

  const tabs: TabDef[] = [
    { to: '/customer/shop',   label: t('nav.home'),   match: p => p === '/customer/shop',                                                              Icon: IconHome },
    { to: '/customer/cart',   label: t('nav.cart'),   match: p => p.startsWith('/customer/cart') || p.startsWith('/customer/checkout'),                Icon: IconCart, badge: itemCount },
    { to: '/customer/orders', label: t('nav.orders'), match: p => p.startsWith('/customer/orders'),                                                    Icon: IconOrders },
  ];

  return (
    <nav
      role="navigation"
      aria-label={t('nav.label')}
      className="fixed bottom-0 inset-x-0 z-40 bg-white shadow-warm-up no-select"
      style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
    >
      <div className="max-w-md mx-auto flex h-16 items-stretch">
        {tabs.map(tab => {
          const active = tab.match(pathname);
          return (
            <Link
              key={tab.label}
              to={tab.to}
              className={
                'flex-1 flex flex-col items-center justify-center gap-0.5 transition active:scale-95 ' +
                (active ? 'text-brand-700' : 'text-brand-400')
              }
              aria-current={active ? 'page' : undefined}
            >
              <span className="relative inline-flex">
                <tab.Icon active={active} />
                {tab.badge !== undefined && tab.badge > 0 && (
                  <span
                    aria-label={t('nav.cartCount', { count: tab.badge })}
                    className="absolute -top-1.5 -right-2 min-w-[18px] h-[18px] px-1 inline-flex items-center justify-center rounded-full bg-brand-700 text-cream-50 text-[10px] font-bold ring-2 ring-white"
                  >
                    {tab.badge > 99 ? '99+' : tab.badge}
                  </span>
                )}
              </span>
              <span className={'text-[11px] leading-none mt-1 ' + (active ? 'font-semibold' : 'font-medium')}>
                {tab.label}
              </span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}

interface TabDef {
  to: string;
  label: string;
  match: (pathname: string) => boolean;
  Icon: (props: { active: boolean }) => JSX.Element;
  badge?: number;
}

function IconHome({ active }: { active: boolean }) {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={active ? 2.2 : 1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 11.5L12 4l9 7.5" />
      <path d="M5 10v10h14V10" />
      <path d="M10 20v-5h4v5" />
    </svg>
  );
}

function IconCart({ active }: { active: boolean }) {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={active ? 2.2 : 1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 4h2l2.4 11.4a2 2 0 0 0 2 1.6h7.7a2 2 0 0 0 2-1.5L21 8H6" />
      <circle cx="9" cy="20" r="1.5" />
      <circle cx="17" cy="20" r="1.5" />
    </svg>
  );
}

function IconOrders({ active }: { active: boolean }) {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={active ? 2.2 : 1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="4" y="5" width="16" height="16" rx="3" />
      <path d="M8 3v4M16 3v4M4 11h16" />
    </svg>
  );
}
