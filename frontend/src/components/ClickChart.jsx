import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from 'recharts'

function fmtXDate(dateStr) {
  // dateStr is "2026-05-07" — avoid Date constructor timezone issues by parsing manually
  const [, m, d] = dateStr.split('-')
  const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec']
  return `${months[parseInt(m, 10) - 1]} ${parseInt(d, 10)}`
}

function CustomTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null
  return (
    <div style={{
      backgroundColor: '#141416', border: '1px solid #262629',
      borderRadius: 8, color: '#f5f5f7', padding: '0.5rem 0.75rem', fontSize: '0.875rem'
    }}>
      <div style={{ fontWeight: 600 }}>{label}</div>
      <div>{payload[0].value} click{payload[0].value !== 1 ? 's' : ''}</div>
    </div>
  )
}

export default function ClickChart({ dailyClicks }) {
  const allZero = dailyClicks.every(d => d.count === 0)

  if (allZero) {
    return (
      <div className="d-flex align-items-center justify-content-center text-muted"
        style={{ height: 300 }}>
        No clicks yet
      </div>
    )
  }

  const data = dailyClicks.map(d => ({ date: fmtXDate(d.date), count: d.count }))

  return (
    <ResponsiveContainer width="100%" height={300}>
      <LineChart data={data} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#262629" />
        <XAxis
          dataKey="date"
          tick={{ fontSize: 11, fill: '#a1a1aa' }}
          interval={4}
          tickLine={false}
          axisLine={{ stroke: '#262629' }}
        />
        <YAxis
          allowDecimals={false}
          tick={{ fontSize: 11, fill: '#a1a1aa' }}
          tickLine={false}
          axisLine={false}
          width={32}
        />
        <Tooltip content={<CustomTooltip />} />
        <Line
          type="monotone"
          dataKey="count"
          stroke="#00ff88"
          strokeWidth={2}
          dot={{ fill: '#00ff88', r: 3 }}
          activeDot={{ r: 5, fill: '#00ff88' }}
        />
      </LineChart>
    </ResponsiveContainer>
  )
}
