export function RetryButton({ onRetry }: { onRetry: () => void }) {
  return (
    <button
      type="button"
      onClick={onRetry}
      className="h-10 rounded-pill bg-primary px-4 text-sm text-primary-foreground"
    >
      重試
    </button>
  )
}
