import type { ReactNode } from 'react'

/** 壳内页面的内容列：居中、限宽、可纵向滚动由页面自己决定。 */
export function Screen({ children }: { children: ReactNode }) {
  return <div className="mx-auto flex min-h-0 w-full max-w-3xl flex-1 flex-col">{children}</div>
}

export function FullScreenStatus({ text }: { text: string }) {
  return (
    <div className="flex min-h-dvh w-full flex-col bg-background text-gpt-ink">
      <div className="safe-top safe-x safe-bottom flex flex-1 items-center justify-center px-6 text-sm text-gpt-muted">
        {text}
      </div>
    </div>
  )
}
