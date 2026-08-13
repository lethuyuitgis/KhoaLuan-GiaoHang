import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import {
  listMyAssignments, acceptAssignment, rejectAssignment,
  formatVnd, formatRelative, type AssignmentResponse
} from '@shop/shared';
import { api } from '@/lib/api';
import { tg } from '@/lib/telegram';
import { useToast } from '@/components/Toast';

const STATUS_LABEL: Record<string, string> = {
  OFFERED:   'Có offer',
  ACCEPTED:  'Đã nhận',
  STARTED:   'Đang giao',
  COMPLETED: 'Hoàn tất',
  CANCELLED: 'Đã huỷ',
  REJECTED:  'Đã từ chối',
};

const STATUS_BADGE: Record<string, string> = {
  OFFERED:   'bg-amber-100 text-amber-700',
  ACCEPTED:  'bg-blue-100 text-blue-700',
  STARTED:   'bg-purple-100 text-purple-700',
  COMPLETED: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-red-100 text-red-700',
  REJECTED:  'bg-red-100 text-red-700',
};

const CARD = 'bg-white rounded-2xl shadow-[0_2px_10px_rgba(0,0,0,0.04)] border border-black/[0.03]';

export function ShipperAssignmentsPage() {
  const nav = useNavigate();
  const qc = useQueryClient();
  const toast = useToast();

  const { data: assignments, isLoading, error } = useQuery({
    queryKey: ['shipper', 'assignments'],
    queryFn: () => listMyAssignments(api),
  });

  const acceptMut = useMutation({
    mutationFn: (id: string) => acceptAssignment(api, id),
    onSuccess: () => {
      toast.success('Đã nhận đơn');
      qc.invalidateQueries({ queryKey: ['shipper'] });
    },
    onError: showError,
  });

  const rejectMut = useMutation({
    mutationFn: (id: string) => rejectAssignment(api, id),
    onSuccess: () => {
      toast.info('Đã từ chối đơn');
      qc.invalidateQueries({ queryKey: ['shipper'] });
    },
    onError: showError,
  });

  async function showError(err: any) {
    const msg = err.response?.data?.message ?? 'Có lỗi xảy ra';
    if (tg.isInTelegram()) await tg.showAlert(msg);
    else toast.error(msg);
  }

  async function confirmAndReject(id: string) {
    const ok = tg.isInTelegram()
      ? await tg.showConfirm('Bạn có chắc muốn từ chối đơn này?')
      : confirm('Từ chối đơn này?');
    if (ok) rejectMut.mutate(id);
  }

  const list = assignments ?? [];
  const offered = list.filter(a => a.status === 'OFFERED');
  const active = list.filter(a => ['ACCEPTED', 'STARTED'].includes(a.status));
  const past = list.filter(a => ['COMPLETED', 'REJECTED', 'CANCELLED'].includes(a.status));

  return (
    <div className="p-4 space-y-5">
      <header>
        <h1 className="text-2xl font-bold text-gray-900">Đơn của tôi</h1>
        <p className="text-xs text-gray-500 mt-0.5">
          {offered.length > 0
            ? `${offered.length} đơn mới đang chờ bạn nhận`
            : active.length > 0
              ? `${active.length} đơn đang giao`
              : 'Chưa có đơn mới'}
        </p>
      </header>

      {isLoading && (
        <div className="space-y-3">
          {[1, 2, 3].map(i => (
            <div key={i} className="h-24 bg-white rounded-2xl animate-pulse" />
          ))}
        </div>
      )}

      {error && (
        <div className="rounded-2xl bg-red-50 border border-red-200 p-4 text-sm text-red-700">
          <p className="font-medium mb-1">Không tải được danh sách đơn.</p>
          <p className="text-xs opacity-80">Vui lòng thử lại sau.</p>
        </div>
      )}

      {!isLoading && !error && list.length === 0 && (
        <div className="text-center py-16">
          <p className="text-5xl mb-3">🛵</p>
          <p className="font-semibold text-gray-700 mb-1">Chưa có đơn nào</p>
          <p className="text-xs text-gray-500">Bật trạng thái AVAILABLE để nhận đơn từ shop</p>
        </div>
      )}

      {offered.length > 0 && (
        <Section title="Đơn mới" dot="bg-amber-500" count={offered.length}>
          {offered.map(a => (
            <AssignmentCard
              key={a.id}
              a={a}
              onNav={() => nav(`/shipper/assignments/${a.id}`)}
              onAccept={() => acceptMut.mutate(a.id)}
              onReject={() => confirmAndReject(a.id)}
            />
          ))}
        </Section>
      )}
      {active.length > 0 && (
        <Section title="Đang giao" dot="bg-purple-500" count={active.length}>
          {active.map(a => (
            <AssignmentCard key={a.id} a={a} onNav={() => nav(`/shipper/assignments/${a.id}`)} onAccept={() => {}} onReject={() => {}} />
          ))}
        </Section>
      )}
      {past.length > 0 && (
        <Section title="Lịch sử" dot="bg-gray-300" count={past.length}>
          {past.map(a => (
            <AssignmentCard key={a.id} a={a} onNav={() => nav(`/shipper/assignments/${a.id}`)} onAccept={() => {}} onReject={() => {}} />
          ))}
        </Section>
      )}
    </div>
  );
}

function Section({ title, dot, count, children }: { title: string; dot: string; count: number; children: React.ReactNode }) {
  return (
    <div>
      <h2 className="flex items-center gap-2 mb-2 px-1 text-[11px] font-semibold uppercase tracking-[0.08em] text-gray-400">
        <span className={`w-1.5 h-1.5 rounded-full ${dot}`} />
        {title}
        <span className="text-gray-300 font-medium normal-case tracking-normal">({count})</span>
      </h2>
      <div className="space-y-2.5">{children}</div>
    </div>
  );
}

function AssignmentCard({ a, onNav, onAccept, onReject }: {
  a: AssignmentResponse;
  onNav: () => void;
  onAccept: () => void;
  onReject: () => void;
}) {
  const isDone = ['COMPLETED', 'CANCELLED', 'REJECTED'].includes(a.status);
  // Hoa hồng backend tính sẵn nếu có; chưa có thì ước lượng 80% phí ship.
  const expected = a.shipperCommission ?? Math.round(Number(a.deliveryFee ?? 0) * 0.8);
  const mustCollect = a.paymentMethod === 'COD' && a.paymentStatus !== 'SUCCESS';
  const itemsSummary = (a.items ?? [])
    .map(it => `${it.productName} ×${it.quantity}`)
    .join(', ');

  return (
    <article
      className={`${CARD} p-3.5 space-y-2 active:scale-[0.98] transition cursor-pointer`}
      onClick={onNav}
    >
      <header className="flex justify-between items-center gap-2">
        <span className={`text-[11px] uppercase font-semibold px-2 py-0.5 rounded-full ${STATUS_BADGE[a.status] ?? 'bg-gray-100 text-gray-700'}`}>
          {STATUS_LABEL[a.status] ?? a.status}
        </span>
        {!isDone && (
          <span className="shrink-0 text-sm font-bold text-[var(--brand-primary)] tabular-nums">
            +{formatVnd(expected)} <span className="text-[11px] font-medium text-gray-400">dự kiến</span>
          </span>
        )}
      </header>

      <p className="text-sm font-mono font-semibold text-gray-900 truncate">{a.orderCode}</p>

      <p className="text-xs text-gray-600 line-clamp-1 flex items-start gap-1.5">
        <PinIcon /> <span className="min-w-0 break-words">{a.deliveryAddress}</span>
      </p>
      {itemsSummary && (
        <p className="text-xs text-gray-400 line-clamp-1">{itemsSummary}</p>
      )}

      <div className="flex items-center justify-between gap-2 pt-0.5">
        <p className="text-[11px] text-gray-400">
          {formatRelative(a.assignedAt)} · {Number(a.distanceKm ?? 0).toFixed(1)} km
        </p>
        {!isDone && (
          mustCollect ? (
            <span className="shrink-0 text-[11px] font-bold px-2 py-0.5 rounded-full bg-amber-100 text-amber-700 tabular-nums">
              Thu {formatVnd(a.total)}
            </span>
          ) : (
            <span className="shrink-0 text-[11px] font-semibold px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-700">
              Đã trả online
            </span>
          )
        )}
      </div>

      {a.status === 'OFFERED' && (
        <div className="flex gap-2 pt-1" onClick={e => e.stopPropagation()}>
          <button
            onClick={onAccept}
            className="flex-1 py-2.5 bg-emerald-600 text-white rounded-xl text-sm font-semibold shadow-sm shadow-emerald-500/25 active:scale-[0.97] transition"
          >
            Nhận đơn
          </button>
          <button
            onClick={onReject}
            className="flex-1 py-2.5 border border-red-200 text-red-600 rounded-xl text-sm font-semibold active:scale-[0.97] transition"
          >
            Từ chối
          </button>
        </div>
      )}
    </article>
  );
}

const PinIcon = () => (
  <svg viewBox="0 0 24 24" className="w-3.5 h-3.5 shrink-0 mt-0.5 text-gray-400" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z" /><circle cx="12" cy="10" r="3" />
  </svg>
);
