import { Loader2 } from 'lucide-react'

import { cn } from '@/lib/cn'

type SpinnerProps = {
  /** 图标边长（px），默认 16（DS §7 按钮 loading 定案）。 */
  size?: number
  className?: string
}

/** 提交/确认按钮的 loading 指示：pending 时与按钮文字并排显示，文字保留（DS §7）。 */
export function Spinner({ size = 16, className }: SpinnerProps) {
  return <Loader2 aria-hidden style={{ width: size, height: size }} className={cn('animate-spin', className)} />
}
