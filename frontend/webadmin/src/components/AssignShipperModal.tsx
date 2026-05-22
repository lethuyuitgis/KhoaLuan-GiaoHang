import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { listShippers, assignShipper } from '@shop/shared';
import { api } from '@/lib/api';

interface Props {
  orderId: string;
  orderCode: string;
  onClose: () => void;
}

export function AssignShipperModal({ orderId, orderCode, onClose }: Props) {
  const qc = useQueryClient();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const { data: shippers, isLoading } = useQuery({
    queryKey: ['admin', 'shippers'],
    queryFn: () => listShippers(api),
  });

  const available = shippers?.filter(s => s.currentState === 'AVAILABLE') ?? [];

  const assignMut = useMutation({
    mutationFn: (shipperId: number) => assignShipper(api, orderId, shipperId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'order', orderId] });
      qc.invalidateQueries({ queryKey: ['admin', 'orders', 'list'] });
      qc.invalidateQueries({ queryKey: ['admin', 'shippers'] });
      onClose();
    },
    onError: (err: any) => {
      setError(err.response?.data?.message ?? 'Gán shipper thất bại');
    },
  });

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
        <h2 className="text-xl font-bold mb-1">Gán shipper</h2>
        <p className="text-sm text-gray-600 mb-4">Đơn {orderCode}</p>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-md p-3 mb-4">
            {error}
          </div>
        )}

        {isLoading && <p className="text-gray-600">Đang tải...</p>}

        {!isLoading && available.length === 0 && (
          <div className="bg-yellow-50 border border-yellow-200 text-yellow-800 text-sm rounded-md p-3 mb-4">
            Không có shipper nào đang AVAILABLE. Yêu cầu shipper bật trạng thái sẵn sàng.
          </div>
        )}

        <div className="space-y-2 mb-4 max-h-80 overflow-y-auto">
          {available.map(s => (
            <label
              key={s.userId}
              className={`block p-3 rounded-md border cursor-pointer ${
                selectedId === s.userId
                  ? 'border-brand-600 bg-brand-50'
                  : 'border-gray-200 hover:border-gray-300'
              }`}
            >
              <input
                type="radio"
                name="shipper"
                value={s.userId}
                checked={selectedId === s.userId}
                onChange={() => setSelectedId(s.userId)}
                className="hidden"
              />
              <div className="flex items-center justify-between">
                <div>
                  <p className="font-medium">{[s.firstName, s.lastName].filter(Boolean).join(' ') || `Shipper #${s.userId}`}</p>
                  <p className="text-xs text-gray-500">{s.vehicleType} {s.licensePlate ?? ''}</p>
                </div>
                <div className="text-right text-xs text-gray-500">
                  {s.totalDeliveries} đơn
                </div>
              </div>
            </label>
          ))}
        </div>

        <div className="flex gap-2">
          <button
            onClick={() => selectedId !== null && assignMut.mutate(selectedId)}
            disabled={selectedId === null || assignMut.isPending}
            className="flex-1 py-2 bg-brand-600 text-white rounded-md hover:bg-brand-700 disabled:opacity-50"
          >
            {assignMut.isPending ? 'Đang gán...' : 'Gán shipper'}
          </button>
          <button onClick={onClose} className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50">
            Hủy
          </button>
        </div>
      </div>
    </div>
  );
}
