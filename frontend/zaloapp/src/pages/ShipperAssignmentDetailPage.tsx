import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  listMyAssignments, startAssignment, completeAssignment, rateCustomer,
  formatVnd, formatDateTime, type AssignmentResponse
} from '@shop/shared';
import { api } from '@/lib/api';
import { useToast } from '@/components/Toast';

// Nhãn mục dùng chung — chữ hoa, giãn nhẹ, xám mờ (thay cho emoji-làm-tiêu-đề).
const LABEL = 'text-[11px] font-semibold uppercase tracking-[0.08em] text-gray-400';
const CARD = 'bg-white rounded-2xl shadow-[0_2px_10px_rgba(0,0,0,0.04)] border border-black/[0.03]';

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

  function showError(err: any) {
    toast.error(err.response?.data?.message ?? 'Có lỗi xảy ra');
  }

  if (isLoading) {
    return (
      <div className="px-4 pt-4 space-y-3">
        <div className="h-10 bg-white rounded-xl animate-pulse border border-gray-100" />
        <div className="h-28 bg-white rounded-2xl animate-pulse border border-gray-100" />
        <div className="h-32 bg-white rounded-2xl animate-pulse border border-gray-100" />
      </div>
    );
  }

  if (!assignment) {
    return (
      <div className="text-center py-16 px-4">
        <p className="text-5xl mb-3">😕</p>
        <p className="font-semibold text-gray-700 mb-1">Không tìm thấy đơn</p>
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

  const STATUS_LABEL: Record<string, string> = {
    OFFERED: 'Có offer', ACCEPTED: 'Đã nhận', STARTED: 'Đang giao',
    COMPLETED: 'Hoàn tất', CANCELLED: 'Đã huỷ', REJECTED: 'Đã từ chối',
  };
  const STATUS_BADGE: Record<string, string> = {
    OFFERED: 'bg-amber-100 text-amber-700', ACCEPTED: 'bg-blue-100 text-blue-700',
    STARTED: 'bg-purple-100 text-purple-700', COMPLETED: 'bg-green-100 text-green-700',
    CANCELLED: 'bg-red-100 text-red-700', REJECTED: 'bg-red-100 text-red-700',
  };

  // COD chưa thanh toán → shipper phải thu tiền mặt của khách khi giao.
  const mustCollect = assignment.paymentMethod === 'COD' && assignment.paymentStatus !== 'SUCCESS';

  return (
    <div className="px-4 pt-4 pb-40 space-y-3">
      <button onClick={() => navigate(-1)} className="text-sm text-gray-500 active:text-gray-700">
        ← Quay lại
      </button>

      <div>
        <div className="flex items-center justify-between gap-3">
          <h1 className="text-2xl font-bold font-mono tracking-tight truncate">{assignment.orderCode}</h1>
          <span className={`shrink-0 text-[11px] uppercase font-semibold px-2.5 py-1 rounded-full ${STATUS_BADGE[assignment.status] ?? 'bg-gray-100 text-gray-700'}`}>
            {STATUS_LABEL[assignment.status] ?? assignment.status}
          </span>
        </div>
        <p className="text-xs text-gray-400 mt-1">{formatDateTime(assignment.assignedAt)}</p>
      </div>

      {/* Thu tiền — thông tin quan trọng nhất với shipper nên đứng đầu, màu nổi. */}
      {mustCollect ? (
        <section className="bg-gradient-to-br from-amber-400 to-orange-500 rounded-2xl shadow-lg shadow-orange-500/25 p-4 text-white">
          <p className="text-[11px] font-semibold uppercase tracking-[0.08em] text-amber-50/90">Thu tiền mặt của khách</p>
          <p className="text-3xl font-extrabold mt-1 tabular-nums">{formatVnd(assignment.total)}</p>
          <p className="text-xs mt-1 text-amber-50/80">Thanh toán COD — thu đủ khi giao hàng</p>
        </section>
      ) : (
        <section className="bg-gradient-to-br from-emerald-500 to-teal-600 rounded-2xl shadow-lg shadow-emerald-500/25 p-4 text-white">
          <p className="text-[11px] font-semibold uppercase tracking-[0.08em] text-emerald-50/90">Đã thanh toán online</p>
          <p className="text-3xl font-extrabold mt-1 tabular-nums">{formatVnd(assignment.total)}</p>
          <p className="text-xs mt-1 text-emerald-50/80">VNPay — không thu tiền khách</p>
        </section>
      )}

      {/* Món hàng — biết lấy gì ở shop. */}
      <section className={`${CARD} p-4`}>
        <h2 className={LABEL}>Món hàng · {assignment.items.length}</h2>
        <ul className="mt-2 divide-y divide-gray-50">
          {assignment.items.map((it, idx) => (
            <li key={idx} className="flex items-center justify-between gap-3 py-2 text-sm">
              <span className="text-gray-800 min-w-0 break-words">{it.productName}</span>
              <span className="shrink-0 font-semibold text-gray-500 tabular-nums">×{it.quantity}</span>
            </li>
          ))}
        </ul>
        {assignment.note && (
          <p className="mt-2 text-xs bg-amber-50 border border-amber-100 text-amber-800 rounded-xl px-3 py-2 break-words">
            Ghi chú của khách: {assignment.note}
          </p>
        )}
      </section>

      {/* Khách hàng */}
      <section className={`${CARD} p-4`}>
        <h2 className={LABEL}>Khách hàng</h2>
        <div className="mt-2 flex items-center justify-between gap-2">
          <div className="min-w-0">
            <p className="text-sm font-semibold text-gray-900 truncate">{assignment.customerName ?? '(chưa có tên)'}</p>
            {assignment.customerPhone && (
              <p className="text-xs text-gray-500 mt-0.5 tabular-nums">{assignment.customerPhone}</p>
            )}
          </div>
          {assignment.customerPhone && (
            <a
              href={`tel:${assignment.customerPhone}`}
              className="shrink-0 inline-flex items-center gap-1.5 px-4 py-2 rounded-full bg-emerald-50 text-emerald-700 text-sm font-semibold active:scale-95 transition"
            >
              <PhoneIcon /> Gọi
            </a>
          )}
        </div>
      </section>

      {/* Địa chỉ giao */}
      <section className={`${CARD} p-4`}>
        <h2 className={LABEL}>Địa chỉ giao</h2>
        <p className="mt-2 text-sm text-gray-800 leading-relaxed break-words">{assignment.deliveryAddress}</p>
        <div className="mt-3 flex justify-between items-center gap-2">
          <span className="inline-flex items-center gap-1.5 text-xs text-gray-500">
            <PinIcon /> {assignment.distanceKm} km
          </span>
          <a
            href={`https://www.google.com/maps?q=${assignment.deliveryLat},${assignment.deliveryLng}`}
            target="_blank"
            rel="noreferrer"
            className="shrink-0 inline-flex items-center gap-1.5 px-4 py-2 rounded-full bg-blue-50 text-blue-700 text-sm font-semibold active:scale-95 transition"
          >
            <MapIcon /> Chỉ đường
          </a>
        </div>
      </section>

      {/* Thanh toán / thu nhập */}
      <section className={`${CARD} p-4 text-sm`}>
        <div className="flex justify-between items-center gap-3">
          <span className="text-gray-500">Tổng đơn (hàng + ship)</span>
          <span className="shrink-0 font-medium tabular-nums text-gray-900">{formatVnd(assignment.total)}</span>
        </div>
        <div className="flex justify-between items-center gap-3 mt-2">
          <span className="text-gray-500">Phí ship khách trả</span>
          <span className="shrink-0 font-medium tabular-nums text-gray-900">{formatVnd(assignment.deliveryFee)}</span>
        </div>
        {assignment.shipperCommission != null && (
          <div className="flex justify-between items-center gap-3 mt-3 pt-3 border-t border-gray-100">
            <span className="text-gray-700 font-semibold">Thu nhập của bạn</span>
            <span className="shrink-0 font-bold text-emerald-600 text-base tabular-nums">
              +{formatVnd(assignment.shipperCommission)}
            </span>
          </div>
        )}
      </section>

      {assignment.status === 'ACCEPTED' && (
        <Steps
          accent="emerald"
          title="Các bước tiếp theo"
          steps={[
            'Đến shop lấy hàng cho đơn này',
            'Gọi khách trước khi đi nếu cần xác nhận địa chỉ',
            'Nhấn "Bắt đầu giao" để thông báo cho khách',
            'Bấm "Chỉ đường" để Google Maps dẫn đường tới khách',
          ]}
        />
      )}

      {/* Dẫn đường — Zalo Mini App không có Live Location như Telegram, nên mở
          Google Maps directions (turn-by-turn) tới địa chỉ khách. Nổi bật khi đang giao. */}
      {assignment.status === 'STARTED' && (
        <a
          href={`https://www.google.com/maps/dir/?api=1&destination=${assignment.deliveryLat},${assignment.deliveryLng}&travelmode=driving`}
          target="_blank"
          rel="noreferrer"
          className="flex items-center gap-3 bg-gradient-to-br from-blue-500 to-indigo-600 rounded-2xl shadow-lg shadow-blue-500/25 p-4 text-white active:scale-[0.99] transition"
        >
          <span className="shrink-0 w-11 h-11 rounded-xl bg-white/20 flex items-center justify-center">
            <MapIcon />
          </span>
          <span className="min-w-0">
            <span className="block text-[11px] font-semibold uppercase tracking-[0.08em] text-blue-50/90">Đang giao đơn này</span>
            <span className="block text-base font-bold leading-tight">Mở Google Maps chỉ đường</span>
            <span className="block text-xs text-blue-50/80 mt-0.5">Dẫn đường tới khách · {assignment.distanceKm} km</span>
          </span>
        </a>
      )}

      {assignment.status === 'ACCEPTED' && (
        <button
          onClick={() => startMut.mutate()}
          disabled={startMut.isPending}
          className="fixed bottom-20 left-4 right-4 max-w-md mx-auto bg-emerald-600 text-white rounded-2xl py-3.5 px-4 font-semibold shadow-xl shadow-emerald-500/30 active:scale-[0.98] transition disabled:bg-gray-300 disabled:shadow-none"
        >
          {startMut.isPending ? 'Đang xử lý…' : '🚀 Bắt đầu giao'}
        </button>
      )}

      {assignment.status === 'STARTED' && (
        <button
          onClick={() => completeMut.mutate()}
          disabled={completeMut.isPending}
          className="fixed bottom-20 left-4 right-4 max-w-md mx-auto bg-emerald-600 text-white rounded-2xl py-3.5 px-4 font-semibold shadow-xl shadow-emerald-500/30 active:scale-[0.98] transition disabled:bg-gray-300 disabled:shadow-none"
        >
          {completeMut.isPending ? 'Đang xử lý…' : '✅ Đã giao xong'}
        </button>
      )}

      {assignment.status === 'COMPLETED' && (
        <section className={`${CARD} p-4`}>
          <h2 className="text-sm font-semibold text-gray-800">Đánh giá khách hàng</h2>
          <p className="text-xs text-gray-500 mt-1 mb-3 leading-relaxed">
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

// Callout hướng dẫn nhiều bước — thay "khối text đánh số" bằng bước có chấm tròn rõ ràng.
function Steps({ accent, title, steps }: { accent: 'emerald' | 'blue'; title: string; steps: string[] }) {
  const tone = accent === 'emerald'
    ? { box: 'from-emerald-50 to-teal-50 border-emerald-200', dot: 'bg-emerald-500', head: 'text-emerald-900', body: 'text-emerald-900/80' }
    : { box: 'from-blue-50 to-indigo-50 border-blue-200', dot: 'bg-blue-500', head: 'text-blue-900', body: 'text-blue-900/80' };
  return (
    <section className={`bg-gradient-to-br ${tone.box} border rounded-2xl p-4`}>
      <p className={`text-sm font-semibold mb-3 ${tone.head}`}>{title}</p>
      <ol className="space-y-2.5">
        {steps.map((s, i) => (
          <li key={i} className="flex gap-3 items-start text-xs">
            <span className={`shrink-0 mt-0.5 w-5 h-5 rounded-full ${tone.dot} text-white flex items-center justify-center font-bold text-[10px]`}>
              {i + 1}
            </span>
            <span className={`${tone.body} leading-relaxed break-words`}>{s}</span>
          </li>
        ))}
      </ol>
    </section>
  );
}

const PhoneIcon = () => (
  <svg viewBox="0 0 24 24" className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
    <path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2 4.2 2 2 0 0 1 4 2h3a2 2 0 0 1 2 1.7c.1.9.4 1.8.7 2.7a2 2 0 0 1-.5 2.1L8 9.6a16 16 0 0 0 6 6l1.1-1.1a2 2 0 0 1 2.1-.5c.9.3 1.8.6 2.7.7a2 2 0 0 1 1.7 2Z" />
  </svg>
);
const PinIcon = () => (
  <svg viewBox="0 0 24 24" className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z" /><circle cx="12" cy="10" r="3" />
  </svg>
);
const MapIcon = () => (
  <svg viewBox="0 0 24 24" className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
    <path d="m9 4-6 2v14l6-2 6 2 6-2V4l-6 2-6-2Z" /><path d="M9 4v14M15 6v14" />
  </svg>
);
