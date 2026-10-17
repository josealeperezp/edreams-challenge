import { Bar, BarChart, Tooltip, XAxis, YAxis } from 'recharts'
import type { StatsJson } from '../api/types'
import { formatCount, formatMoney } from '../format'
import { ACCENT } from './colors'

export default function CarrierBars({ byCarrier, width }: { byCarrier: StatsJson['byCarrier']; width: number }) {
  const data = byCarrier.map((entry) => ({ carrier: entry.carrier, count: entry.count, minPrice: entry.minPrice }))
  return (
    <BarChart width={width} height={Math.max(220, 22 * data.length + 40)} data={data} layout="vertical"
      margin={{ top: 8, right: 16, left: 0, bottom: 8 }}>
      <XAxis type="number" tick={{ fontSize: 11 }} tickFormatter={(value: number) => formatCount(value)} />
      <YAxis type="category" dataKey="carrier" width={44} tick={{ fontSize: 11 }} />
      <Tooltip
        formatter={(value, _name, item) => {
          const minPrice = (item as { payload?: { minPrice?: number } }).payload?.minPrice
          const from = minPrice === undefined ? '' : ` · from ${formatMoney(minPrice)}`
          return [`${formatCount(Number(value))}${from}`, 'itineraries']
        }}
      />
      <Bar dataKey="count" fill={ACCENT} radius={[0, 3, 3, 0]} isAnimationActive={false} />
    </BarChart>
  )
}
