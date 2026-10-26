import { useEffect } from 'react'

/** 鍵盤蓋住版面視埠時，把可用高度減去被遮住的部分。瀏覽器已經縮小版面視埠時，差值接近 0。 */
export function useKeyboardInset() {
  useEffect(() => {
    const root = document.documentElement
    const apply = () => {
      const viewport = window.visualViewport
      if (!viewport) {
        root.style.setProperty('--keyboard-inset', '0px')
        return
      }
      const inset = Math.max(0, window.innerHeight - viewport.height - viewport.offsetTop)
      root.style.setProperty('--keyboard-inset', `${Math.round(inset)}px`)
    }

    apply()
    window.visualViewport?.addEventListener('resize', apply)
    window.visualViewport?.addEventListener('scroll', apply)
    window.addEventListener('orientationchange', apply)
    return () => {
      window.visualViewport?.removeEventListener('resize', apply)
      window.visualViewport?.removeEventListener('scroll', apply)
      window.removeEventListener('orientationchange', apply)
      root.style.removeProperty('--keyboard-inset')
    }
  }, [])
}
