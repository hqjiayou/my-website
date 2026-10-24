import { Screen } from '@/components/screen'

export function GuidePage() {
  return (
    <Screen>
      <main className="safe-top safe-x safe-bottom flex flex-1 flex-col justify-center px-page">
        <h1 className="text-3xl font-semibold tracking-tight">站點</h1>
        <p className="mt-3 text-sm leading-6 text-muted-foreground">請在網址後加上站點代碼，例如 /demo</p>
      </main>
    </Screen>
  )
}
