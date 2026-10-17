import { Bar, BarChart, Tooltip, XAxis, YAxis } from 'recharts'
import type { BucketJson } from '../api/types'
import { formatCount } from '../format'
import { ACCENT } from './colors'

export default function PriceHistogram({ buckets, width }: { buckets: BucketJson[]; width: number }) {
  const data = buckets.map((bucket) => ({
    label: `${Math.round(bucket.from)}–${Math.round(bucket.to)}`,
    count: bucket.count,
  }))
  return (
    <BarChart width={width} height={220} data={data} margin={{ top: 8, right: 8, left: 0, bottom: 8 }}>
      <XAxis dataKey="label" tick={{ fontSize: 11 }} interval={Math.max(0, Math.floor(data.length / 5) - 1)} />
      <YAxis tick={{ fontSize: 11 }} width={52} tickFormatter={(value: number) => formatCount(value)} />
      <Tooltip
        formatter={(value) => [formatCount(Number(value)), 'itineraries']}
        labelFormatter={(label) => `${String(label)} €`}
      />
      <Bar dataKey="count" fill={ACCENT} radius={[3, 3, 0, 0]} isAnimationActive={false} />
    </BarChart>
  )
}
