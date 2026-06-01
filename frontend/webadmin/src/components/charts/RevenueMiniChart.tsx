import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { formatVnd } from '@shop/shared';
import type { RevenuePoint } from '@shop/shared';

interface Props { data: RevenuePoint[]; }

export function RevenueMiniChart({ data }: Props) {
  const fmtAxis = (v: number) =>
    v >= 1_000_000 ? `${(v / 1_000_000).toFixed(1)}tr` :
    v >= 1_000     ? `${(v / 1_000).toFixed(0)}k`     :
    `${v}`;

  return (
    <div className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 10, right: 16, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="date" tickFormatter={(d: string) => d.slice(5)} />
          <YAxis tickFormatter={fmtAxis} />
          <Tooltip
            formatter={(value, name) =>
              name === 'revenue'
                ? [formatVnd(Number(value)), 'Doanh thu']
                : [String(value), 'Số đơn']
            }
            labelFormatter={(label) => `Ngày ${String(label)}`}
          />
          <Line type="monotone" dataKey="revenue" stroke="#16a34a" strokeWidth={2} dot={{ r: 3 }} />
          <Line type="monotone" dataKey="orderCount" stroke="#2563eb" strokeWidth={2} dot={{ r: 3 }} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
