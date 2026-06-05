import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { BarChart, Bar, XAxis, YAxis, ResponsiveContainer, Tooltip, LineChart, Line } from 'recharts';
import { fetchAdminShipperEarnings, formatVnd } from '@shop/shared';
import { api } from '@/lib/api';

export function ShipperEarningsPage() {
  const [from, setFrom] = useState(new Date(Date.now() - 7*86400_000).toISOString().slice(0,10));
  const [to,   setTo]   = useState(new Date().toISOString().slice(0,10));

  const fromIso = `${from}T00:00:00Z`;
  const toIso = `${to}T23:59:59Z`;

  const { data: byShipper = [] } = useQuery({
    queryKey: ['admin','earnings','shipper', from, to],
    queryFn: () => fetchAdminShipperEarnings(api, fromIso, toIso, 'shipper'),
  });
  const { data: byDay = [] } = useQuery({
    queryKey: ['admin','earnings','day', from, to],
    queryFn: () => fetchAdminShipperEarnings(api, fromIso, toIso, 'day'),
  });

  const totalCommission = useMemo(() => byShipper.reduce((s, b) => s + Number(b.commission), 0), [byShipper]);
  const totalOrders = useMemo(() => byShipper.reduce((s, b) => s + b.ordersCount, 0), [byShipper]);

  return (
    <div>
      <header className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold">Thu nhập shipper</h1>
          <p className="text-sm text-gray-500">Hoa hồng đã chi trả theo khoảng thời gian</p>
        </div>
        <div className="flex gap-2">
          <input type="date" value={from} onChange={e => setFrom(e.target.value)} className="px-3 py-2 rounded border" />
          <span className="self-center">→</span>
          <input type="date" value={to} onChange={e => setTo(e.target.value)} className="px-3 py-2 rounded border" />
        </div>
      </header>

      <div className="grid grid-cols-3 gap-4 mb-6">
        <Kpi label="Tổng hoa hồng" value={formatVnd(totalCommission)} color="text-green-700" />
        <Kpi label="Số đơn DELIVERED" value={String(totalOrders)} />
        <Kpi label="Shipper hoạt động" value={String(byShipper.filter(b => b.ordersCount > 0).length)} />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <Panel title="Top shipper (theo hoa hồng)">
          {byShipper.length === 0 ? <EmptyChart /> : (
            <ResponsiveContainer width="100%" height={260}>
              <BarChart
                data={byShipper.slice().sort((a,b) => Number(b.commission) - Number(a.commission)).slice(0, 5)}
                layout="vertical" margin={{ left: 60 }}>
                <XAxis type="number" tickFormatter={n => `${Math.round(n/1000)}k`} />
                <YAxis dataKey="groupKey" type="category" width={80} />
                <Tooltip formatter={(v) => formatVnd(Number(v))} />
                <Bar dataKey="commission" fill="#f97316" radius={[0,4,4,0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </Panel>
        <Panel title="Hoa hồng theo ngày">
          {byDay.length === 0 ? <EmptyChart /> : (
            <ResponsiveContainer width="100%" height={260}>
              <LineChart data={byDay}>
                <XAxis dataKey="groupKey" tickFormatter={d => d.slice(5)} />
                <YAxis tickFormatter={n => `${Math.round(n/1000)}k`} />
                <Tooltip formatter={(v) => formatVnd(Number(v))} />
                <Line type="monotone" dataKey="commission" stroke="#f97316" strokeWidth={2} />
              </LineChart>
            </ResponsiveContainer>
          )}
        </Panel>
      </div>
    </div>
  );
}

function Kpi({ label, value, color }:{label:string;value:string;color?:string}) {
  return <div className="bg-white rounded-xl p-4">
    <p className="text-xs text-gray-500">{label}</p>
    <p className={`text-xl font-bold mt-1 ${color ?? ''}`}>{value}</p>
  </div>;
}
function Panel({ title, children }:{title:string;children:React.ReactNode}) {
  return <div className="bg-white rounded-xl p-4"><p className="font-semibold mb-3">{title}</p>{children}</div>;
}
function EmptyChart() {
  return <p className="text-center text-sm text-gray-500 py-20">Chưa có dữ liệu</p>;
}
