interface StatCardProps {
  label: string
  value: string
  trend: string
}

export function StatCard({ label, value, trend }: StatCardProps) {
  const isPositive = trend.startsWith('+') || trend.includes('+')

  return (
    <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <p className="text-sm text-slate-500">{label}</p>
      <p className="mt-2 text-3xl font-semibold text-slate-900">{value}</p>
      <p className={isPositive ? 'mt-2 text-sm text-emerald-600' : 'mt-2 text-sm text-rose-600'}>{trend}</p>
    </article>
  )
}
