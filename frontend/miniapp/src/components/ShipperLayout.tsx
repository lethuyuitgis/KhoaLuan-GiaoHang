import { Outlet, NavLink } from 'react-router-dom';

export function ShipperLayout() {
  return (
    <div className="min-h-screen pb-16 bg-gray-50">
      <Outlet />
      <nav
        className="fixed bottom-0 inset-x-0 bg-white border-t border-gray-200 flex justify-around py-2"
        style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
      >
        <Tab to="/shipper/earnings"    icon="💰" label="Thu nhập" />
        <Tab to="/shipper/assignments" icon="📋" label="Đơn" />
        <Tab to="/shipper/wallet"      icon="👛" label="Ví" />
        <Tab to="/shipper/profile"     icon="👤" label="Profile" />
      </nav>
    </div>
  );
}

function Tab({ to, icon, label }: { to: string; icon: string; label: string }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        `flex flex-col items-center text-xs ${isActive ? 'text-[var(--brand-primary)] font-semibold' : 'text-gray-500'}`
      }
    >
      <span className="text-xl">{icon}</span>
      <span>{label}</span>
    </NavLink>
  );
}
