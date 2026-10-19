import { useTheme } from '../../theme/ThemeContext'

// 현재 테마의 "반대" 모양을 보여주는 토글 버튼(다크일 땐 ☀️ → 라이트로, 라이트일 땐 🌙 → 다크로).
function ThemeToggle({ className = '' }) {
  const { theme, toggleTheme } = useTheme()
  const isDark = theme === 'dark'
  const label = isDark ? '라이트 모드로 전환' : '다크 모드로 전환'

  return (
    <button
      type="button"
      onClick={toggleTheme}
      aria-label={label}
      title={label}
      className={`text-xl leading-none text-gray-300 hover:text-white ${className}`}
    >
      {isDark ? '☀️' : '🌙'}
    </button>
  )
}

export default ThemeToggle
