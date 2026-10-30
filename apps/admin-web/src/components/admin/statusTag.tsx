import { cva, type VariantProps } from 'class-variance-authority'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/cn'
import { ROLE_STATUS } from '@/types/rbac'

const statusTagVariants = cva('admin-status-tag inline-flex items-center gap-1.5 rounded-full px-2 text-xs font-medium', {
  variants: {
    tone: {
      active: 'bg-admin-success-muted text-admin-success',
      disabled: 'bg-admin-surface-muted text-admin-muted',
      warning: 'bg-admin-warning-muted text-admin-warning',
      neutral: 'bg-admin-surface-muted text-admin-muted',
      danger: 'bg-admin-danger-muted text-admin-danger',
      info: 'bg-admin-info-muted text-admin-info',
    },
  },
  defaultVariants: {
    tone: 'neutral',
  },
})

const statusDotClass = {
  active: 'bg-admin-success',
  disabled: 'bg-admin-muted-foreground',
  warning: 'bg-admin-warning',
  neutral: 'bg-admin-muted-foreground',
  danger: 'bg-admin-danger',
  info: 'bg-admin-info',
} as const

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
  const dot = statusDotClass[resolvedTone ?? 'neutral']

  return (
    <span className={cn(statusTagVariants({ tone: resolvedTone }), className)}>
      <span className={cn('h-1.5 w-1.5 shrink-0 rounded-full', dot)} />
      {displayLabel}
    </span>
  )
}
