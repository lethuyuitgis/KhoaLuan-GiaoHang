import { useState, type FormEvent, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
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
  validFrom: string;      // YYYY-MM-DDTHH:mm
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
      code: s.code,
      name: s.name,
      target: s.target,
      discountType: s.discountType,
      discountValue: Number(s.discountValue),
      maxDiscount: s.maxDiscount,
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
      setErr('valid_until phải sau valid_from');
      return;
    }
    if (form.discountType === 'PERCENT' && form.discountValue > 100) {
      setErr('PERCENT phải ≤ 100');
      return;
    }

    const fromIso = new Date(form.validFrom).toISOString();
    const untilIso = new Date(form.validUntil).toISOString();

    if (isEdit) {
      update.mutate({
        name: form.name,
        discountValue: form.discountValue,
        maxDiscount: form.maxDiscount ?? null,
        minOrderAmount: form.minOrderAmount,
        validFrom: fromIso,
        validUntil: untilIso,
        maxUsesTotal: form.maxUsesTotal ?? null,
        maxUsesPerCustomer: form.maxUsesPerCustomer,
        active: form.active,
      });
    } else {
      create.mutate({
        code: form.code.toUpperCase(),
        name: form.name,
        target: form.target,
        discountType: form.discountType,
        discountValue: form.discountValue,
        maxDiscount: form.maxDiscount,
        minOrderAmount: form.minOrderAmount,
        validFrom: fromIso,
        validUntil: untilIso,
        maxUsesTotal: form.maxUsesTotal,
        maxUsesPerCustomer: form.maxUsesPerCustomer,
      });
    }
  };

  const preview = (() => {
    const sample = 200000;
    if (form.discountType === 'FIXED') return Math.min(form.discountValue, sample);
    const raw = Math.round(sample * form.discountValue / 100);
    return form.maxDiscount ? Math.min(raw, form.maxDiscount) : raw;
  })();

  return (
    <form onSubmit={submit} className="max-w-2xl space-y-4">
      <header className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">
          {isEdit ? 'Sửa voucher' : 'Tạo voucher mới'}
        </h1>
      </header>

      <Section title="Thông tin chung">
        <Field label="Mã voucher" required>
          <input
            value={form.code}
            disabled={isEdit}
            onChange={e => setForm({ ...form, code: e.target.value.toUpperCase() })}
            className="input font-mono"
            placeholder="VD: HELLO20K"
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
            required
          />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Đối tượng" required>
            <select
              value={form.target}
              disabled={isEdit}
              onChange={e => setForm({ ...form, target: e.target.value as VoucherTarget })}
              className="input"
            >
              <option value="PRODUCTS">🛍️ Giảm tiền hàng</option>
              <option value="SHIPPING">🚚 Giảm phí ship</option>
            </select>
          </Field>
          <Field label="Loại giảm" required>
            <select
              value={form.discountType}
              disabled={isEdit}
              onChange={e => setForm({ ...form, discountType: e.target.value as DiscountType })}
              className="input"
            >
              <option value="FIXED">Số tiền cố định (đ)</option>
              <option value="PERCENT">Phần trăm (%)</option>
            </select>
          </Field>
        </div>
      </Section>

      <Section title="Mức giảm">
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
          <Field label="Giảm tối đa (cap, đ)">
            <input
              type="number"
              min={0}
              value={form.maxDiscount ?? ''}
              onChange={e =>
                setForm({ ...form, maxDiscount: e.target.value ? Number(e.target.value) : null })
              }
              className="input"
              placeholder="Để trống = không giới hạn"
            />
          </Field>
        )}
        <div className="rounded-xl bg-gray-50 border border-gray-200 p-3 text-sm">
          📊 <strong>Xem trước:</strong> đơn 200 000đ → giảm{' '}
          <strong>{formatVnd(preview)}</strong>
        </div>
      </Section>

      <Section title="Điều kiện áp dụng">
        <Field label="Đơn tối thiểu (đ)">
          <input
            type="number"
            min={0}
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
          <Field label="Tổng số lần dùng (max_uses_total)">
            <input
              type="number"
              min={1}
              value={form.maxUsesTotal ?? ''}
              onChange={e =>
                setForm({ ...form, maxUsesTotal: e.target.value ? Number(e.target.value) : null })
              }
              className="input"
              placeholder="Để trống = không giới hạn"
            />
          </Field>
          <Field label="Mỗi khách dùng tối đa" required>
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

      {isEdit && (
        <Section title="Trạng thái">
          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="checkbox"
              checked={form.active}
              onChange={e => setForm({ ...form, active: e.target.checked })}
              className="w-4 h-4"
            />
            <span className="text-sm text-gray-700">Active</span>
          </label>
        </Section>
      )}

      {err && (
        <div className="bg-red-50 border border-red-200 rounded p-3 text-sm text-red-700">
          {err}
        </div>
      )}

      <div className="flex gap-3">
        <button
          type="submit"
          disabled={create.isPending || update.isPending}
          className="px-5 py-3 bg-orange-500 hover:bg-orange-600 text-white rounded-xl font-semibold disabled:opacity-50 transition-colors"
        >
          {isEdit ? 'Lưu thay đổi' : 'Tạo voucher'}
        </button>
        <button
          type="button"
          onClick={() => nav(-1)}
          className="px-5 py-3 bg-gray-200 hover:bg-gray-300 text-gray-700 rounded-xl transition-colors"
        >
          Huỷ
        </button>
      </div>

      <style>{`
        .input {
          margin-top: 0.25rem;
          width: 100%;
          padding: 0.5rem 0.75rem;
          border: 1px solid #d1d5db;
          border-radius: 0.5rem;
          font-size: 0.875rem;
          background: white;
        }
        .input:focus {
          outline: none;
          border-color: #fb923c;
          box-shadow: 0 0 0 3px rgba(251, 146, 60, 0.2);
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

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="bg-white rounded-xl border border-gray-200 p-5">
      <h2 className="font-semibold text-gray-900 mb-4">{title}</h2>
      <div className="space-y-3">{children}</div>
    </section>
  );
}

function Field({
  label,
  required,
  children,
}: {
  label: string;
  required?: boolean;
  children: React.ReactNode;
}) {
  return (
    <label className="block">
      <span className="text-sm text-gray-700">
        {label}
        {required && <span className="text-red-500 ml-0.5">*</span>}
      </span>
      {children}
    </label>
  );
}
