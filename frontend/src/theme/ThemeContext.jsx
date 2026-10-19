import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'

// 사용자가 고른 테마(dark | light)를 브라우저(localStorage)에 기억해 두고, <html data-theme="..."> 로 반영합니다.
// 아직 고른 적이 없으면 기존 화면(다크)을 그대로 보여줍니다.
// index.html 에서 같은 키를 먼저 읽어 첫 화면이 깜빡이지 않게 하므로, 키 이름은 거기와 맞춰야 합니다.
const STORAGE_KEY = 'moviepick_theme'
const ThemeContext = createContext(null)

function readStoredTheme() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    return saved === 'light' ? 'light' : 'dark'
  } catch {
    return 'dark'
  }
}

export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(readStoredTheme)

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    try {
      localStorage.setItem(STORAGE_KEY, theme)
    } catch {
      // 저장소를 못 쓰는 환경(시크릿 모드 등)에서는 이번 방문에만 적용됩니다.
    }
  }, [theme])

  const toggleTheme = useCallback(() => setTheme((current) => (current === 'dark' ? 'light' : 'dark')), [])
  const value = useMemo(() => ({ theme, toggleTheme }), [theme, toggleTheme])

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme() {
  const context = useContext(ThemeContext)
  if (!context) {
    throw new Error('useTheme는 ThemeProvider 안에서만 사용할 수 있습니다.')
  }
  return context
}
