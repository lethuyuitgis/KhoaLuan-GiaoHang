import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
  listMyAssignments, acceptAssignment, rejectAssignment,
  formatVnd, formatRelative, type AssignmentResponse
} from '@shop/shared';
import { api } from '@/lib/api';
import { tg } from '@/lib/telegram';
import { useToast } from '@/components/Toast';

export function ShipperAssignmentsPage() {
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
    <div className="-mx-4 -mt-4">
      {/* Hero strip — shipper variant: green/teal */}
      <div className="bg-gradient-to-br from-emerald-500 to-teal-600 px-4 pt-5 pb-6 text-white">
        <p className="text-xs opacity-90">🛵 Shipper</p>
        <h1 className="text-2xl font-bold mt-0.5">Đơn của tôi</h1>
        <p className="text-xs opacity-90 mt-1">
          {offered.length > 0
            ? `${offered.length} đơn mới đang chờ bạn nhận`
            : active.length > 0
              ? `${active.length} đơn đang giao`
              : 'Chưa có đơn mới — bật trạng thái AVAILABLE để nhận'}
        </p>
      </div>

      <div className="px-4 pt-5 pb-6">
        {isLoading && (
          <div className="space-y-3">
            {[1, 2, 3].map(i => (
              <div key={i} className="h-32 bg-white rounded-2xl animate-pulse border border-gray-100" />
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
          <div className="text-center py-12">
            <p className="text-5xl mb-3">🛵</p>
            <p className="font-medium text-gray-700 mb-1">Chưa có đơn nào</p>
            <p className="text-xs text-gray-500">Bật trạng thái AVAILABLE để nhận đơn từ shop</p>
          </div>
        )}

        {offered.length > 0 && (
          <Section title="🔥 Đơn mới">
            {offered.map(a => (
              <AssignmentCard
                key={a.id}
                a={a}
                onAccept={() => acceptMut.mutate(a.id)}
                onReject={() => confirmAndReject(a.id)}
              />
            ))}
          </Section>
        )}
        {active.length > 0 && (
          <Section title="🚀 Đang giao">
            {active.map(a => (
              <AssignmentCard key={a.id} a={a} onAccept={() => {}} onReject={() => {}} />
            ))}
          </Section>
        )}
        {past.length > 0 && (
          <Section title="📜 Lịch sử">
            {past.map(a => (
              <AssignmentCard key={a.id} a={a} onAccept={() => {}} onReject={() => {}} />
            ))}
          </Section>
        )}
      </div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="mb-5">
      <h2 className="text-sm font-semibold text-gray-500 mb-2 px-1">{title}</h2>
      <div className="space-y-3">{children}</div>
    </div>
  );
}

function AssignmentCard({ a, onAccept, onReject }: {
  a: AssignmentResponse;
  onAccept: () => void;
  onReject: () => void;
}) {
  return (
    <Link
      to={`/shipper/assignments/${a.id}`}
      className="block bg-white rounded-2xl shadow-sm border border-gray-100 p-3.5 active:scale-[0.99] transition"
    >
      <div className="flex items-start justify-between mb-2 gap-2">
        <div className="min-w-0">
          <p className="font-bold truncate">{a.orderCode}</p>
          <p className="text-xs text-gray-500 mt-0.5">{formatRelative(a.assignedAt)}</p>
        </div>
        <StatusBadge status={a.status} />
      </div>

      <p className="text-sm flex items-start gap-1.5">
        <span>📍</span>
        <span className="truncate">{a.deliveryAddress}</span>
      </p>
      <div className="flex justify-between mt-1 text-xs text-gray-500">
        <span>📏 {a.distanceKm} km</span>
        <span className="font-semibold text-orange-600">{formatVnd(a.deliveryFee)}</span>
      </div>

      {a.status === 'OFFERED' && (
        <div className="flex gap-2 mt-3" onClick={e => e.preventDefault()}>
          <button
            onClick={onAccept}
            className="flex-1 py-2.5 bg-emerald-600 text-white rounded-xl text-sm font-semibold shadow-sm active:scale-[0.97] transition"
          >
            ✅ Nhận đơn
          </button>
          <button
            onClick={onReject}
            className="flex-1 py-2.5 border border-red-300 text-red-600 rounded-xl text-sm font-semibold active:scale-[0.97] transition"
          >
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
    REJECTED:   { label: 'Đã từ chối',  cls: 'bg-gray-100 text-gray-700' },
    CANCELLED:  { label: 'Đã hủy',      cls: 'bg-red-100 text-red-700' },
  };
  const info = labels[status] ?? { label: status, cls: 'bg-gray-100 text-gray-700' };
  return (
    <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${info.cls}`}>
      {info.label}
    </span>
  );
}
