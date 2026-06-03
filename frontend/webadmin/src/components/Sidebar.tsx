import { NavLink } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { fetchAdminShopConfig } from '@shop/shared';
import { useAuthStore } from '@/stores/auth-store';
import { api } from '@/lib/api';

const ITEMS = [
  { to: '/',         label: 'Tổng quan',  icon: 'M3 12l9-9 9 9M5 10v10h4v-6h6v6h4V10' },
  { to: '/orders',   label: 'Đơn hàng',   icon: 'M3 7l9-4 9 4-9 4-9-4zm0 5l9 4 9-4M3 17l9 4 9-4' },
  { to: '/reports',  label: 'Báo cáo',    icon: 'M3 3v18h18M7 14l3-3 4 4 5-6' },
  { to: '/vouchers', label: 'Khuyến mãi', icon: 'M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z' },
  { to: '/products', label: 'Sản phẩm',   icon: 'M20 7l-8-4-8 4v10l8 4 8-4V7zm-8 4l8-4M12 11v9M4 7l8 4' },
  { to: '/shippers', label: 'Shipper',    icon: 'M3 17h2l2-7h10l2 7h2M6 17a2 2 0 104 0 2 2 0 00-4 0zm10 0a2 2 0 104 0 2 2 0 00-4 0z' },
  { to: '/settings', label: 'Cài đặt',    icon: 'M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065zM15 12a3 3 0 11-6 0 3 3 0 016 0z' },
];

function Icon({ d }: { d: string }) {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" className="w-5 h-5" fill="none"
         viewBox="0 0 24 24" stroke="currentColor" strokeWidth="1.8"
         strokeLinecap="round" strokeLinejoin="round">
      <path d={d} />
    </svg>
  );
}

export function Sidebar() {
  const logout = useAuthStore(s => s.logout);
  const auth = useAuthStore(s => s.auth);
  const emailFirst = auth?.email?.charAt(0).toUpperCase() ?? 'A';

  // Pulls shop name + tagline + brand color from /api/admin/shop-config so the
  // header reads as the current shop, not a hardcoded label. Falls back to a
  // safe default while loading or on error.
  const { data: cfg } = useQuery({
    queryKey: ['admin', 'shop-config'],
    queryFn: () => fetchAdminShopConfig(api),
    staleTime: 5 * 60_000,
    enabled: !!auth?.accessToken,
  });
  const shopName = cfg?.name ?? 'Shop Admin';
  const shopTagline = cfg?.tagline ?? 'Giao hàng nội thành';
  const brandColor = cfg?.brandPrimary ?? '#D97706';
  const brandInitial = shopName.charAt(0).toUpperCase();

  return (
    <aside className="w-64 bg-white border-r border-gray-200 h-screen sticky top-0 flex flex-col">
      {/* Brand */}
      <div className="px-5 py-5 border-b border-gray-100">
        <div className="flex items-center gap-2">
          <div
            className="w-9 h-9 rounded-xl flex items-center justify-center text-white font-bold shadow-sm"
            style={{ backgroundColor: brandColor }}
            title={shopName}>
            {cfg?.logoUrl
              ? <img src={cfg.logoUrl} alt="" className="w-7 h-7 object-contain" />
              : brandInitial}
          </div>
          <div className="min-w-0">
            <p className="font-semibold text-gray-900 leading-tight truncate" title={shopName}>{shopName}</p>
            <p className="text-[11px] text-gray-500 leading-tight truncate">{shopTagline}</p>
          </div>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 px-3 py-4 space-y-0.5">
        {ITEMS.map(item => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm transition-colors ${
                isActive
                  ? 'bg-orange-50 text-orange-700 font-semibold'
                  : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
              }`
            }
          >
            <Icon d={item.icon} />
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>

      {/* User card */}
      <div className="p-3 border-t border-gray-100">
        <div className="bg-gray-50 rounded-xl p-3 flex items-center gap-3">
          <div className="w-9 h-9 rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white font-semibold flex-shrink-0">
            {emailFirst}
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-xs text-gray-500 leading-tight">Đăng nhập</p>
            <p className="text-sm font-medium text-gray-900 truncate" title={auth?.email ?? ''}>
              {auth?.email ?? '—'}
            </p>
          </div>
          <button
            onClick={logout}
            title="Đăng xuất"
            className="text-gray-400 hover:text-red-600 transition-colors p-1.5 rounded-md hover:bg-white"
          >
            <svg xmlns="http://www.w3.org/2000/svg" className="w-4 h-4" fill="none"
                 viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2"
                 strokeLinecap="round" strokeLinejoin="round">
              <path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4M16 17l5-5-5-5M21 12H9" />
            </svg>
          </button>
        </div>
      </div>
    </aside>
  );
}
