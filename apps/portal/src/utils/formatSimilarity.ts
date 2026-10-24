/** 檢索分數是 0–1 的相似度，畫面上收成百分比。超出區間的值夾到 0%–100%。 */
export function formatSimilarityPercent(score: number): string {
  if (!Number.isFinite(score)) return '—'
  const percent = Math.round(score * 100)
  return `${Math.min(100, Math.max(0, percent))}%`
}
