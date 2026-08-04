import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getCandidateShippers, assignShipper, type ShipperCandidateResponse } from '@shop/shared';
import { api } from '@/lib/api';

interface Props {
  orderId: string;
  orderCode: string;
  onClose: () => void;
}

type SortMode = 'distance' | 'rating';

const VEHICLE_VN: Record<string, string> = { MOTORBIKE: 'Xe máy', CAR: 'Ô tô', BICYCLE: 'Xe đạp' };

/** "15 phút trước", "2 giờ trước", "3 ngày trước" — locale-free relative time. */
function relativeTime(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const mins = Math.max(0, Math.round(diffMs / 60000));
  if (mins < 60) return `${mins} phút trước`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `${hours} giờ trước`;
  return `${Math.round(hours / 24)} ngày trước`;
}

function distanceLabel(c: ShipperCandidateResponse): string {
  if (c.distanceKm == null) return 'Chưa rõ vị trí';
  const km = c.distanceKm < 10 ? c.distanceKm.toFixed(1) : Math.round(c.distanceKm).toString();
  const when = c.lastLocationAt ? ` · ${relativeTime(c.lastLocationAt)}` : '';
  return `~${km} km${when}`;
}

function sortCandidates(list: ShipperCandidateResponse[], mode: SortMode): ShipperCandidateResponse[] {
  const copy = [...list];
  if (mode === 'rating') {
    return copy.sort((a, b) => b.ratingAvg - a.ratingAvg);
  }
  // distance: nearest first, unknown-distance last
  return copy.sort((a, b) => {
    if (a.distanceKm == null) return b.distanceKm == null ? 0 : 1;
    if (b.distanceKm == null) return -1;
    return a.distanceKm - b.distanceKm;
  });
}

export function AssignShipperModal({ orderId, orderCode, onClose }: Props) {
  const qc = useQueryClient();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [sortMode, setSortMode] = useState<SortMode>('distance');
  const [error, setError] = useState<string | null>(null);

  const { data: candidates, isLoading, isError } = useQuery({
    queryKey: ['admin', 'order', orderId, 'candidates'],
    queryFn: () => getCandidateShippers(api, orderId),
  });

  const sorted = sortCandidates(candidates ?? [], sortMode);

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

        {isError && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-md p-3 mb-4">
            Không tải được danh sách shipper. Vui lòng thử lại.
          </div>
        )}

        {!isLoading && !isError && sorted.length === 0 && (
          <div className="bg-yellow-50 border border-yellow-200 text-yellow-800 text-sm rounded-md p-3 mb-4">
            Không có shipper nào đang sẵn sàng. Yêu cầu shipper bật trạng thái sẵn sàng.
          </div>
        )}

        {!isLoading && sorted.length > 0 && (
          <div className="flex items-center gap-2 mb-3 text-sm">
            <span className="text-gray-500">Sắp xếp:</span>
            <button
              onClick={() => setSortMode('distance')}
              className={`px-2.5 py-1 rounded-full text-xs font-medium ${sortMode === 'distance' ? 'bg-brand-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}
            >
              Gần nhất
            </button>
            <button
              onClick={() => setSortMode('rating')}
              className={`px-2.5 py-1 rounded-full text-xs font-medium ${sortMode === 'rating' ? 'bg-brand-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}
            >
              Đánh giá cao
            </button>
          </div>
        )}

        <div className="space-y-2 mb-4 max-h-80 overflow-y-auto">
          {sorted.map(s => (
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
              <div className="flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="font-medium">{[s.firstName, s.lastName].filter(Boolean).join(' ') || `Shipper #${s.userId}`}</p>
                  <p className="text-xs text-gray-500">
                    {VEHICLE_VN[s.vehicleType] ?? s.vehicleType} {s.licensePlate ?? ''} · {s.totalDeliveries} đơn
                  </p>
                </div>
                <div className="text-right flex-shrink-0">
                  <p className="text-xs">
                    {s.ratingCount > 0
                      ? <span className="text-amber-600 font-medium">★ {s.ratingAvg.toFixed(1)} <span className="text-gray-400 font-normal">({s.ratingCount})</span></span>
                      : <span className="text-gray-400">Chưa có đánh giá</span>}
                  </p>
                  <p className={`text-xs mt-0.5 ${s.distanceKm == null ? 'text-gray-400 italic' : 'text-gray-600'}`}>
                    {distanceLabel(s)}
                  </p>
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
