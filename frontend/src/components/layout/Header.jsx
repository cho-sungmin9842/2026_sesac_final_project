import { useEffect, useState } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'

const NAV_LINKS = [
  { to: '/', label: '홈' },
  { to: '/booking', label: '예매' },
  { to: '/download', label: '다운로드' },
  { to: '/ai-chat', label: 'AI 추천' },
]

// value는 KMDB 검색 API의 실제 파라미터명(title/actor/director)과 동일하게 맞춥니다.
const SEARCH_TYPES = [
  { value: 'title', label: '영화', particle: '를' },
  { value: 'actor', label: '배우', particle: '를' },
  { value: 'director', label: '감독', particle: '을' },
  { value: 'keyword', label: '키워드', particle: '를' },
]

function Header() {
  const navigate = useNavigate()
  const location = useLocation()
  const { user, logout } = useAuth()
  const [searchType, setSearchType] = useState(SEARCH_TYPES[0].value)
  const [searchText, setSearchText] = useState('')
  const selectedSearchType = SEARCH_TYPES.find((type) => type.value === searchType) ?? SEARCH_TYPES[0]

  // Header는 라우트가 바뀌어도 다시 마운트되지 않아서(레이아웃에 고정), 검색 결과 화면(/movies)을 벗어나면
  // 드롭박스/입력창을 기본값(영화)으로 되돌립니다. 새로고침은 컴포넌트가 새로 마운트되니 항상 기본값이고,
  // "뒤로 가기"로 /movies를 벗어났을 때도 이 로직으로 같은 기본값을 보게 됩니다.
  useEffect(() => {
    if (location.pathname !== '/movies') {
      setSearchText('')
      setSearchType(SEARCH_TYPES[0].value)
    }
  }, [location.pathname])

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const handleSearchSubmit = (event) => {
    event.preventDefault()
    navigate(`/movies?query=${encodeURIComponent(searchText)}&field=${searchType}`)
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

      <form onSubmit={handleSearchSubmit} className="ml-4 flex max-w-xl flex-1 items-center gap-2">
        <select
          value={searchType}
          onChange={(event) => setSearchType(event.target.value)}
          className="rounded-lg bg-slate-900 px-3 py-2 text-sm text-gray-300 focus:outline-none"
        >
          {SEARCH_TYPES.map((type) => (
            <option key={type.value} value={type.value}>
              {type.label}
            </option>
          ))}
        </select>
        <div className="flex flex-1 items-center gap-2 rounded-lg bg-slate-900 px-3 py-2 text-sm text-gray-400">
          <span>🔍</span>
          <input
            name="query"
            type="text"
            value={searchText}
            onChange={(event) => setSearchText(event.target.value)}
            autoComplete="off"
            placeholder={`${selectedSearchType.label}${selectedSearchType.particle} 입력해주세요`}
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
