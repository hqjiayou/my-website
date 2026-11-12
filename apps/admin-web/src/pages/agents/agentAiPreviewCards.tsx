import type { DataMessagePartComponent } from '@assistant-ui/react'
import { Droplets } from 'lucide-react'
import { Suspense, lazy } from 'react'
import { useTranslation } from 'react-i18next'

import type { AiChartCardPayload, AiVisualizationEnvelope, AiWeatherCardPayload } from '@/types/ai'

/** Recharts 实现单独分片：只有消息流里真出现 chart 卡时才加载（Issue #179）。 */
const ChartCardBody = lazy(() => import('./agentAiPreviewChart'))

/** 数值呈现：整数不带小数，非整数一位小数（与执行层 wire 口径一致，不做四舍五入造数）。 */
function formatValue(value: number): string {
  const rounded = Math.round(value * 10) / 10
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1)
}

/** payload 的 time/date 已是当地时刻字符串：只截取呈现，不做时区换算。 */
function clockOf(time: string): string {
  return time.length >= 16 ? time.slice(11, 16) : time
}

function hourOf(time: string): string {
  return time.length >= 13 ? time.slice(11, 13) : time
}

function dayOf(date: string): string {
  return date.length >= 10 ? date.slice(5, 10) : date
}

/**
 * 天气卡（Issue #179）：数据全部来自 get_weather 的结构化 payload，不经模型。
 * 逐小时走势用纯 CSS 条（24 根、按本段高低温归一），不为迷你图拉 Recharts；
 * 缺字段不显示对应项。weatherText 是 Python 按 WMO 码表给的文本，原样显示。
 */
function WeatherCard({ payload, title }: { payload: AiWeatherCardPayload; title?: string }) {
  const { t } = useTranslation('sites')
  const current = payload.current
  if (!current || typeof current.time !== 'string') return null
  const hourly = Array.isArray(payload.hourly) ? payload.hourly : []
  const daily = Array.isArray(payload.daily) ? payload.daily : []

  const facts: Array<{ label: string; value: string }> = []
  if (current.apparentTemperatureC != null) {
    facts.push({
      label: t('aiWorkspace.preview.cards.weather.feelsLike'),
      value: `${formatValue(current.apparentTemperatureC)}°C`,
    })
  }
  if (current.humidityPercent != null) {
    facts.push({
      label: t('aiWorkspace.preview.cards.weather.humidity'),
      value: `${formatValue(current.humidityPercent)}%`,
    })
  }
  if (current.windSpeedKmh != null) {
    facts.push({
      label: t('aiWorkspace.preview.cards.weather.wind'),
      value: `${formatValue(current.windSpeedKmh)} km/h`,
    })
  }
  if (current.precipitationMm != null) {
    facts.push({
      label: t('aiWorkspace.preview.cards.weather.precipitation'),
      value: `${formatValue(current.precipitationMm)} mm`,
    })
  }

  const hourlyTemps = hourly.map((point) => point.temperatureC)
  const hourlyMin = hourlyTemps.length ? Math.min(...hourlyTemps) : 0
  const hourlySpan = hourlyTemps.length ? Math.max(...hourlyTemps) - hourlyMin || 1 : 1

  return (
    <div className="my-2 w-full max-w-md rounded-xl border border-admin bg-admin-surface p-3.5 text-admin">
      {title ? <p className="mb-2 text-xs font-medium text-admin-muted">{title}</p> : null}
      <div className="flex items-start gap-2">
        <span className="text-3xl font-semibold leading-none">
          {current.temperatureC != null ? formatValue(current.temperatureC) : '—'}
        </span>
        <span className="text-sm leading-5 text-admin-muted">°C</span>
        <div className="min-w-0">
          {current.weatherText ? <p className="truncate text-sm leading-5">{current.weatherText}</p> : null}
          <p className="font-mono text-xs leading-5 text-admin-muted">{clockOf(current.time)}</p>
        </div>
      </div>
      {facts.length ? (
        <dl className="mt-2.5 flex flex-wrap gap-x-4 gap-y-1">
          {facts.map((fact) => (
            <div key={fact.label} className="flex items-baseline gap-1 text-xs">
              <dt className="text-admin-muted">{fact.label}</dt>
              <dd className="font-medium">{fact.value}</dd>
            </div>
          ))}
        </dl>
      ) : null}
      {hourly.length ? (
        <div className="mt-3 border-t border-admin pt-2.5">
          <p className="text-xs text-admin-muted">{t('aiWorkspace.preview.cards.weather.hourly')}</p>
          <div className="mt-1.5 flex items-stretch gap-[3px]">
            {hourly.map((point, index) => {
              const heightPercent = 22 + ((point.temperatureC - hourlyMin) / hourlySpan) * 78
              const tooltip =
                `${clockOf(point.time)} · ${formatValue(point.temperatureC)}°C` +
                (point.precipitationProbabilityPercent != null
                  ? ` · ${t('aiWorkspace.preview.cards.weather.precipitationChance')} ${formatValue(point.precipitationProbabilityPercent)}%`
                  : '')
              return (
                <div key={`${point.time}-${index}`} className="flex min-w-0 flex-1 flex-col items-center" title={tooltip}>
                  <span className="mb-1 h-4 text-xs leading-4 text-admin-muted">
                    {index % 6 === 0 ? formatValue(point.temperatureC) : ''}
                  </span>
                  <div className="flex h-12 w-full items-end">
                    <div
                      className="w-full rounded-sm bg-chart-1/75"
                      style={{ height: `${heightPercent}%` }}
                    />
                  </div>
                  <span className="mt-1 h-4 font-mono text-xs leading-4 text-admin-muted">
                    {index % 6 === 0 ? hourOf(point.time) : ''}
                  </span>
                </div>
              )
            })}
          </div>
        </div>
      ) : null}
      {daily.length ? (
        <ul className="mt-3 space-y-1.5 border-t border-admin pt-2.5">
          {daily.map((day) => (
            <li key={day.date} className="flex items-center gap-2 text-xs">
              <span className="w-11 shrink-0 font-mono text-admin-muted">{dayOf(day.date)}</span>
              <span className="min-w-0 flex-1 truncate">{day.weatherText ?? ''}</span>
              {day.precipitationProbabilityMaxPercent != null ? (
                <span className="flex shrink-0 items-center gap-0.5 text-info-emphasis">
                  <Droplets className="h-3 w-3" aria-hidden />
                  {formatValue(day.precipitationProbabilityMaxPercent)}%
                </span>
              ) : null}
              <span className="shrink-0 font-mono">
                {formatValue(day.temperatureMaxC)}° / {formatValue(day.temperatureMinC)}°
              </span>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}

/** 图表卡外壳：标题取信封 title，图体懒加载；payload 形状不对直接不渲染。 */
function ChartCard({ title, payload }: { title?: string; payload: AiChartCardPayload }) {
  if (!payload || !Array.isArray(payload.series) || payload.series.length === 0) return null
  return (
    <div className="my-2 w-full max-w-lg rounded-xl border border-admin bg-admin-surface p-3.5">
      {title ? <p className="mb-2 text-sm font-medium text-admin">{title}</p> : null}
      <Suspense fallback={null}>
        <ChartCardBody payload={payload} />
      </Suspense>
    </div>
  )
}

/**
 * VISUALIZATION data part 的分发组件（Issue #179，替换 #178 占位）：
 * adapter 已做信封校验，这里再按 kind 兜底分发，未知 kind / 形状不对返回 null。
 */
export const PreviewVisualizationPart: DataMessagePartComponent = ({ data }) => {
  const envelope = data as AiVisualizationEnvelope | undefined
  if (!envelope || typeof envelope !== 'object') return null
  if (envelope.kind === 'weather') {
    return <WeatherCard payload={envelope.payload} title={envelope.title} />
  }
  if (envelope.kind === 'chart') {
    return <ChartCard payload={envelope.payload} title={envelope.title} />
  }
  return null
}
