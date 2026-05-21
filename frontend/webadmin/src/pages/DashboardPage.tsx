import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';

interface OrderSummary {
  id: string;
  code: string;
  total: number;
  status: string;
  paymentMethod: string;
  paymentStatus: string;
  createdAt: string;
}

interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
}

export function DashboardPage() {
  const { data: orders, isLoading } = useQuery({
    queryKey: ['admin', 'orders', 'summary'],
    queryFn: async () => {
      const { data } = await api.get<Page<OrderSummary>>('/api/admin/orders?size=50');
      return data;
    },
  });

  const stats = {
    total: orders?.totalElements ?? 0,
    pending: orders?.content.filter(o => o.status === 'PENDING').length ?? 0,
    delivering: orders?.content.filter(o => o.status === 'DELIVERING').length ?? 0,
    delivered: orders?.content.filter(o => o.status === 'DELIVERED').length ?? 0,
    revenueToday: orders?.content
      .filter(o => new Date(o.createdAt).toDateString() === new Date().toDateString())
      .filter(o => o.status === 'DELIVERED')
      .reduce((sum, o) => sum + Number(o.total), 0) ?? 0,
  };

  if (isLoading) return <p className="text-gray-600">Đang tải...</p>;

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Tổng quan</h1>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="Tổng đơn" value={stats.total} />
        <StatCard label="Chờ xác nhận" value={stats.pending} className="text-yellow-600" />
        <StatCard label="Đang giao" value={stats.delivering} className="text-purple-600" />
        <StatCard
          label="Doanh thu hôm nay"
          value={new Intl.NumberFormat('vi-VN').format(stats.revenueToday) + ' ₫'}
          className="text-green-600"
        />
      </div>

      <div className="mt-6 bg-white rounded-lg shadow p-4">
        <p className="text-sm text-gray-500">📊 Biểu đồ 7 ngày + Top shipper sẽ ở P9 (Reports)</p>
      </div>
    </div>
  );
}

function StatCard({ label, value, className = '' }: { label: string; value: number | string; className?: string }) {
  return (
    <div className="bg-white rounded-lg shadow p-4">
      <p className="text-sm text-gray-600">{label}</p>
      <p className={`text-2xl font-bold mt-1 ${className}`}>{value}</p>
    </div>
  );
}
