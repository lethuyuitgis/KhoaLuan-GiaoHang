import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  listMyAssignments, startAssignment, completeAssignment,
  formatVnd, formatDateTime, type AssignmentResponse
} from '@shop/shared';
import { api } from '@/lib/api';
import { tg } from '@/lib/telegram';

export function ShipperAssignmentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();

  const { data: list } = useQuery({
    queryKey: ['shipper', 'assignments'],
    queryFn: () => listMyAssignments(api),
  });

  const assignment: AssignmentResponse | undefined = list?.find(a => a.id === id);

  const startMut = useMutation({
    mutationFn: () => startAssignment(api, id!),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['shipper'] }),
    onError: showError,
  });

  const completeMut = useMutation({
    mutationFn: () => completeAssignment(api, id!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['shipper'] });
      navigate('/shipper/assignments', { replace: true });
    },
    onError: showError,
  });

  async function showError(err: any) {
    const msg = err.response?.data?.message ?? 'Có lỗi xảy ra';
    if (tg.isInTelegram()) await tg.showAlert(msg);
    else alert(msg);
  }

  if (!assignment) {
    return (
      <div>
        <Link to="/shipper/assignments" className="text-tg-link">← Về danh sách</Link>
        <p className="mt-4 text-tg-hint">Không tìm thấy đơn.</p>
      </div>
    );
  }

  return (
    <div>
      <button onClick={() => navigate(-1)} className="mb-4 text-tg-link">← Quay lại</button>

      <h1 className="text-2xl font-bold mb-1">{assignment.orderCode}</h1>
      <p className="text-xs text-tg-hint mb-4">{formatDateTime(assignment.assignedAt)}</p>

      <div className="bg-tg-secondaryBg rounded-lg p-4 mb-4">
        <h2 className="font-semibold mb-2">Khách hàng</h2>
        <p className="text-sm">{assignment.customerName ?? '(chưa có tên)'}</p>
        {assignment.customerPhone && (
          <a href={`tel:${assignment.customerPhone}`} className="text-sm text-tg-link block mt-1">
            📞 {assignment.customerPhone}
          </a>
        )}
      </div>

      <div className="bg-tg-secondaryBg rounded-lg p-4 mb-4">
        <h2 className="font-semibold mb-2">Địa chỉ giao</h2>
        <p className="text-sm">{assignment.deliveryAddress}</p>
        <p className="text-xs text-tg-hint mt-2">
          Khoảng cách: {assignment.distanceKm}km
        </p>
        <p className="text-xs text-tg-hint">
          📍 {assignment.deliveryLat}, {assignment.deliveryLng} (P6 sẽ có map)
        </p>
      </div>

      <div className="bg-tg-secondaryBg rounded-lg p-4 mb-4 text-sm">
        <div className="flex justify-between">
          <span className="text-tg-hint">Phí ship</span>
          <span className="font-bold">{formatVnd(assignment.deliveryFee)}</span>
        </div>
        <div className="flex justify-between mt-1">
          <span className="text-tg-hint">Tổng đơn</span>
          <span>{formatVnd(assignment.total)}</span>
        </div>
      </div>

      {assignment.status === 'STARTED' && (
        <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 mb-4 text-sm">
          <p className="font-semibold mb-2">📍 Hãy chia sẻ vị trí real-time</p>
          <ol className="list-decimal list-inside space-y-1 text-blue-900">
            <li>Quay lại chat với bot</li>
            <li>Bấm biểu tượng 📎 → "Vị trí" → "Chia sẻ vị trí trực tiếp"</li>
            <li>Chọn thời lượng (15 phút / 1 giờ / 8 giờ)</li>
            <li>Khách sẽ thấy vị trí của bạn trên map realtime</li>
          </ol>
        </div>
      )}

      {assignment.status === 'ACCEPTED' && (
        <button onClick={() => startMut.mutate()} disabled={startMut.isPending}
          className="w-full py-3 bg-tg-button text-tg-buttonText rounded-lg font-medium disabled:opacity-50">
          {startMut.isPending ? 'Đang xử lý...' : '🚀 Bắt đầu giao'}
        </button>
      )}

      {assignment.status === 'STARTED' && (
        <button onClick={() => completeMut.mutate()} disabled={completeMut.isPending}
          className="w-full py-3 bg-green-600 text-white rounded-lg font-medium disabled:opacity-50">
          {completeMut.isPending ? 'Đang xử lý...' : '✅ Đã giao'}
        </button>
      )}
    </div>
  );
}
