import { NavLink } from 'react-router-dom';
import { useAuthStore } from '@/stores/auth-store';

const ITEMS = [
  { to: '/',         label: 'Tổng quan',  icon: 'M3 12l9-9 9 9M5 10v10h4v-6h6v6h4V10' },
  { to: '/orders',   label: 'Đơn hàng',   icon: 'M3 7l9-4 9 4-9 4-9-4zm0 5l9 4 9-4M3 17l9 4 9-4' },
  { to: '/reports',  label: 'Báo cáo',    icon: 'M3 3v18h18M7 14l3-3 4 4 5-6' },
  { to: '/products', label: 'Sản phẩm',   icon: 'M20 7l-8-4-8 4v10l8 4 8-4V7zm-8 4l8-4M12 11v9M4 7l8 4' },
  { to: '/shippers', label: 'Shipper',    icon: 'M3 17h2l2-7h10l2 7h2M6 17a2 2 0 104 0 2 2 0 00-4 0zm10 0a2 2 0 104 0 2 2 0 00-4 0z' },
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

  return (
    <aside className="w-64 bg-white border-r border-gray-200 h-screen sticky top-0 flex flex-col">
      {/* Brand */}
      <div className="px-5 py-5 border-b border-gray-100">
        <div className="flex items-center gap-2">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-orange-500 to-red-600 flex items-center justify-center text-white font-bold shadow-sm">
            S
          </div>
          <div>
            <p className="font-semibold text-gray-900 leading-tight">Shop Admin</p>
            <p className="text-[11px] text-gray-500 leading-tight">Giao hàng nội thành</p>
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
