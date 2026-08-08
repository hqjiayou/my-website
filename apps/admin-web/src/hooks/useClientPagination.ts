import { useMemo, useState } from 'react'

export function useClientPagination<T>(items: T[], pageSize = 20) {
  const [page, setPage] = useState(1)

  const total = items.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.min(page, totalPages)

  const pageItems = useMemo(() => {
    const start = (safePage - 1) * pageSize
    return items.slice(start, start + pageSize)
  }, [items, pageSize, safePage])

  function resetPage() {
    setPage(1)
  }

  return {
    page: safePage,
    pageSize,
    total,
    pageItems,
    setPage,
    resetPage,
    pagination:
      total > pageSize
        ? {
            page: safePage,
            pageSize,
            total,
            onPageChange: setPage,
          }
        : undefined,
  }
}
