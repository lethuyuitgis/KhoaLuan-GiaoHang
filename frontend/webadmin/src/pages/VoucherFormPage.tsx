import { useState, type FormEvent, useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  fetchAdminVoucherDetail, createVoucher, updateVoucher, formatVnd,
  type CreateVoucherRequest, type UpdateVoucherRequest,
  type VoucherTarget, type DiscountType,
} from '@shop/shared';
import { api } from '@/lib/api';

const CODE_RE = /^[A-Z0-9_-]+$/;

interface FormState {
  code: string;
  name: string;
  target: VoucherTarget;
  discountType: DiscountType;
  discountValue: number;
  maxDiscount: number | null;
  minOrderAmount: number;
  validFrom: string;
  validUntil: string;
  maxUsesTotal: number | null;
  maxUsesPerCustomer: number;
  active: boolean;
}

const empty: FormState = {
  code: '', name: '', target: 'PRODUCTS', discountType: 'FIXED',
  discountValue: 10000, maxDiscount: null, minOrderAmount: 0,
  validFrom: new Date().toISOString().slice(0, 16),
  validUntil: new Date(Date.now() + 30 * 86400_000).toISOString().slice(0, 16),
  maxUsesTotal: 100, maxUsesPerCustomer: 1, active: true,
};

const PREVIEW_SAMPLES = [50000, 100000, 200000, 500000];

export function VoucherFormPage() {
  const { id } = useParams();
  const isEdit = !!id;
  const nav = useNavigate();
  const qc = useQueryClient();
  const [form, setForm] = useState<FormState>(empty);
  const [err, setErr] = useState<string | null>(null);

  const { data: detail } = useQuery({
    queryKey: ['admin', 'voucher', id],
    queryFn: () => fetchAdminVoucherDetail(api, Number(id)),
    enabled: isEdit,
  });

  useEffect(() => {
    if (!detail) return;
    const s = detail.summary;
    setForm({
      code: s.code, name: s.name, target: s.target, discountType: s.discountType,
      discountValue: Number(s.discountValue), maxDiscount: s.maxDiscount,
      minOrderAmount: Number(s.minOrderAmount ?? 0),
      validFrom: s.validFrom.slice(0, 16),
      validUntil: s.validUntil.slice(0, 16),
      maxUsesTotal: s.maxUsesTotal,
      maxUsesPerCustomer: s.maxUsesPerCustomer ?? 1,
      active: s.active,
    });
  }, [detail]);

  const create = useMutation({
    mutationFn: (req: CreateVoucherRequest) => createVoucher(api, req),
    onSuccess: v => {
      qc.invalidateQueries({ queryKey: ['admin', 'vouchers'] });
      nav(`/vouchers/${v.id}`);
    },
    onError: (e: Error & { response?: { data?: { message?: string } } }) =>
      setErr(e.response?.data?.message ?? e.message),
  });

  const update = useMutation({
    mutationFn: (req: UpdateVoucherRequest) => updateVoucher(api, Number(id), req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'vouchers'] });
      nav(`/vouchers/${id}`);
    },
    onError: (e: Error & { response?: { data?: { message?: string } } }) =>
      setErr(e.response?.data?.message ?? e.message),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    if (!isEdit && !CODE_RE.test(form.code)) {
      setErr('Mã chỉ chứa A-Z, 0-9, _, -');
      return;
    }
    if (new Date(form.validUntil) <= new Date(form.validFrom)) {
      setErr('Ngày hết hạn phải sau ngày bắt đầu');
      return;
    }
    if (form.discountType === 'PERCENT' && form.discountValue > 100) {
      setErr('Phần trăm phải ≤ 100');
      return;
    }
    const fromIso = new Date(form.validFrom).toISOString();
    const untilIso = new Date(form.validUntil).toISOString();
    if (isEdit) {
      update.mutate({
        name: form.name, discountValue: form.discountValue,
        maxDiscount: form.maxDiscount ?? null, minOrderAmount: form.minOrderAmount,
        validFrom: fromIso, validUntil: untilIso,
        maxUsesTotal: form.maxUsesTotal ?? null,
        maxUsesPerCustomer: form.maxUsesPerCustomer,
        active: form.active,
      });
    } else {
      create.mutate({
        code: form.code.toUpperCase(), name: form.name,
        target: form.target, discountType: form.discountType,
        discountValue: form.discountValue, maxDiscount: form.maxDiscount,
        minOrderAmount: form.minOrderAmount,
        validFrom: fromIso, validUntil: untilIso,
        maxUsesTotal: form.maxUsesTotal,
        maxUsesPerCustomer: form.maxUsesPerCustomer,
      });
    }
  };

  const computeDiscount = (subtotal: number): number => {
    if (form.discountType === 'FIXED') return Math.min(form.discountValue, subtotal);
    const raw = Math.round((subtotal * form.discountValue) / 100);
    return form.maxDiscount ? Math.min(raw, form.maxDiscount) : raw;
  };

  return (
    <form onSubmit={submit} className="max-w-3xl mx-auto pb-32">
      {/* ─────────── Back link + header ─────────── */}
      <Link to="/vouchers" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 mb-3">
        ← Tất cả voucher
      </Link>
      <header className="mb-6 flex items-center gap-3">
        <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-orange-500 to-amber-600 flex items-center justify-center text-white text-xl shadow-md">
          🎟️
        </div>
        <div>
          <h1 className="text-2xl font-bold text-gray-900 tracking-tight">
            {isEdit ? 'Sửa voucher' : 'Tạo voucher mới'}
          </h1>
          <p className="text-sm text-gray-500">
            {isEdit ? 'Cập nhật điều kiện áp dụng + tên + trạng thái' : 'Khách sẽ gõ mã ở Checkout để giảm tiền hàng hoặc phí ship'}
          </p>
        </div>
      </header>

      <div className="space-y-4">
        {/* ─────────── Section 1: Thông tin chung ─────────── */}
        <Section icon="📝" title="Thông tin chung" hint="Mã + tên + đối tượng — không sửa được sau khi tạo">
          <Field label="Mã voucher" required hint="VIẾT HOA, A-Z 0-9 _ -">
            <input
              value={form.code}
              disabled={isEdit}
              onChange={e => setForm({ ...form, code: e.target.value.toUpperCase() })}
              className="input font-mono uppercase tracking-wider"
              placeholder="HELLO20K"
              maxLength={32}
              required
            />
          </Field>
          <Field label="Tên hiển thị" required>
            <input
              value={form.name}
              onChange={e => setForm({ ...form, name: e.target.value })}
              className="input"
              maxLength={128}
              placeholder="VD: Chào khách mới — giảm 20 000đ"
              required
            />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Đối tượng" required>
              <SegmentedControl
                value={form.target}
                disabled={isEdit}
                options={[
                  { value: 'PRODUCTS', label: '🛍️ Tiền hàng' },
                  { value: 'SHIPPING', label: '🚚 Phí ship' },
                ]}
                onChange={v => setForm({ ...form, target: v as VoucherTarget })}
              />
            </Field>
            <Field label="Loại giảm" required>
              <SegmentedControl
                value={form.discountType}
                disabled={isEdit}
                options={[
                  { value: 'FIXED', label: 'Số tiền (đ)' },
                  { value: 'PERCENT', label: 'Phần trăm (%)' },
                ]}
                onChange={v => setForm({ ...form, discountType: v as DiscountType })}
              />
            </Field>
          </div>
        </Section>

        {/* ─────────── Section 2: Mức giảm ─────────── */}
        <Section icon="💸" title="Mức giảm">
          <div className="grid grid-cols-2 gap-3">
            <Field label={`Giá trị ${form.discountType === 'PERCENT' ? '(%)' : '(đ)'}`} required>
              <input
                type="number"
                min={1}
                value={form.discountValue}
                onChange={e => setForm({ ...form, discountValue: Number(e.target.value) })}
                className="input"
                required
              />
            </Field>
            {form.discountType === 'PERCENT' && (
              <Field label="Giảm tối đa (đ)" hint="Để trống = không giới hạn">
                <input
                  type="number"
                  min={0}
                  value={form.maxDiscount ?? ''}
                  onChange={e => setForm({ ...form, maxDiscount: e.target.value ? Number(e.target.value) : null })}
                  className="input"
                  placeholder="∞"
                />
              </Field>
            )}
          </div>

          {/* Preview table — 4 sample subtotals */}
          <div className="bg-gradient-to-br from-orange-50 to-amber-50 border border-orange-200 rounded-xl p-4">
            <p className="text-xs font-semibold text-orange-700 uppercase tracking-wide mb-2">
              📊 Xem trước với 4 mức đơn
            </p>
            <table className="w-full text-sm">
              <tbody className="divide-y divide-orange-100">
                {PREVIEW_SAMPLES.map(sample => {
                  const discount = computeDiscount(sample);
                  const finalAmt = sample - discount;
                  return (
                    <tr key={sample}>
                      <td className="py-1.5 text-gray-600 tabular-nums">Đơn {formatVnd(sample)}</td>
                      <td className="py-1.5 text-emerald-700 tabular-nums font-semibold">
                        −{formatVnd(discount)}
                      </td>
                      <td className="py-1.5 text-right font-bold text-gray-900 tabular-nums">
                        = {formatVnd(finalAmt)}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </Section>

        {/* ─────────── Section 3: Điều kiện ─────────── */}
        <Section icon="⚙️" title="Điều kiện áp dụng">
          <Field label="Đơn tối thiểu (đ)" hint="Khách phải đặt từ số tiền này mới được áp">
            <input
              type="number"
              min={0}
              step={1000}
              value={form.minOrderAmount}
              onChange={e => setForm({ ...form, minOrderAmount: Number(e.target.value) })}
              className="input"
            />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Hiệu lực từ" required>
              <input
                type="datetime-local"
                value={form.validFrom}
                onChange={e => setForm({ ...form, validFrom: e.target.value })}
                className="input"
                required
              />
            </Field>
            <Field label="Hết hạn" required>
              <input
                type="datetime-local"
                value={form.validUntil}
                onChange={e => setForm({ ...form, validUntil: e.target.value })}
                className="input"
                required
              />
            </Field>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Tổng lượt áp" hint="Trên toàn shop — để trống = ∞">
              <input
                type="number"
                min={1}
                value={form.maxUsesTotal ?? ''}
                onChange={e => setForm({ ...form, maxUsesTotal: e.target.value ? Number(e.target.value) : null })}
                className="input"
                placeholder="∞"
              />
            </Field>
            <Field label="Mỗi khách tối đa" required hint="Vd: 1 = chỉ áp được 1 lần/khách">
              <input
                type="number"
                min={1}
                value={form.maxUsesPerCustomer}
                onChange={e => setForm({ ...form, maxUsesPerCustomer: Number(e.target.value) })}
                className="input"
                required
              />
            </Field>
          </div>
        </Section>

        {/* ─────────── Section 4: Trạng thái (edit only) ─────────── */}
        {isEdit && (
          <Section icon="🔘" title="Trạng thái">
            <label className="flex items-center justify-between p-3 rounded-xl bg-gray-50 cursor-pointer hover:bg-gray-100 transition-colors">
              <div>
                <p className="text-sm font-medium text-gray-900">Voucher đang hoạt động</p>
                <p className="text-xs text-gray-500 mt-0.5">Tắt để ẩn voucher khỏi danh sách + chặn áp mới</p>
              </div>
              <input
                type="checkbox"
                checked={form.active}
                onChange={e => setForm({ ...form, active: e.target.checked })}
                className="w-5 h-5 accent-orange-500"
              />
            </label>
          </Section>
        )}

        {err && (
          <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-sm text-red-700 flex items-start gap-2">
            <span className="text-base">⚠️</span>
            <span>{err}</span>
          </div>
        )}
      </div>

      {/* ─────────── Sticky submit bar ─────────── */}
      <div className="fixed bottom-0 left-64 right-0 bg-white border-t border-gray-200 px-6 py-4 shadow-[0_-4px_12px_rgba(0,0,0,0.04)] z-10">
        <div className="max-w-3xl mx-auto flex items-center justify-between gap-3">
          <button
            type="button"
            onClick={() => nav(-1)}
            className="px-5 py-2.5 bg-white border border-gray-200 hover:bg-gray-50 text-gray-700 rounded-xl text-sm font-medium transition-colors"
          >
            Huỷ
          </button>
          <button
            type="submit"
            disabled={create.isPending || update.isPending}
            className="px-6 py-2.5 bg-orange-500 hover:bg-orange-600 text-white rounded-xl text-sm font-semibold disabled:opacity-50 transition-colors shadow-sm inline-flex items-center gap-2"
          >
            {(create.isPending || update.isPending) ? (
              <><Spinner /> Đang lưu…</>
            ) : (
              <>{isEdit ? '💾 Lưu thay đổi' : '+ Tạo voucher'}</>
            )}
          </button>
        </div>
      </div>

      <style>{`
        .input {
          margin-top: 0.25rem;
          width: 100%;
          padding: 0.5rem 0.75rem;
          border: 1px solid #e5e7eb;
          border-radius: 0.625rem;
          font-size: 0.875rem;
          background: white;
          transition: border-color 0.15s, box-shadow 0.15s;
        }
        .input:focus {
          outline: none;
          border-color: #fb923c;
          box-shadow: 0 0 0 3px rgba(251, 146, 60, 0.15);
        }
        .input:disabled {
          background: #f9fafb;
          color: #6b7280;
          cursor: not-allowed;
        }
      `}</style>
    </form>
  );
}

// ───────────── Components ─────────────

function Section({
  icon,
  title,
  hint,
  children,
}: {
  icon: string;
  title: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <section className="bg-white rounded-2xl border border-gray-200 p-5">
      <header className="flex items-start gap-3 mb-4 pb-3 border-b border-gray-100">
        <span className="text-xl">{icon}</span>
        <div>
          <h2 className="font-semibold text-gray-900">{title}</h2>
          {hint && <p className="text-xs text-gray-500 mt-0.5">{hint}</p>}
        </div>
      </header>
      <div className="space-y-3">{children}</div>
    </section>
  );
}

function Field({
  label,
  required,
  hint,
  children,
}: {
  label: string;
  required?: boolean;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="block">
      <span className="text-xs font-medium text-gray-700">
        {label}
        {required && <span className="text-red-500 ml-0.5">*</span>}
      </span>
      {children}
      {hint && <p className="text-[11px] text-gray-400 mt-1">{hint}</p>}
    </label>
  );
}

function SegmentedControl({
  value,
  options,
  onChange,
  disabled,
}: {
  value: string;
  options: { value: string; label: string }[];
  onChange: (v: string) => void;
  disabled?: boolean;
}) {
  return (
    <div className={`mt-1 inline-flex w-full gap-1 bg-gray-100 p-1 rounded-lg ${disabled ? 'opacity-60' : ''}`}>
      {options.map(o => (
        <button
          key={o.value}
          type="button"
          disabled={disabled}
          onClick={() => onChange(o.value)}
          className={`flex-1 text-xs px-3 py-1.5 rounded-md font-medium transition-all ${
            value === o.value
              ? 'bg-white text-gray-900 shadow-sm'
              : 'text-gray-500 hover:text-gray-700'
          } ${disabled ? 'cursor-not-allowed' : ''}`}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

function Spinner() {
  return (
    <svg className="w-4 h-4 animate-spin" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
    </svg>
  );
}
