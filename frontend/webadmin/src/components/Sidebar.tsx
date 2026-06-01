import { NavLink } from 'react-router-dom';
import { useAuthStore } from '@/stores/auth-store';

const ITEMS = [
  { to: '/', label: 'Dashboard', icon: '📊' },
  { to: '/orders', label: 'Đơn hàng', icon: '📦' },
  { to: '/reports', label: 'Báo cáo', icon: '📈' },
  { to: '/products', label: 'Sản phẩm', icon: '🛍️' },
  { to: '/shippers', label: 'Shipper', icon: '🚴' },
];

export function Sidebar() {
  const logout = useAuthStore(s => s.logout);
  const auth = useAuthStore(s => s.auth);

  return (
    <aside className="w-64 bg-white border-r border-gray-200 h-screen sticky top-0 flex flex-col">
      <div className="p-4 border-b border-gray-200">
        <h1 className="font-bold text-lg">🚚 Shop Admin</h1>
      </div>
      <nav className="flex-1 p-3 space-y-1">
        {ITEMS.map(item => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2 rounded-md text-sm ${
                isActive
                  ? 'bg-brand-50 text-brand-700 font-medium'
                  : 'text-gray-700 hover:bg-gray-100'
              }`
            }
          >
            <span>{item.icon}</span>
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>
      <div className="p-3 border-t border-gray-200 text-sm">
        <p className="text-gray-700 truncate mb-2">{auth?.email}</p>
        <button
          onClick={logout}
          className="w-full px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded-md text-left"
        >
          Đăng xuất
        </button>
      </div>
    </aside>
  );
}
