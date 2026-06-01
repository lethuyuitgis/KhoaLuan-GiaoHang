import {
  Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import type { TopShipperRow } from '@shop/shared';

export function TopShippersChart({ data }: { data: TopShipperRow[] }) {
  // Recharts BarChart works best when each row has a short label
  const rows = data.map(r => ({
    name: r.name.length > 18 ? r.name.slice(0, 17) + '…' : r.name,
    deliveredCount: r.deliveredCount,
  }));

  return (
    <div className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={rows} layout="vertical" margin={{ top: 10, right: 16, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis type="number" allowDecimals={false} />
          <YAxis dataKey="name" type="category" width={140} />
          <Tooltip formatter={(v) => [`${String(v)} đơn`, 'Số đơn đã giao']} />
          <Bar dataKey="deliveredCount" fill="#2563eb" />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
