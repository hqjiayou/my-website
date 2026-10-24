import type { ReactNode } from 'react'

export function Screen({ children }: { children: ReactNode }) {
  return <div className="mx-auto flex min-h-dvh w-full max-w-lg flex-col bg-background text-foreground">{children}</div>
}

export function FullScreenStatus({ text, children }: { text: string; children?: ReactNode }) {
  return (
    <Screen>
      <div className="safe-top safe-x safe-bottom flex flex-1 flex-col items-center justify-center gap-stack px-6 text-center">
        <p className="text-sm leading-6 text-muted-foreground">{text}</p>
        {children}
      </div>
    </Screen>
  )
}
