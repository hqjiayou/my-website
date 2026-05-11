import { useEffect, useState } from 'react'
import { resolveInitialLocale } from './i18n/locale'
import { HomePage } from './pages/home/HomePage'
import { applyTheme, resolveInitialTheme } from './theme/theme'
import './App.css'

function App() {
  const [locale, setLocale] = useState(resolveInitialLocale)
  const [theme, setTheme] = useState(resolveInitialTheme)

  useEffect(() => {
    applyTheme(theme)
  }, [theme])

  return <HomePage locale={locale} onLocaleChange={setLocale} theme={theme} onThemeChange={setTheme} />
}

export default App
