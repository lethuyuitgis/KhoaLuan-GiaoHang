import {
  Bar, BarChart, CartesianGrid, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import type { TooltipContentProps } from 'recharts';
import type { TopShipperRow } from '@shop/shared';
import { axisProps, chart, TooltipCard, TooltipRow } from './chartTheme';

function ShipperTooltip({ active, payload, label }: Partial<TooltipContentProps<number, string>>) {
  if (!active || !payload?.length) return null;
  return (
    <TooltipCard title={String(label)}>
      <TooltipRow color={chart.primary} label="Đã giao" value={`${payload[0].value} đơn`} />
    </TooltipCard>
  );
}

export function TopShippersChart({ data }: { data: TopShipperRow[] }) {
  const rows = data.map(r => ({
    name: r.name.length > 18 ? r.name.slice(0, 17) + '…' : r.name,
    deliveredCount: r.deliveredCount,
  }));

  return (
    <div className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={rows} layout="vertical" margin={{ top: 4, right: 40, left: 0, bottom: 4 }} barCategoryGap={12}>
          {/* Lưới dọc thưa cho bar ngang; ẩn trục số (giá trị đã ghi ở đầu thanh) */}
          <CartesianGrid horizontal={false} stroke={chart.grid} />
          <XAxis type="number" hide allowDecimals={false} />
          <YAxis dataKey="name" type="category" width={148} {...axisProps} tick={{ fill: chart.inkSoft, fontSize: 12 }} />
          <Tooltip content={<ShipperTooltip />} cursor={{ fill: 'rgba(37,99,235,0.06)' }} />
          <Bar dataKey="deliveredCount" fill={chart.primary} radius={[0, 6, 6, 0]} maxBarSize={26}>
            <LabelList
              dataKey="deliveredCount"
              position="right"
              offset={8}
              style={{ fill: chart.inkSoft, fontSize: 12, fontWeight: 600 }}
            />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
