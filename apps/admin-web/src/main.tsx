import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'sonner'

import '@/i18n'
import '@/stores/adminPreferencesStore'
import '@/assets/styles/globals.css'
import { createAppQueryClient } from '@/utils/http/queryClient'
import App from './App.tsx'

const queryClient = createAppQueryClient()

// data router 仅用于启用 useBlocker（未保存离开保护）；路由表仍是 router/routes 里的 <Routes>，
// 由单个 splat 路由整体承接，守卫与权限逻辑不变。
const router = createBrowserRouter([
  {
    path: '*',
    element: (
      <>
        <App />
        <Toaster position="top-right" richColors />
      </>
    ),
  },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)
