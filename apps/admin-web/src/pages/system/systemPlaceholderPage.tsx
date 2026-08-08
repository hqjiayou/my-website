import { PageHeader } from '@/components/page'

interface SystemPlaceholderPageProps {
  title: string
}

export function SystemPlaceholderPage({ title }: SystemPlaceholderPageProps) {
  return (
    <section>
      <PageHeader title={title} description="本轮为占位页，完整功能后续实现。" />
      <div className="rounded-xl border border-dashed border-slate-300 bg-white p-6 text-sm text-slate-600">
        功能开发中
      </div>
    </section>
  )
}
