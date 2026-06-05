import { useEffect, useState, type FormEvent } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchAdminShopConfig,
  updateShopConfig,
  type ShopConfig,
  type UpdateShopConfigRequest,
} from '@shop/shared';
import { api } from '@/lib/api';

const HEX_RE = /^#[0-9A-Fa-f]{6}$/;

/**
 * Multi-shop configuration page. Lets the admin theme the customer-facing
 * apps (miniapp + zaloapp) and tune fees + pickup location without code.
 */
export function SettingsPage() {
  const qc = useQueryClient();
  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin', 'shop-config'],
    queryFn: () => fetchAdminShopConfig(api),
  });

  return (
    <div>
      <header className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Cài đặt</h1>
        <p className="text-sm text-gray-500 mt-1">
          Tuỳ chỉnh thương hiệu, điểm pickup và phí giao cho shop của bạn.
        </p>
      </header>

      {isLoading && <LoadingSkeleton />}
      {isError && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-sm text-red-700">
          Không tải được cấu hình shop. Vui lòng thử lại.
        </div>
      )}
      {data && <SettingsForm initial={data} onSaved={c => qc.setQueryData(['admin', 'shop-config'], c)} />}
    </div>
  );
}

function LoadingSkeleton() {
  return (
    <div className="space-y-4 max-w-3xl">
      {[1, 2, 3, 4].map(i => (
        <div key={i} className="bg-white rounded-xl border border-gray-200 p-6 animate-pulse">
          <div className="h-5 w-40 bg-gray-200 rounded mb-4" />
          <div className="space-y-3">
            <div className="h-10 bg-gray-100 rounded" />
            <div className="h-10 bg-gray-100 rounded" />
          </div>
        </div>
      ))}
    </div>
  );
}

interface FormProps {
  initial: ShopConfig;
  onSaved: (c: ShopConfig) => void;
}

function SettingsForm({ initial, onSaved }: FormProps) {
  const [form, setForm] = useState<ShopConfig>(initial);
  const [toast, setToast] = useState<{ kind: 'ok' | 'err'; msg: string } | null>(null);

  useEffect(() => setForm(initial), [initial]);

  const mut = useMutation({
    mutationFn: (body: UpdateShopConfigRequest) => updateShopConfig(api, body),
    onSuccess: c => {
      onSaved(c);
      setToast({ kind: 'ok', msg: 'Đã lưu thay đổi' });
      setTimeout(() => setToast(null), 3000);
    },
    onError: (err: Error & { response?: { data?: { message?: string } } }) => {
      const msg = err.response?.data?.message ?? err.message ?? 'Không lưu được';
      setToast({ kind: 'err', msg });
      setTimeout(() => setToast(null), 4000);
    },
  });

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!HEX_RE.test(form.brandPrimary) || !HEX_RE.test(form.brandSecondary)) {
      setToast({ kind: 'err', msg: 'Màu phải có dạng #RRGGBB (vd: #D97706)' });
      return;
    }
    mut.mutate(form);
  };

  const set = <K extends keyof ShopConfig>(k: K, v: ShopConfig[K]) =>
    setForm(prev => ({ ...prev, [k]: v }));

  return (
    <form onSubmit={handleSubmit} className="space-y-6 max-w-3xl pb-32">
      {/* Section 1: Shop info */}
      <Section title="Thông tin shop" subtitle="Hiển thị trên Mini App và header customer">
        <Field label="Tên shop" required>
          <input type="text" required minLength={3} maxLength={128}
            value={form.name} onChange={e => set('name', e.target.value)}
            className="input" />
        </Field>
        <Field label="Tagline (slogan ngắn)">
          <input type="text" maxLength={256}
            value={form.tagline} onChange={e => set('tagline', e.target.value)}
            className="input" placeholder="vd: Giao đồ ăn nhanh • Thanh toán dễ" />
        </Field>
        <Field label="Logo URL">
          <input type="url" value={form.logoUrl ?? ''}
            onChange={e => set('logoUrl', e.target.value || null)}
            className="input" placeholder="https://cdn.example.com/logo.png" />
          {form.logoUrl && (
            <img src={form.logoUrl} alt="Logo preview" className="mt-2 w-16 h-16 rounded-lg object-contain bg-gray-50 border border-gray-200" />
          )}
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Số điện thoại liên hệ">
            <input type="tel" value={form.contactPhone ?? ''}
              onChange={e => set('contactPhone', e.target.value || null)}
              className="input" placeholder="+84901234567" />
          </Field>
          <Field label="Email liên hệ">
            <input type="email" value={form.contactEmail ?? ''}
              onChange={e => set('contactEmail', e.target.value || null)}
              className="input" placeholder="shop@example.com" />
          </Field>
        </div>
        <Field label="Giờ mở cửa">
          <input type="text" value={form.openingHours ?? ''} maxLength={64}
            onChange={e => set('openingHours', e.target.value || null)}
            className="input" placeholder="08:00 - 22:00 hằng ngày" />
        </Field>
      </Section>

      {/* Section 2: Branding */}
      <Section title="Thương hiệu" subtitle="Màu chính dùng cho nút bấm, hero và accent trong Mini App">
        <div className="grid grid-cols-2 gap-3">
          <ColorField label="Màu chính (primary)" value={form.brandPrimary}
            onChange={v => set('brandPrimary', v)} />
          <ColorField label="Màu phụ (secondary)" value={form.brandSecondary}
            onChange={v => set('brandSecondary', v)} />
        </div>
        <div className="mt-3 rounded-xl border border-gray-200 p-4 bg-gray-50">
          <p className="text-xs text-gray-500 mb-2">Xem trước</p>
          <div className="flex items-center gap-3">
            <button type="button"
              style={{ backgroundColor: form.brandPrimary }}
              className="px-4 py-2 rounded-full text-white text-sm font-semibold">
              Đặt hàng
            </button>
            <span className="px-3 py-1 rounded-full text-xs font-medium text-white"
              style={{ backgroundColor: form.brandSecondary }}>
              Khuyến mãi
            </span>
          </div>
        </div>
      </Section>

      {/* Section 3: Pickup point */}
      <Section title="Điểm lấy hàng (pickup)" subtitle="Toạ độ shop — dùng để tính khoảng cách giao">
        <Field label="Địa chỉ" required>
          <input type="text" required minLength={5} maxLength={256}
            value={form.pickupAddress} onChange={e => set('pickupAddress', e.target.value)}
            className="input" />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Vĩ độ (lat)" required>
            <input type="number" step="0.0000001" required
              value={form.pickupLat} onChange={e => set('pickupLat', Number(e.target.value))}
              className="input" />
          </Field>
          <Field label="Kinh độ (lng)" required>
            <input type="number" step="0.0000001" required
              value={form.pickupLng} onChange={e => set('pickupLng', Number(e.target.value))}
              className="input" />
          </Field>
        </div>
        <a
          href={`https://www.google.com/maps?q=${form.pickupLat},${form.pickupLng}`}
          target="_blank" rel="noreferrer"
          className="text-sm text-orange-600 hover:underline">
          Xem trên Google Maps →
        </a>
      </Section>

      {/* Section 4: Fees */}
      <Section title="Phí giao hàng" subtitle="Công thức: feeBase + max(0, km − freeKm) × feePerKm">
        <div className="grid grid-cols-3 gap-3">
          <Field label="Phí cơ bản (VND)" required>
            <input type="number" min={0} step={1000} required
              value={form.feeBase} onChange={e => set('feeBase', Number(e.target.value))}
              className="input" />
          </Field>
          <Field label="Phí mỗi km (VND)" required>
            <input type="number" min={0} step={500} required
              value={form.feePerKm} onChange={e => set('feePerKm', Number(e.target.value))}
              className="input" />
          </Field>
          <Field label="Số km miễn phí" required>
            <input type="number" min={0} step={0.1} required
              value={form.freeKm} onChange={e => set('freeKm', Number(e.target.value))}
              className="input" />
          </Field>
        </div>
        <p className="text-xs text-gray-500 mt-2">
          Ví dụ: với 3km và feeBase=15k, feePerKm=5k, freeKm=0 →{' '}
          <strong>{(Number(form.feeBase) + 3 * Number(form.feePerKm)).toLocaleString('vi-VN')}đ</strong>
        </p>
      </Section>

      {/* Section 5: Shipper commission */}
      <Section title="Hoa hồng shipper" subtitle="% phí ship gốc shipper được hưởng khi đơn DELIVERED">
        <Field label="Tỉ lệ hoa hồng (%)" required>
          <input type="number" min={0} max={100} step={0.1} required
            value={form.shipperCommissionPct}
            onChange={e => set('shipperCommissionPct', Number(e.target.value))}
            className="input" />
        </Field>
        <p className="text-xs text-gray-500">
          Đơn 30 000đ phí ship × {form.shipperCommissionPct}% = <strong>{Math.round(30000 * Number(form.shipperCommissionPct || 0) / 100).toLocaleString('vi-VN')}đ</strong>
        </p>
      </Section>

      {/* Sticky submit bar */}
      <div className="fixed bottom-0 left-64 right-0 bg-white border-t border-gray-200 px-6 py-4 shadow-lg z-10">
        <div className="max-w-3xl flex items-center gap-3">
          <button type="submit" disabled={mut.isPending}
            className="flex-1 py-3 bg-orange-500 hover:bg-orange-600 disabled:opacity-50 text-white rounded-xl font-semibold transition-colors">
            {mut.isPending ? 'Đang lưu…' : 'Lưu thay đổi'}
          </button>
          {toast && (
            <div className={`text-sm font-medium px-3 py-2 rounded-lg ${
              toast.kind === 'ok'
                ? 'bg-green-50 text-green-700 border border-green-200'
                : 'bg-red-50 text-red-700 border border-red-200'
            }`}>
              {toast.msg}
            </div>
          )}
        </div>
      </div>

      {/* Local Tailwind class shortcut — keep one source of truth for the input
          look so each Section stays scannable. */}
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
      `}</style>
    </form>
  );
}

function Section({ title, subtitle, children }: {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
}) {
  return (
    <section className="bg-white rounded-xl border border-gray-200 p-6">
      <h2 className="font-semibold text-gray-900">{title}</h2>
      {subtitle && <p className="text-xs text-gray-500 mt-0.5 mb-4">{subtitle}</p>}
      <div className="space-y-3">{children}</div>
    </section>
  );
}

function Field({ label, required, children }: {
  label: string;
  required?: boolean;
  children: React.ReactNode;
}) {
  return (
    <label className="block">
      <span className="text-sm text-gray-700">
        {label}{required && <span className="text-red-500 ml-0.5">*</span>}
      </span>
      {children}
    </label>
  );
}

function ColorField({ label, value, onChange }: {
  label: string;
  value: string;
  onChange: (v: string) => void;
}) {
  const valid = HEX_RE.test(value);
  return (
    <label className="block">
      <span className="text-sm text-gray-700">{label}</span>
      <div className="mt-1 flex items-center gap-2">
        <input type="color" value={valid ? value : '#000000'}
          onChange={e => onChange(e.target.value.toUpperCase())}
          className="w-12 h-10 rounded border border-gray-300 cursor-pointer" />
        <input type="text" value={value}
          onChange={e => onChange(e.target.value)}
          className="flex-1 input" placeholder="#D97706"
          title={valid ? value : 'Phải là mã hex 6 ký tự — vd #D97706'} />
      </div>
    </label>
  );
}
