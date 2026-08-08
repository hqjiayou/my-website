import { Check, Copy } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { cn } from '@/lib/cn'

interface CopyableTextProps {
  value: string
  className?: string
}

export function CopyableText({ value, className }: CopyableTextProps) {
  const { t } = useTranslation('common')
  const [copied, setCopied] = useState(false)

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(value)
      setCopied(true)
      toast.success(t('actions.copied'))
      window.setTimeout(() => setCopied(false), 1500)
    } catch {
      toast.error(t('error.unknown'))
    }
  }

  return (
    <span className={cn('inline-flex items-center gap-1.5', className)}>
      <span className="font-medium text-slate-900">{value}</span>
      <button
        type="button"
        onClick={() => void handleCopy()}
        className="rounded p-0.5 text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
        title={t('actions.copy')}
        aria-label={t('actions.copy')}
      >
        {copied ? <Check className="h-3.5 w-3.5 text-emerald-600" /> : <Copy className="h-3.5 w-3.5" />}
      </button>
    </span>
  )
}
