import {
  Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import type { TooltipContentProps } from 'recharts';
import { formatVnd } from '@shop/shared';
import type { RevenuePoint } from '@shop/shared';
import { axisProps, chart, gridProps, TooltipCard, TooltipRow } from './chartTheme';

const fmtAxis = (v: number) =>
  v >= 1_000_000 ? `${(v / 1_000_000).toFixed(1)}tr` :
  v >= 1_000     ? `${(v / 1_000).toFixed(0)}k`     :
  `${v}`;

// Doanh thu (triệu) và số đơn (đơn vị) khác thang đo → chỉ vẽ doanh thu trên
// một trục; số đơn hiển thị kèm trong tooltip (tránh trục kép/đường phẳng đáy).
function RevenueTooltip({ active, payload, label }: Partial<TooltipContentProps<number, string>>) {
  if (!active || !payload?.length) return null;
  const p = payload[0].payload as RevenuePoint;
  return (
    <TooltipCard title={`Ngày ${String(label)}`}>
      <TooltipRow color={chart.primary} label="Doanh thu" value={formatVnd(p.revenue)} />
      <TooltipRow color={chart.inkMuted} label="Số đơn" value={`${p.orderCount} đơn`} />
    </TooltipCard>
  );
}

export function RevenueChart({ data }: { data: RevenuePoint[] }) {
  return (
    <div className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data} margin={{ top: 12, right: 16, left: 0, bottom: 0 }}>
          <defs>
            <linearGradient id="revFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={chart.primary} stopOpacity={0.28} />
              <stop offset="100%" stopColor={chart.primary} stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid {...gridProps} />
          <XAxis dataKey="date" tickFormatter={(d: string) => d.slice(5)} {...axisProps} />
          <YAxis tickFormatter={fmtAxis} width={44} {...axisProps} />
          <Tooltip content={<RevenueTooltip />} cursor={{ stroke: chart.inkMuted, strokeDasharray: '4 4' }} />
          <Area
            type="monotone"
            dataKey="revenue"
            stroke={chart.primary}
            strokeWidth={2.5}
            fill="url(#revFill)"
            dot={false}
            activeDot={{ r: 5, strokeWidth: 2, stroke: chart.surface }}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
