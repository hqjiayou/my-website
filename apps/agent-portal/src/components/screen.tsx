import type { ReactNode } from 'react'

export function Screen({ children }: { children: ReactNode }) {
  return <div className="mx-auto flex min-h-dvh w-full max-w-lg flex-col bg-gpt-canvas text-gpt-ink">{children}</div>
}

export function FullScreenStatus({ text }: { text: string }) {
  return (
    <Screen>
      <div className="safe-top safe-x safe-bottom flex flex-1 items-center justify-center px-6 text-sm text-gpt-muted">
        {text}
      </div>
    </Screen>
  )
}
