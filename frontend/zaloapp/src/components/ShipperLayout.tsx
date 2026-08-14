import { Outlet, NavLink } from 'react-router-dom';

/**
 * Bottom tab navigation for the shipper section — Grab/Be style with
 * pill-shaped active indicator + larger touch targets + safe-area inset.
 */
export function ShipperLayout() {
  return (
    <div className="min-h-screen pb-24 bg-[#f6f2ec]">
      <Outlet />
      <nav
        className="fixed bottom-0 inset-x-0 bg-white/95 backdrop-blur-md border-t border-black/5 shadow-[0_-4px_20px_rgba(0,0,0,0.04)]"
        style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
      >
        <div className="flex justify-around items-center px-2 py-2 max-w-md mx-auto">
          <Tab to="/shipper/earnings"    iconActive={IconWalletFill} icon={IconWallet} label="Thu nhập" />
          <Tab to="/shipper/assignments" iconActive={IconListFill}   icon={IconList}   label="Đơn" />
          <Tab to="/shipper/wallet"      iconActive={IconCoinFill}   icon={IconCoin}   label="Ví" />
          <Tab to="/shipper/profile"     iconActive={IconUserFill}   icon={IconUser}   label="Profile" />
        </div>
      </nav>
    </div>
  );
}

function Tab({
  to,
  icon: Icon,
  iconActive: IconActive,
  label,
}: {
  to: string;
  icon: React.FC<{ className?: string }>;
  iconActive: React.FC<{ className?: string }>;
  label: string;
}) {
  return (
    <NavLink to={to} end={false} className="flex-1">
      {({ isActive }) => (
        <div
          className={`flex flex-col items-center gap-1 py-1.5 rounded-2xl transition-all ${
            isActive
              ? 'text-[var(--brand-primary)] scale-100'
              : 'text-gray-400'
          }`}
        >
          <div
            className={`relative h-7 w-12 flex items-center justify-center rounded-full transition-all ${
              isActive ? 'bg-[var(--brand-primary)]/12' : ''
            }`}
          >
            {isActive ? <IconActive className="w-5 h-5" /> : <Icon className="w-5 h-5" />}
          </div>
          <span className={`text-[10px] font-medium ${isActive ? 'font-semibold' : ''}`}>
            {label}
          </span>
        </div>
      )}
    </NavLink>
  );
}

// ───────────── Inline line icons (no extra npm dep) ─────────────
// Each icon receives an optional className for sizing/color via currentColor.

const IconWallet = ({ className }: { className?: string }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="M3 12V7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2" />
    <path d="M17 11h4v4h-4a2 2 0 0 1 0-4z" />
  </svg>
);
const IconWalletFill = ({ className }: { className?: string }) => (
  <svg className={className} viewBox="0 0 24 24" fill="currentColor">
    <path d="M5 5h14a2 2 0 0 1 2 2v3h-4a3 3 0 0 0 0 6h4v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z" />
    <circle cx="18" cy="13" r="1.3" />
  </svg>
);

const IconList = ({ className }: { className?: string }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3.5" y="5" width="17" height="3" rx="1" />
    <rect x="3.5" y="10.5" width="17" height="3" rx="1" />
    <rect x="3.5" y="16" width="17" height="3" rx="1" />
  </svg>
);
const IconListFill = ({ className }: { className?: string }) => (
  <svg className={className} viewBox="0 0 24 24" fill="currentColor">
    <rect x="3" y="4.5" width="18" height="4" rx="1.2" />
    <rect x="3" y="10" width="18" height="4" rx="1.2" />
    <rect x="3" y="15.5" width="18" height="4" rx="1.2" />
  </svg>
);

const IconCoin = ({ className }: { className?: string }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7v10M9 9.5h5a1.5 1.5 0 0 1 0 3H9a1.5 1.5 0 0 0 0 3h5" />
  </svg>
);
const IconCoinFill = ({ className }: { className?: string }) => (
  <svg className={className} viewBox="0 0 24 24" fill="currentColor">
    <path d="M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zm-3 6.5a1.5 1.5 0 0 1 1.5-1.5h3v1.5h-3v2h2.5a2.5 2.5 0 0 1 0 5H10.5v1.5h-1v-1.5h-1V15h4a1 1 0 0 0 0-2H10a2.5 2.5 0 0 1-2.5-2.5z" />
  </svg>
);

const IconUser = ({ className }: { className?: string }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="8" r="4" />
    <path d="M4 21a8 8 0 0 1 16 0" />
  </svg>
);
const IconUserFill = ({ className }: { className?: string }) => (
  <svg className={className} viewBox="0 0 24 24" fill="currentColor">
    <circle cx="12" cy="7.5" r="4" />
    <path d="M4 21a8 8 0 0 1 16 0H4z" />
  </svg>
);
