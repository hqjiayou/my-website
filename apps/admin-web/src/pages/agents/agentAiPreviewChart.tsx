import { useState } from 'react'
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

import type { AiChartCardPayload } from '@/types/ai'

/**
 * 图表色板（Issue #179）：Recharts 只收具体色值，从设计系统既有的主题变量
 * --chart-1..4（theme.css 语义色板，含深色档）读计算值，不在组件里散写 hex；
 * 读不到时回退 theme.css 浅色档原值。挂载时读一次，主题切换后新卡生效。
 */
const COLOR_FALLBACKS: Record<string, string> = {
  '--chart-1': '37 99 235',
  '--chart-2': '124 58 237',
  '--chart-3': '22 163 74',
  '--chart-4': '234 88 12',
  '--border': '226 232 240',
  '--muted-foreground': '100 116 139',
  '--card': '255 255 255',
  '--foreground': '15 23 42',
}

function readThemeColor(name: string): string {
  const fallback = `rgb(${COLOR_FALLBACKS[name]})`
  if (typeof window === 'undefined') return fallback
  const raw = window.getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return raw ? `rgb(${raw})` : fallback
}

interface ChartColors {
  series: string[]
  grid: string
  tick: string
  tooltipBackground: string
  tooltipBorder: string
  tooltipText: string
}

function useChartColors(): ChartColors {
  const [colors] = useState<ChartColors>(() => ({
    series: ['--chart-1', '--chart-2', '--chart-3', '--chart-4'].map(readThemeColor),
    grid: readThemeColor('--border'),
    tick: readThemeColor('--muted-foreground'),
    tooltipBackground: readThemeColor('--card'),
    tooltipBorder: readThemeColor('--border'),
    tooltipText: readThemeColor('--foreground'),
  }))
  return colors
}

type ChartRow = { label: string } & Record<string, string | number>

/** 多系列按 label 对齐成行：label 序取各系列首次出现序，缺的点留空（图上断开/缺柱）。 */
function toRows(payload: AiChartCardPayload): ChartRow[] {
  const labels: string[] = []
  const byLabel = new Map<string, ChartRow>()
  for (const series of payload.series) {
    for (const point of series.points) {
      let row = byLabel.get(point.label)
      if (!row) {
        row = { label: point.label }
        byLabel.set(point.label, row)
        labels.push(point.label)
      }
      row[series.name] = point.value
    }
  }
  return labels.flatMap((label) => {
    const row = byLabel.get(label)
    return row ? [row] : []
  })
}

/**
 * 图表卡主体（Recharts）：经 React.lazy 单独分片，只有点出 chart 卡时才加载。
 * pie 只吃单系列：多系列时取第一组（执行层契约允许 ≤4 系列，占比图多系列无
 * 统一口径），其余图种全系列渲染。
 */
export default function PreviewChartCardBody({ payload }: { payload: AiChartCardPayload }) {
  const colors = useChartColors()
  const tooltipStyle = {
    backgroundColor: colors.tooltipBackground,
    border: `1px solid ${colors.tooltipBorder}`,
    borderRadius: 8,
    color: colors.tooltipText,
    fontSize: 12,
  }
  const tickStyle = { fontSize: 11, fill: colors.tick }

  if (payload.type === 'pie') {
    const first = payload.series[0]
    if (!first) return null
    const data = first.points.map((point) => ({ name: point.label, value: point.value }))
    return (
      <ResponsiveContainer width="100%" height={220}>
        <PieChart>
          <Tooltip contentStyle={tooltipStyle} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          <Pie data={data} dataKey="value" nameKey="name" outerRadius={78} paddingAngle={1}>
            {data.map((point, index) => (
              <Cell key={point.name} fill={colors.series[index % colors.series.length]} />
            ))}
          </Pie>
        </PieChart>
      </ResponsiveContainer>
    )
  }

  const rows = toRows(payload)
  const multiSeries = payload.series.length > 1
  const axes = (
    <>
      <CartesianGrid stroke={colors.grid} strokeDasharray="3 3" vertical={false} />
      <XAxis dataKey="label" tick={tickStyle} tickLine={false} axisLine={{ stroke: colors.grid }} minTickGap={24} />
      <YAxis width={40} tick={tickStyle} tickLine={false} axisLine={false} />
      <Tooltip contentStyle={tooltipStyle} />
      {multiSeries ? <Legend wrapperStyle={{ fontSize: 12 }} /> : null}
    </>
  )

  if (payload.type === 'bar') {
    return (
      <ResponsiveContainer width="100%" height={220}>
        <BarChart data={rows} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
          {axes}
          {payload.series.map((series, index) => (
            <Bar
              key={series.name}
              dataKey={series.name}
              fill={colors.series[index % colors.series.length]}
              radius={[4, 4, 0, 0]}
              maxBarSize={36}
            />
          ))}
        </BarChart>
      </ResponsiveContainer>
    )
  }

  if (payload.type === 'area') {
    return (
      <ResponsiveContainer width="100%" height={220}>
        <AreaChart data={rows} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
          {axes}
          {payload.series.map((series, index) => {
            const color = colors.series[index % colors.series.length]
            return (
              <Area
                key={series.name}
                type="monotone"
                dataKey={series.name}
                stroke={color}
                fill={color}
                fillOpacity={0.16}
                strokeWidth={2}
                dot={false}
                activeDot={{ r: 3 }}
                connectNulls
              />
            )
          })}
        </AreaChart>
      </ResponsiveContainer>
    )
  }

  return (
    <ResponsiveContainer width="100%" height={220}>
      <LineChart data={rows} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
        {axes}
        {payload.series.map((series, index) => (
          <Line
            key={series.name}
            type="monotone"
            dataKey={series.name}
            stroke={colors.series[index % colors.series.length]}
            strokeWidth={2}
            dot={false}
            activeDot={{ r: 3 }}
            connectNulls
          />
        ))}
      </LineChart>
    </ResponsiveContainer>
  )
}
