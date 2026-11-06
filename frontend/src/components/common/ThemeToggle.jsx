import { useTheme } from '../../theme/ThemeContext'

// 다크/라이트 전환 스위치(토글 버튼). 다크일 땐 손잡이가 오른쪽(🌙), 라이트일 땐 왼쪽(☀️)에 있습니다.
// 색은 라이트 테마의 팔레트 반전(index.css)에 영향을 받지 않도록 고정 색(hex)을 씁니다.
function ThemeToggle({ className = '' }) {
  const { theme, toggleTheme } = useTheme()
  const isDark = theme === 'dark'
  const label = isDark ? '다크 모드 (눌러서 라이트 모드로 전환)' : '라이트 모드 (눌러서 다크 모드로 전환)'

  return (
    <button
      type="button"
      role="switch"
      aria-checked={isDark}
      aria-label="다크 모드"
      title={label}
      onClick={toggleTheme}
      className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full p-0.5 transition-colors duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400 focus-visible:ring-offset-1 ${
        isDark ? 'bg-[#4f46e5]' : 'bg-[#fbbf24]'
      } ${className}`}
    >
      <span
        className={`flex h-5 w-5 items-center justify-center rounded-full bg-white shadow transition-transform duration-200 ${
          isDark ? 'translate-x-5' : 'translate-x-0'
        }`}
      >
        {isDark ? (
          // 달
          <svg viewBox="0 0 24 24" className="h-3 w-3" fill="#4f46e5" aria-hidden="true">
            <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
          </svg>
        ) : (
          // 해
          <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="#f59e0b" strokeWidth="2.2" strokeLinecap="round" aria-hidden="true">
            <circle cx="12" cy="12" r="4" fill="#f59e0b" />
            <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
          </svg>
        )}
      </span>
    </button>
  )
}

export default ThemeToggle
