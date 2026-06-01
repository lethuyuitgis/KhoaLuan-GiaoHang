import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import type { ReasonCount } from '@shop/shared';

const COLORS = ['#ef4444', '#f97316', '#eab308', '#22c55e', '#3b82f6', '#a855f7', '#ec4899', '#14b8a6', '#84cc16', '#6366f1'];

export function CancellationChart({ data }: { data: ReasonCount[] }) {
  if (data.length === 0) {
    return <div className="h-72 w-full flex items-center justify-center text-gray-500">Không có dữ liệu huỷ trong khoảng này.</div>;
  }

  return (
    <div className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie data={data} dataKey="count" nameKey="reason" outerRadius={100} label>
            {data.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
          </Pie>
          <Tooltip formatter={(v) => [`${String(v)} đơn`, 'Số lượng']} />
          <Legend />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}
