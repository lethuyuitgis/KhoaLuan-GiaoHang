import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import type { TooltipContentProps } from 'recharts';
import type { ReasonCount } from '@shop/shared';
import { chart, TooltipCard, TooltipRow } from './chartTheme';

const MAX_SLICES = 5; // top 5 lý do, phần còn lại gộp "Khác"

function fold(data: ReasonCount[]): ReasonCount[] {
  const sorted = [...data].sort((a, b) => b.count - a.count);
  if (sorted.length <= MAX_SLICES + 1) return sorted;
  const head = sorted.slice(0, MAX_SLICES);
  const rest = sorted.slice(MAX_SLICES).reduce((s, r) => s + r.count, 0);
  return [...head, { reason: 'Khác', count: rest }];
}

export function CancellationChart({ data }: { data: ReasonCount[] }) {
  if (data.length === 0) {
    return <div className="flex h-72 w-full items-center justify-center text-slate-400">Không có dữ liệu huỷ trong khoảng này.</div>;
  }

  const rows = fold(data);
  const total = rows.reduce((s, r) => s + r.count, 0);

  const ReasonTooltip = ({ active, payload }: Partial<TooltipContentProps<number, string>>) => {
    if (!active || !payload?.length) return null;
    const r = payload[0].payload as ReasonCount;
    const pct = ((r.count / total) * 100).toFixed(1);
    const color = chart.categorical[rows.findIndex(x => x.reason === r.reason) % chart.categorical.length];
    return (
      <TooltipCard title={r.reason}>
        <TooltipRow color={color} label="Số đơn" value={`${r.count} (${pct}%)`} />
      </TooltipCard>
    );
  };

  return (
    <div className="flex h-72 w-full items-center gap-4">
      <div className="relative h-full flex-1">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={rows}
              dataKey="count"
              nameKey="reason"
              innerRadius={58}
              outerRadius={92}
              paddingAngle={2}
              stroke={chart.surface}
              strokeWidth={2}
            >
              {rows.map((_, i) => (
                <Cell key={i} fill={chart.categorical[i % chart.categorical.length]} />
              ))}
            </Pie>
            <Tooltip content={<ReasonTooltip />} />
          </PieChart>
        </ResponsiveContainer>
        {/* Tổng ở tâm donut */}
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-2xl font-bold tabular-nums text-slate-900">{total}</span>
          <span className="text-[11px] uppercase tracking-wide text-slate-400">đơn huỷ</span>
        </div>
      </div>
      {/* Legend + nhãn trực tiếp (bù cảnh báo tương phản: định danh không chỉ bằng màu) */}
      <ul className="min-w-[130px] max-w-[46%] space-y-1.5">
        {rows.map((r, i) => (
          <li key={r.reason} className="flex items-center gap-2 text-sm">
            <span className="h-2.5 w-2.5 flex-shrink-0 rounded-full" style={{ backgroundColor: chart.categorical[i % chart.categorical.length] }} />
            <span className="truncate text-slate-600">{r.reason}</span>
            <span className="ml-auto font-semibold tabular-nums text-slate-900">
              {((r.count / total) * 100).toFixed(0)}%
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
