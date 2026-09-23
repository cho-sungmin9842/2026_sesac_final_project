import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'

const NAV_LINKS = [
  { to: '/', label: '홈' },
  { to: '/booking', label: '예매' },
  { to: '/download', label: '다운로드' },
  { to: '/ai-chat', label: 'AI 추천' },
]

function Header() {
  const navigate = useNavigate()
  const { user, logout } = useAuth()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const handleSearchSubmit = (event) => {
    event.preventDefault()
    const query = new FormData(event.currentTarget).get('query')
    navigate(`/movies?query=${encodeURIComponent(query ?? '')}`)
  }

  return (
    <header className="sticky top-0 z-20 flex items-center gap-6 border-b border-white/5 bg-slate-950 px-6 py-3">
      <NavLink to="/" className="flex items-center gap-2 text-lg font-bold text-white">
        <span className="flex h-7 w-7 items-center justify-center rounded-md bg-gradient-to-br from-indigo-500 to-purple-500">
          🎬
        </span>
        SESAC MOVIE
      </NavLink>

      <nav className="flex items-center gap-5 text-sm font-medium text-gray-300">
        {NAV_LINKS.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            className={({ isActive }) =>
              isActive ? 'text-white' : 'text-gray-400 hover:text-white'
            }
          >
            {link.label}
          </NavLink>
        ))}
      </nav>

      <form onSubmit={handleSearchSubmit} className="ml-4 flex-1">
        <div className="flex max-w-xl items-center gap-2 rounded-lg bg-slate-900 px-3 py-2 text-sm text-gray-400">
          <span>🔍</span>
          <input
            name="query"
            type="text"
            placeholder="영화, 배우, 감독 검색"
            className="w-full bg-transparent text-gray-100 placeholder:text-gray-500 focus:outline-none"
          />
        </div>
      </form>

      <div className="flex items-center gap-4">
        <button type="button" aria-label="알림" className="text-gray-300 hover:text-white">
          🔔
        </button>
        <span className="text-sm text-gray-400">{user?.nickname}</span>
        <NavLink to="/mypage" aria-label="마이페이지">
          <span className="block h-8 w-8 rounded-full bg-gradient-to-br from-orange-400 to-red-500" />
        </NavLink>
        <button type="button" onClick={handleLogout} className="text-xs text-gray-400 hover:text-white">
          로그아웃
        </button>
      </div>
    </header>
  )
}

export default Header
