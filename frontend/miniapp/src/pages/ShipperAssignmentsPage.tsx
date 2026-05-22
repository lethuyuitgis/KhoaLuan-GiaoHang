import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
  listMyAssignments, acceptAssignment, rejectAssignment,
  formatVnd, formatRelative, type AssignmentResponse
} from '@shop/shared';
import { api } from '@/lib/api';
import { tg } from '@/lib/telegram';

export function ShipperAssignmentsPage() {
  const qc = useQueryClient();

  const { data: assignments, isLoading } = useQuery({
    queryKey: ['shipper', 'assignments'],
    queryFn: () => listMyAssignments(api),
  });

  const acceptMut = useMutation({
    mutationFn: (id: string) => acceptAssignment(api, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['shipper'] }),
    onError: showError,
  });

  const rejectMut = useMutation({
    mutationFn: (id: string) => rejectAssignment(api, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['shipper'] }),
    onError: showError,
  });

  async function showError(err: any) {
    const msg = err.response?.data?.message ?? 'Có lỗi xảy ra';
    if (tg.isInTelegram()) await tg.showAlert(msg);
    else alert(msg);
  }

  async function confirmAndReject(id: string) {
    const ok = tg.isInTelegram()
      ? await tg.showConfirm('Bạn có chắc muốn từ chối đơn này?')
      : confirm('Từ chối đơn này?');
    if (ok) rejectMut.mutate(id);
  }

  if (isLoading) return <p className="text-tg-hint">Đang tải...</p>;

  const active = assignments ?? [];

  return (
    <div>
      <h1 className="text-2xl font-bold mb-4">Đơn của tôi</h1>

      {active.length === 0 && (
        <div className="text-center py-12">
          <p className="text-tg-hint mb-2">Chưa có đơn nào</p>
          <p className="text-xs text-tg-hint">Bật trạng thái AVAILABLE để nhận đơn từ shop</p>
        </div>
      )}

      <div className="space-y-3">
        {active.map(a => (
          <AssignmentCard
            key={a.id}
            a={a}
            onAccept={() => acceptMut.mutate(a.id)}
            onReject={() => confirmAndReject(a.id)}
          />
        ))}
      </div>
    </div>
  );
}

function AssignmentCard({ a, onAccept, onReject }: {
  a: AssignmentResponse;
  onAccept: () => void;
  onReject: () => void;
}) {
  return (
    <Link to={`/shipper/assignments/${a.id}`} className="block bg-tg-secondaryBg rounded-lg p-3">
      <div className="flex items-start justify-between mb-2">
        <div>
          <p className="font-bold">{a.orderCode}</p>
          <p className="text-xs text-tg-hint">{formatRelative(a.assignedAt)}</p>
        </div>
        <StatusBadge status={a.status} />
      </div>

      <p className="text-sm mb-1">📍 {a.deliveryAddress}</p>
      <p className="text-xs text-tg-hint">
        {a.distanceKm}km — phí ship: {formatVnd(a.deliveryFee)}
      </p>

      {a.status === 'OFFERED' && (
        <div className="flex gap-2 mt-3" onClick={e => e.preventDefault()}>
          <button onClick={onAccept}
            className="flex-1 py-2 bg-green-600 text-white rounded-md text-sm font-medium">
            ✅ Nhận
          </button>
          <button onClick={onReject}
            className="flex-1 py-2 border border-red-500 text-red-500 rounded-md text-sm font-medium">
            ❌ Từ chối
          </button>
        </div>
      )}
    </Link>
  );
}

function StatusBadge({ status }: { status: string }) {
  const labels: Record<string, { label: string; cls: string }> = {
    OFFERED:    { label: 'Mới',         cls: 'bg-yellow-100 text-yellow-800' },
    ACCEPTED:   { label: 'Đã nhận',     cls: 'bg-blue-100 text-blue-800' },
    STARTED:    { label: 'Đang giao',   cls: 'bg-purple-100 text-purple-800' },
    COMPLETED:  { label: 'Đã giao',     cls: 'bg-green-100 text-green-800' },
    REJECTED:   { label: 'Đã từ chối',  cls: 'bg-gray-100 text-gray-800' },
    CANCELLED:  { label: 'Hủy',         cls: 'bg-red-100 text-red-800' },
  };
  const info = labels[status] ?? { label: status, cls: 'bg-gray-100 text-gray-800' };
  return <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${info.cls}`}>{info.label}</span>;
}
