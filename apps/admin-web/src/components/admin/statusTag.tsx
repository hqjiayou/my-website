import { cva, type VariantProps } from 'class-variance-authority'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/cn'
import { ROLE_STATUS } from '@/types/rbac'

const statusTagVariants = cva('inline-flex rounded-md border px-2 py-0.5 text-xs font-medium', {
  variants: {
    tone: {
      active: 'border-emerald-200 bg-emerald-50 text-emerald-700',
      disabled: 'border-slate-200 bg-slate-50 text-slate-600',
      warning: 'border-amber-200 bg-amber-50 text-amber-700',
      neutral: 'border-slate-200 bg-slate-50 text-slate-600',
    },
  },
  defaultVariants: {
    tone: 'neutral',
  },
})

type StatusTagVariantProps = VariantProps<typeof statusTagVariants>

interface StatusTagProps extends StatusTagVariantProps {
  status: string
  label?: string
  className?: string
}

function resolveTone(status: string): StatusTagVariantProps['tone'] {
  if (status === ROLE_STATUS.ACTIVE || status === 'ACTIVE') return 'active'
  if (status === ROLE_STATUS.DISABLED || status === 'DISABLED') return 'disabled'
  return 'neutral'
}

export function StatusTag({ status, label, tone, className }: StatusTagProps) {
  const { t } = useTranslation('common')
  const resolvedTone = tone ?? resolveTone(status)
  const displayLabel =
    label ??
    t(`enum.status.${status}`, {
      defaultValue: status,
    })

  return <span className={cn(statusTagVariants({ tone: resolvedTone }), className)}>{displayLabel}</span>
}
