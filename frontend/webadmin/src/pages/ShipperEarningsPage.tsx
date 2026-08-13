import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, LabelList,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import type { TooltipContentProps } from 'recharts';
import { fetchAdminShipperEarnings, formatVnd } from '@shop/shared';
import { api } from '@/lib/api';
import { axisProps, chart, gridProps, TooltipCard, TooltipRow } from '@/components/charts/chartTheme';

const fmtK = (n: number) => `${Math.round(n / 1000)}k`;

function CommissionTooltip({ active, payload, label }: Partial<TooltipContentProps<number, string>>) {
  if (!active || !payload?.length) return null;
  return (
    <TooltipCard title={String(label)}>
      <TooltipRow color={chart.primary} label="Hoa hồng" value={formatVnd(Number(payload[0].value ?? 0))} />
    </TooltipCard>
  );
}

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
                layout="vertical" margin={{ top: 4, right: 48, left: 8, bottom: 4 }} barCategoryGap={12}>
                <CartesianGrid horizontal={false} stroke={chart.grid} />
                <XAxis type="number" hide />
                <YAxis dataKey="groupKey" type="category" width={90} {...axisProps} tick={{ fill: chart.inkSoft, fontSize: 12 }} />
                <Tooltip content={<CommissionTooltip />} cursor={{ fill: 'rgba(37,99,235,0.06)' }} />
                <Bar dataKey="commission" fill={chart.primary} radius={[0,6,6,0]} maxBarSize={26}>
                  <LabelList dataKey="commission" position="right" offset={8}
                    formatter={(v: unknown) => fmtK(Number(v ?? 0))}
                    style={{ fill: chart.inkSoft, fontSize: 12, fontWeight: 600 }} />
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
        </Panel>
        <Panel title="Hoa hồng theo ngày">
          {byDay.length === 0 ? <EmptyChart /> : (
            <ResponsiveContainer width="100%" height={260}>
              <AreaChart data={byDay} margin={{ top: 12, right: 16, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="commFill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={chart.primary} stopOpacity={0.28} />
                    <stop offset="100%" stopColor={chart.primary} stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid {...gridProps} />
                <XAxis dataKey="groupKey" tickFormatter={d => d.slice(5)} {...axisProps} />
                <YAxis tickFormatter={fmtK} width={44} {...axisProps} />
                <Tooltip content={<CommissionTooltip />} cursor={{ stroke: chart.inkMuted, strokeDasharray: '4 4' }} />
                <Area type="monotone" dataKey="commission" stroke={chart.primary} strokeWidth={2.5}
                  fill="url(#commFill)" dot={false} activeDot={{ r: 5, strokeWidth: 2, stroke: chart.surface }} />
              </AreaChart>
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
