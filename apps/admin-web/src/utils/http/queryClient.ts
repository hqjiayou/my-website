import { QueryClient } from '@tanstack/react-query'

import { handleApiError } from './handleApiError'

export function createAppQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: 1,
      },
      mutations: {
        onError: (error) => handleApiError(error),
      },
    },
  })
}
