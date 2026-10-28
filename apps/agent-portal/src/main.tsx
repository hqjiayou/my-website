import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'

import App from '@/App'
import '@/assets/styles/globals.css'

// 深色模式在首幀前定版，避免閃白：用戶存過的選擇優先，否則跟隨系統。
try {
  const saved = window.localStorage.getItem('portal.theme')
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
  if (saved === 'dark' || (saved !== 'light' && prefersDark)) {
    document.documentElement.classList.add('dark')
  }
} catch {
  // 讀不到就用淺色，不擋啟動。
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)

if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    void navigator.serviceWorker.register('/sw.js')
  })
}
