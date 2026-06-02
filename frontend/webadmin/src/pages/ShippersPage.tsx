import { useState, type FormEvent } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { listShippers, createShipper, type CreateShipperRequest, type VehicleType } from '@shop/shared';
import { api } from '@/lib/api';

export function ShippersPage() {
  const qc = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [telegramUserId, setTelegramUserId] = useState('');
  const [vehicleType, setVehicleType] = useState<VehicleType>('MOTORBIKE');
  const [licensePlate, setLicensePlate] = useState('');
  const [error, setError] = useState<string | null>(null);

  const { data: shippers, isLoading } = useQuery({
    queryKey: ['admin', 'shippers'],
    queryFn: () => listShippers(api),
  });

  const createMut = useMutation({
    mutationFn: (req: CreateShipperRequest) => createShipper(api, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'shippers'] });
      setShowForm(false);
      setTelegramUserId('');
      setVehicleType('MOTORBIKE');
      setLicensePlate('');
      setError(null);
    },
    onError: (err: any) => {
      setError(err.response?.data?.message ?? 'Tạo shipper thất bại');
    },
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    createMut.mutate({
      telegramUserId: Number(telegramUserId),
      vehicleType,
      licensePlate: licensePlate || undefined,
    });
  };

  const VEHICLE_ICON: Record<string, string> = { MOTORBIKE: '🏍️', CAR: '🚗', BICYCLE: '🚲' };
  const VEHICLE_VN: Record<string, string> = { MOTORBIKE: 'Xe máy', CAR: 'Ô tô', BICYCLE: 'Xe đạp' };
  const STATE_LABEL: Record<string, { label: string; bg: string; fg: string; dot: string }> = {
    AVAILABLE: { label: 'Sẵn sàng', bg: 'bg-green-50',  fg: 'text-green-700',  dot: 'bg-green-500' },
    BUSY:      { label: 'Đang bận', bg: 'bg-amber-50',  fg: 'text-amber-700',  dot: 'bg-amber-500' },
    OFFLINE:   { label: 'Ngoại tuyến', bg: 'bg-gray-100', fg: 'text-gray-600', dot: 'bg-gray-400' },
  };

  return (
    <div>
      <div className="mb-6 flex items-end justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Shipper</h1>
          <p className="text-sm text-gray-500 mt-1">{shippers?.length ?? 0} shipper trong hệ thống</p>
        </div>
        <button
          onClick={() => setShowForm(s => !s)}
          className="px-4 py-2 bg-orange-500 hover:bg-orange-600 text-white text-sm font-medium rounded-lg shadow-sm transition-colors flex items-center gap-1.5"
        >
          {showForm ? (
            <>
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2"><path strokeLinecap="round" d="M6 18L18 6M6 6l12 12"/></svg>
              Đóng
            </>
          ) : (
            <>
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2"><path strokeLinecap="round" d="M12 4v16m8-8H4"/></svg>
              Thêm shipper
            </>
          )}
        </button>
      </div>

      {showForm && (
        <form onSubmit={onSubmit} className="bg-white rounded-xl border border-gray-100 shadow-sm p-5 mb-6 max-w-md">
          <h2 className="font-semibold text-gray-900 mb-4">Thêm shipper mới</h2>
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg p-3 mb-4">
              {error}
            </div>
          )}
          <label className="block mb-4">
            <span className="text-sm font-medium text-gray-700">Telegram User ID *</span>
            <input type="number" required value={telegramUserId} onChange={e => setTelegramUserId(e.target.value)}
              placeholder="vd: 1951735745"
              className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-orange-500 outline-none" />
            <p className="text-xs text-gray-500 mt-1">User phải /start bot trước để có entry trong telegram_user</p>
          </label>
          <label className="block mb-4">
            <span className="text-sm font-medium text-gray-700">Loại xe *</span>
            <select value={vehicleType} onChange={e => setVehicleType(e.target.value as VehicleType)}
              className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-orange-500 outline-none">
              <option value="MOTORBIKE">🏍️ Xe máy</option>
              <option value="CAR">🚗 Ô tô</option>
              <option value="BICYCLE">🚲 Xe đạp</option>
            </select>
          </label>
          <label className="block mb-4">
            <span className="text-sm font-medium text-gray-700">Biển số</span>
            <input type="text" value={licensePlate} onChange={e => setLicensePlate(e.target.value)}
              placeholder="29A-12345"
              className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-orange-500 outline-none" />
          </label>
          <button type="submit" disabled={createMut.isPending}
            className="w-full py-2.5 bg-orange-500 hover:bg-orange-600 text-white font-medium rounded-lg disabled:opacity-50 transition-colors">
            {createMut.isPending ? 'Đang tạo...' : 'Tạo shipper'}
          </button>
        </form>
      )}

      <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wider">
              <th className="px-4 py-3 text-left font-semibold">Shipper</th>
              <th className="px-4 py-3 text-left font-semibold">Telegram ID</th>
              <th className="px-4 py-3 text-left font-semibold">Phương tiện</th>
              <th className="px-4 py-3 text-left font-semibold">Trạng thái</th>
              <th className="px-4 py-3 text-center font-semibold">Đánh giá</th>
              <th className="px-4 py-3 text-right font-semibold">Đơn đã giao</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && Array.from({ length: 3 }).map((_, i) => (
              <tr key={i}><td colSpan={6} className="px-4 py-4"><div className="h-4 bg-gray-100 rounded animate-pulse" /></td></tr>
            ))}
            {!isLoading && shippers?.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-12 text-center text-gray-400">
                  <p className="text-3xl mb-1">🚴</p>
                  <p className="text-sm">Chưa có shipper nào — bấm "Thêm shipper" để bắt đầu</p>
                </td>
              </tr>
            )}
            {shippers?.map(s => {
              const fullName = [s.firstName, s.lastName].filter(Boolean).join(' ') || `User ${s.userId}`;
              const initial = (s.firstName ?? '?').charAt(0).toUpperCase();
              const state = STATE_LABEL[s.currentState] ?? STATE_LABEL.OFFLINE;
              const ratingShown = s.ratingCount > 0;
              return (
                <tr key={s.userId} className="hover:bg-orange-50/40 transition-colors">
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-3">
                      <div className="w-9 h-9 rounded-full bg-gradient-to-br from-indigo-400 to-purple-600 flex items-center justify-center text-white font-semibold flex-shrink-0">
                        {initial}
                      </div>
                      <div>
                        <p className="font-medium text-gray-900">{fullName}</p>
                        {s.username && <p className="text-xs text-gray-500">@{s.username}</p>}
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-gray-500">{s.userId}</td>
                  <td className="px-4 py-3 text-gray-700">
                    <span className="inline-flex items-center gap-1.5">
                      <span>{VEHICLE_ICON[s.vehicleType] ?? '🛵'}</span>
                      <span>{VEHICLE_VN[s.vehicleType] ?? s.vehicleType}</span>
                      {s.licensePlate && <span className="text-xs text-gray-400 ml-1">· {s.licensePlate}</span>}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium ${state.bg} ${state.fg}`}>
                      <span className={`w-1.5 h-1.5 rounded-full ${state.dot}`}></span>
                      {state.label}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-center">
                    {ratingShown ? (
                      <span className="inline-flex items-center gap-1 text-sm">
                        <span className="text-amber-500">★</span>
                        <span className="font-medium text-gray-900">{Number(s.ratingAvg).toFixed(2)}</span>
                        <span className="text-xs text-gray-400">({s.ratingCount})</span>
                      </span>
                    ) : (
                      <span className="text-xs text-gray-400">Chưa có</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right font-semibold text-gray-900">{s.totalDeliveries}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
