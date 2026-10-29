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
    <button
      type="button"
      onClick={() => void handleCopy()}
      className={cn(
        'inline-flex max-w-full items-center gap-1.5 rounded-md text-left text-admin transition hover:bg-admin-surface-muted',
        className,
      )}
      title={t('actions.copy')}
      aria-label={`${t('actions.copy')} ${value}`}
    >
      <span className="min-w-0 break-all font-medium">{value}</span>
      {copied ? (
        <Check className="h-3.5 w-3.5 shrink-0 text-emerald-600" />
      ) : (
        <Copy className="h-3.5 w-3.5 shrink-0 text-admin-muted" />
      )}
    </button>
  )
}
