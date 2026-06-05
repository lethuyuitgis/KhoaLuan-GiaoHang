import { useQuery } from '@tanstack/react-query';
import { fetchShipperSelfProfile } from '@shop/shared';
import { api } from '@/lib/api';

export function ShipperProfilePage() {
  const { data: p, isLoading } = useQuery({
    queryKey: ['shipper', 'profile'],
    queryFn: () => fetchShipperSelfProfile(api),
  });
  if (isLoading || !p) return <p className="p-4">Đang tải…</p>;
  return (
    <div className="p-4 space-y-4">
      <section className="bg-white rounded-2xl p-5 text-center shadow-sm">
        <div className="w-20 h-20 rounded-full bg-gradient-to-br from-[var(--brand-primary)] to-[var(--brand-primary-dark,#ea580c)] mx-auto mb-3 flex items-center justify-center text-3xl text-white">
          🛵
        </div>
        <h2 className="text-xl font-bold">{p.name}</h2>
        <p className="text-sm text-gray-500 mt-1">{p.phone}</p>
        <p className="text-yellow-500 mt-2">★ {Number(p.ratingAvg).toFixed(2)}</p>
      </section>
      <section className="grid grid-cols-2 gap-3">
        <Stat label="Tổng đơn đã giao" value={p.totalOrders.toString()} />
        <Stat
          label="Ngày tham gia"
          value={new Date(p.joinedAt).toLocaleDateString('vi-VN')}
        />
      </section>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-white rounded-xl p-3 shadow-sm">
      <p className="text-xs text-gray-500">{label}</p>
      <p className="font-bold mt-1">{value}</p>
    </div>
  );
}
