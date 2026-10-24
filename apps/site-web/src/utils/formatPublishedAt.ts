export function formatPublishedAt(value: string | null): string {
  if (!value) return '未標明發佈時間'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-Hant-TW', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}
