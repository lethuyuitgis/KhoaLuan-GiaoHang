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

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Shipper</h1>
        <button
          onClick={() => setShowForm(s => !s)}
          className="px-4 py-2 bg-brand-600 text-white rounded-md hover:bg-brand-700"
        >
          {showForm ? 'Đóng' : '+ Thêm shipper'}
        </button>
      </div>

      {showForm && (
        <form onSubmit={onSubmit} className="bg-white rounded-lg shadow p-4 mb-6 max-w-md">
          <h2 className="font-semibold mb-3">Thêm shipper</h2>
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-md p-3 mb-3">
              {error}
            </div>
          )}
          <label className="block mb-3">
            <span className="text-sm text-gray-700">Telegram User ID *</span>
            <input type="number" required value={telegramUserId} onChange={e => setTelegramUserId(e.target.value)}
              placeholder="vd: 1951735745"
              className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md" />
            <p className="text-xs text-gray-500 mt-1">User phải /start bot trước để có entry trong telegram_user</p>
          </label>
          <label className="block mb-3">
            <span className="text-sm text-gray-700">Loại xe *</span>
            <select value={vehicleType} onChange={e => setVehicleType(e.target.value as VehicleType)}
              className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md">
              <option value="MOTORBIKE">Xe máy</option>
              <option value="CAR">Ô tô</option>
              <option value="BICYCLE">Xe đạp</option>
            </select>
          </label>
          <label className="block mb-3">
            <span className="text-sm text-gray-700">Biển số</span>
            <input type="text" value={licensePlate} onChange={e => setLicensePlate(e.target.value)}
              placeholder="29A-12345"
              className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md" />
          </label>
          <button type="submit" disabled={createMut.isPending}
            className="w-full py-2 bg-brand-600 text-white rounded-md hover:bg-brand-700 disabled:opacity-50">
            {createMut.isPending ? 'Đang tạo...' : 'Tạo'}
          </button>
        </form>
      )}

      {isLoading && <p className="text-gray-600">Đang tải...</p>}

      <div className="bg-white rounded-lg shadow overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-600">
            <tr>
              <th className="px-4 py-2 text-left">Tên</th>
              <th className="px-4 py-2 text-left">Telegram ID</th>
              <th className="px-4 py-2 text-left">Xe</th>
              <th className="px-4 py-2 text-left">Biển số</th>
              <th className="px-4 py-2 text-left">Trạng thái</th>
              <th className="px-4 py-2 text-right">Đơn đã giao</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {shippers?.length === 0 && (
              <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-500">Chưa có shipper</td></tr>
            )}
            {shippers?.map(s => (
              <tr key={s.userId} className="hover:bg-gray-50">
                <td className="px-4 py-3">{[s.firstName, s.lastName].filter(Boolean).join(' ') || '-'}</td>
                <td className="px-4 py-3 text-gray-600">{s.userId}</td>
                <td className="px-4 py-3">{s.vehicleType}</td>
                <td className="px-4 py-3">{s.licensePlate ?? '-'}</td>
                <td className="px-4 py-3">
                  <span className={`px-2 py-0.5 rounded-full text-xs ${
                    s.currentState === 'AVAILABLE' ? 'bg-green-100 text-green-800' :
                    s.currentState === 'BUSY' ? 'bg-yellow-100 text-yellow-800' :
                    'bg-gray-200 text-gray-700'
                  }`}>
                    {s.currentState}
                  </span>
                </td>
                <td className="px-4 py-3 text-right">{s.totalDeliveries}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
