import type { ReactNode } from 'react';

/**
 * Bảng màu + token dùng chung cho toàn bộ biểu đồ Web Admin.
 * Chữ luôn dùng token mực (ink), không bao giờ mượn màu chuỗi dữ liệu.
 * Palette categorical đã qua kiểm định CVD (dataviz validate_palette.js).
 */
export const chart = {
  // Mực (text) — nhãn/giá trị/legend luôn mặc token này
  ink:      '#0f172a',
  inkSoft:  '#475569',
  inkMuted: '#94a3b8',
  grid:     '#eef2f7',
  surface:  '#ffffff',

  // Chuỗi chính (doanh thu, thanh) — brand blue
  primary:  '#2563eb',

  // Categorical (donut lý do huỷ) — thứ tự cố định, không xoay vòng
  categorical: ['#2563eb', '#f59e0b', '#10b981', '#8b5cf6', '#ef4444', '#64748b'],
} as const;

/** Trục thưa (recessive): bỏ đường trục/tick, chữ mực nhạt. */
export const axisProps = {
  tickLine: false,
  axisLine: false,
  tick: { fill: chart.inkMuted, fontSize: 12 },
} as const;

/** Lưới thưa: chỉ đường ngang, màu nhạt. */
export const gridProps = {
  vertical: false,
  stroke: chart.grid,
} as const;

/** Thẻ tooltip nền trắng bo góc, đổ bóng nhẹ — dùng chung mọi chart. */
export function TooltipCard({ title, children }: { title?: ReactNode; children: ReactNode }) {
  return (
    <div className="rounded-xl border border-slate-100 bg-white/95 px-3 py-2 shadow-lg backdrop-blur-sm">
      {title != null && (
        <p className="mb-1 text-[11px] font-semibold uppercase tracking-wide text-slate-400">{title}</p>
      )}
      <div className="space-y-0.5">{children}</div>
    </div>
  );
}

/** Một dòng trong tooltip: chấm màu chuỗi + nhãn (mực nhạt) + giá trị (mực đậm). */
export function TooltipRow({ color, label, value }: { color: string; label: string; value: ReactNode }) {
  return (
    <div className="flex items-center gap-2 text-sm">
      <span className="h-2.5 w-2.5 flex-shrink-0 rounded-full" style={{ backgroundColor: color }} />
      <span className="text-slate-500">{label}</span>
      <span className="ml-auto font-semibold tabular-nums text-slate-900">{value}</span>
    </div>
  );
}
