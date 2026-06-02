import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  listMyAssignments, startAssignment, completeAssignment, rateCustomer,
  formatVnd, formatDateTime, type AssignmentResponse
} from '@shop/shared';
import { api } from '@/lib/api';
import { tg } from '@/lib/telegram';
import { useToast } from '@/components/Toast';

export function ShipperAssignmentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const toast = useToast();

  // Local state for the "rate customer" section (only relevant when COMPLETED).
  const [rateStars, setRateStars] = useState<number>(5);
  const [rateComment, setRateComment] = useState<string>('');
  const [rateSubmitted, setRateSubmitted] = useState<boolean>(false);

  const { data: list, isLoading } = useQuery({
    queryKey: ['shipper', 'assignments'],
    queryFn: () => listMyAssignments(api),
  });

  const assignment: AssignmentResponse | undefined = list?.find(a => a.id === id);

  const startMut = useMutation({
    mutationFn: () => startAssignment(api, id!),
    onSuccess: () => {
      toast.success('Đã bắt đầu giao đơn');
      qc.invalidateQueries({ queryKey: ['shipper'] });
    },
    onError: showError,
  });

  const completeMut = useMutation({
    mutationFn: () => completeAssignment(api, id!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['shipper'] });
      toast.success('Hoàn thành đơn — tuyệt vời!');
      // Stay on the page so shipper can rate the customer before returning.
    },
    onError: showError,
  });

  const rateMut = useMutation({
    mutationFn: () => rateCustomer(api, id!, {
      stars: rateStars,
      comment: rateComment.trim() || null,
    }),
    onSuccess: () => {
      setRateSubmitted(true);
      toast.success('Đã gửi đánh giá khách hàng. Cảm ơn bạn!');
    },
    onError: (err: any) => {
      // ALREADY_RATED: treat as success-y (UI shows submitted state)
      if (err?.response?.data?.code === 'ALREADY_RATED') {
        setRateSubmitted(true);
        toast.info?.('Bạn đã đánh giá đơn này rồi.');
        return;
      }
      showError(err);
    },
  });

  async function showError(err: any) {
    const msg = err.response?.data?.message ?? 'Có lỗi xảy ra';
    if (tg.isInTelegram()) await tg.showAlert(msg);
    else toast.error(msg);
  }

  if (isLoading) {
    return (
      <div className="space-y-3">
        <div className="h-10 bg-white rounded-xl animate-pulse border border-gray-100" />
        <div className="h-28 bg-white rounded-2xl animate-pulse border border-gray-100" />
        <div className="h-32 bg-white rounded-2xl animate-pulse border border-gray-100" />
      </div>
    );
  }

  if (!assignment) {
    return (
      <div className="text-center py-16">
        <p className="text-5xl mb-3">😕</p>
        <p className="font-medium text-gray-700 mb-1">Không tìm thấy đơn</p>
        <p className="text-sm text-gray-500 mb-5">Đơn có thể đã bị huỷ hoặc đã gán shipper khác.</p>
        <Link
          to="/shipper/assignments"
          className="inline-block px-5 py-2.5 rounded-2xl bg-emerald-600 text-white font-semibold text-sm shadow-md shadow-emerald-500/30 active:scale-[0.98] transition"
        >
          Về danh sách
        </Link>
      </div>
    );
  }

  return (
    <div className="pb-24">
      <button onClick={() => navigate(-1)} className="mb-3 text-sm text-gray-500 active:text-gray-700">
        ← Quay lại
      </button>

      <h1 className="text-2xl font-bold">{assignment.orderCode}</h1>
      <p className="text-xs text-gray-500 mt-1 mb-4">{formatDateTime(assignment.assignedAt)}</p>

      <section className="bg-white rounded-2xl shadow-sm border border-gray-100 p-4 mb-3">
        <h2 className="text-sm font-semibold text-gray-500 mb-2">Khách hàng</h2>
        <p className="text-sm font-medium">{assignment.customerName ?? '(chưa có tên)'}</p>
        {assignment.customerPhone && (
          <a
            href={`tel:${assignment.customerPhone}`}
            className="mt-2 inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-emerald-50 text-emerald-700 text-sm font-medium active:scale-95 transition"
          >
            📞 {assignment.customerPhone}
          </a>
        )}
      </section>

      <section className="bg-white rounded-2xl shadow-sm border border-gray-100 p-4 mb-3">
        <h2 className="text-sm font-semibold text-gray-500 mb-2">Địa chỉ giao</h2>
        <p className="text-sm flex items-start gap-2">
          <span>📍</span>
          <span>{assignment.deliveryAddress}</span>
        </p>
        <div className="mt-2 flex justify-between text-xs text-gray-500">
          <span>📏 Khoảng cách: {assignment.distanceKm} km</span>
          <a
            href={`https://www.google.com/maps?q=${assignment.deliveryLat},${assignment.deliveryLng}`}
            target="_blank"
            rel="noreferrer"
            className="text-emerald-600 font-medium active:scale-95"
          >
            Xem bản đồ →
          </a>
        </div>
      </section>

      <section className="bg-white rounded-2xl shadow-sm border border-gray-100 p-4 mb-3 text-sm">
        <div className="flex justify-between items-center">
          <span className="text-gray-500">Phí ship</span>
          <span className="font-bold text-orange-600 text-base">{formatVnd(assignment.deliveryFee)}</span>
        </div>
        <div className="flex justify-between items-center mt-2">
          <span className="text-gray-500">Tổng đơn</span>
          <span className="font-medium">{formatVnd(assignment.total)}</span>
        </div>
      </section>

      {assignment.status === 'STARTED' && (
        <section className="bg-gradient-to-br from-blue-50 to-indigo-50 border border-blue-200 rounded-2xl p-4 mb-3 text-sm text-blue-900">
          <p className="font-semibold mb-2">📍 Chia sẻ vị trí real-time</p>
          <ol className="list-decimal list-inside space-y-1 text-blue-900/90 text-xs">
            <li>Quay lại chat với bot</li>
            <li>Bấm biểu tượng 📎 → "Vị trí" → "Chia sẻ vị trí trực tiếp"</li>
            <li>Chọn thời lượng (15 phút / 1 giờ / 8 giờ)</li>
            <li>Khách sẽ thấy vị trí của bạn trên bản đồ realtime</li>
          </ol>
        </section>
      )}

      {assignment.status === 'ACCEPTED' && (
        <button
          onClick={() => startMut.mutate()}
          disabled={startMut.isPending}
          className="fixed bottom-4 left-4 right-4 max-w-md mx-auto bg-emerald-600 text-white rounded-2xl py-3.5 px-4 font-semibold shadow-xl shadow-emerald-500/30 active:scale-[0.98] transition disabled:bg-gray-300 disabled:shadow-none"
        >
          {startMut.isPending ? 'Đang xử lý…' : '🚀 Bắt đầu giao'}
        </button>
      )}

      {assignment.status === 'STARTED' && (
        <button
          onClick={() => completeMut.mutate()}
          disabled={completeMut.isPending}
          className="fixed bottom-4 left-4 right-4 max-w-md mx-auto bg-emerald-600 text-white rounded-2xl py-3.5 px-4 font-semibold shadow-xl shadow-emerald-500/30 active:scale-[0.98] transition disabled:bg-gray-300 disabled:shadow-none"
        >
          {completeMut.isPending ? 'Đang xử lý…' : '✅ Đã giao xong'}
        </button>
      )}

      {assignment.status === 'COMPLETED' && (
        <section className="bg-white rounded-2xl shadow-sm border border-gray-100 p-4 mb-3">
          <h2 className="text-sm font-semibold text-gray-700 mb-1">Đánh giá khách hàng</h2>
          <p className="text-xs text-gray-500 mb-3">
            Thái độ, dễ tìm địa chỉ, có ở nhà — đánh giá riêng cho shop. Khách không thấy được.
          </p>

          {rateSubmitted ? (
            <p className="text-sm text-emerald-700 font-medium flex items-center gap-2">
              <span>✓</span> Bạn đã gửi đánh giá cho đơn này.
            </p>
          ) : (
            <>
              <div className="flex items-center gap-1 mb-3" role="radiogroup" aria-label="Chọn số sao">
                {[1, 2, 3, 4, 5].map(s => (
                  <button
                    key={s}
                    type="button"
                    role="radio"
                    aria-checked={rateStars === s}
                    onClick={() => setRateStars(s)}
                    className={`text-3xl leading-none active:scale-90 transition ${
                      s <= rateStars ? 'text-amber-400' : 'text-gray-300'
                    }`}
                  >
                    ★
                  </button>
                ))}
                <span className="ml-2 text-sm text-gray-600">{rateStars}/5</span>
              </div>

              <textarea
                value={rateComment}
                onChange={e => setRateComment(e.target.value)}
                maxLength={1000}
                rows={3}
                placeholder="Nhận xét (tuỳ chọn)…"
                className="w-full rounded-xl border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/40 resize-none"
              />

              <button
                onClick={() => rateMut.mutate()}
                disabled={rateMut.isPending}
                className="mt-3 w-full bg-emerald-600 text-white rounded-2xl py-3 px-4 font-semibold shadow-md shadow-emerald-500/30 active:scale-[0.98] transition disabled:bg-gray-300 disabled:shadow-none"
              >
                {rateMut.isPending ? 'Đang gửi…' : 'Gửi đánh giá'}
              </button>
            </>
          )}

          <button
            onClick={() => navigate('/shipper/assignments', { replace: true })}
            className="mt-3 w-full text-sm text-gray-500 active:text-gray-700 py-2"
          >
            ← Về danh sách
          </button>
        </section>
      )}
    </div>
  );
}
