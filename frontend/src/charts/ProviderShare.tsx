import { Cell, Legend, Pie, PieChart, Tooltip } from 'recharts'
import type { StatsJson } from '../api/types'
import { formatCount } from '../format'
import { PROVIDER_COLORS } from './colors'

/** Who won the cheapest offer across the filtered inventory: the aggregation rule as a share. */
export default function ProviderShare({ byProvider, width }: { byProvider: StatsJson['byProvider']; width: number }) {
  const data = byProvider.map((entry) => ({ name: entry.provider, value: entry.count }))
  return (
    <PieChart width={width} height={220}>
      <Pie data={data} dataKey="value" nameKey="name" innerRadius={48} outerRadius={78} paddingAngle={2}
        isAnimationActive={false}>
        {data.map((entry) => (
          <Cell key={entry.name} fill={PROVIDER_COLORS[entry.name]} />
        ))}
      </Pie>
      <Tooltip formatter={(value) => [formatCount(Number(value)), 'cheapest for']} />
      <Legend />
    </PieChart>
  )
}
