import { useEffect, useRef, useState } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { getMovieSuggestions, getPersonSuggestions } from '../../api/movieApi'
import NotificationBell from './NotificationBell'
import ThemeToggle from '../common/ThemeToggle'
import VersionBadge from '../common/VersionBadge'

// 타이핑이 끝나고서야 요청하도록 이 시간만큼 기다립니다(그 전엔 이전 타이머를 취소) - 제목은 로컬
// 캐시라 거의 즉시 응답되지만, 배우/감독은 KMDB를 직접 조회해 조금 더 걸리므로 그만큼 여유 있게
// 잡아둡니다.
const SUGGESTION_DEBOUNCE_MS = 300

// 자동완성을 지원하는 검색 유형만 적어둡니다 - 키워드(줄거리/소재 태그)는 "이름"이나 "제목"처럼
// 후보를 뚜렷하게 보여줄 개념이 없어 자동완성 없이 일반 입력으로만 검색합니다(2026-10-18 요청).
const AUTOCOMPLETE_FIELDS = new Set(['title', 'actor', 'director'])

const NAV_LINKS = [
  { to: '/', label: '홈' },
  { to: '/booking', label: '예매' },
  { to: '/ai-chat', label: 'AI 챗봇' },
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
  const [menuOpen, setMenuOpen] = useState(false)
  const [suggestions, setSuggestions] = useState([])
  const [showSuggestions, setShowSuggestions] = useState(false)
  // 방향키(↑/↓)로 하이라이트한 자동완성 항목 번호 - -1이면 아무것도 고르지 않은 상태(Enter는 일반 검색).
  const [activeIndex, setActiveIndex] = useState(-1)
  const suggestionRequestId = useRef(0)
  // 방향키/후보 클릭으로 입력창 글자가 프로그램에 의해 바뀌면, 이걸 "새 입력"으로 착각해 후보를 다시
  // 불러오지 않도록 그 글자를 기억해둡니다(글자가 같을 때만 건너뜀 - 같은 값으로 바꾼 경우에도 건너뛰기
  // 표시가 남아 다음 입력을 씹는 일이 없도록 불리언 대신 값 비교를 씁니다). typedText는 사용자가 직접 친
  // 글자(Esc로 되돌릴 때 사용).
  const programmaticText = useRef(null)
  const typedText = useRef('')
  const selectedSearchType = SEARCH_TYPES.find((type) => type.value === searchType) ?? SEARCH_TYPES[0]

  // Header는 라우트가 바뀌어도 다시 마운트되지 않아서(레이아웃에 고정), 검색 결과 화면(/movies)을 벗어나면
  // 드롭박스/입력창을 기본값(영화)으로 되돌립니다. 새로고침은 컴포넌트가 새로 마운트되니 항상 기본값이고,
  // "뒤로 가기"로 /movies를 벗어났을 때도 이 로직으로 같은 기본값을 보게 됩니다. 모바일 메뉴도 페이지를
  // 옮기면 함께 닫습니다.
  useEffect(() => {
    if (location.pathname !== '/movies') {
      setSearchText('')
      setSearchType(SEARCH_TYPES[0].value)
    }
    setMenuOpen(false)
    setShowSuggestions(false)
  }, [location.pathname])

  // 입력 중일 때마다 바로 요청하지 않고(타이핑할 때마다 서버를 때리는 걸 막기 위해) 잠깐 멈췄을
  // 때만 자동완성을 불러옵니다. 응답이 늦게 와서 이미 다른 글자를 입력한 뒤 도착하는 경우(요청
  // 순서가 뒤바뀌는 경우)를 대비해, 매 요청에 번호를 매겨 가장 최근 요청의 응답만 반영합니다.
  // 후보 목록이 바뀌거나(새 입력/유형 변경) 닫히면 하이라이트를 초기화합니다.
  useEffect(() => {
    setActiveIndex(-1)
  }, [suggestions, showSuggestions])

  useEffect(() => {
    if (programmaticText.current !== null && programmaticText.current === searchText) {
      // 이미 날아간 이전 요청의 응답이 뒤늦게 목록을 덮어쓰지 못하게 요청 번호도 올려둡니다.
      suggestionRequestId.current += 1
      return undefined
    }
    if (!searchText.trim() || !AUTOCOMPLETE_FIELDS.has(searchType)) {
      setSuggestions([])
      return undefined
    }
    const requestId = ++suggestionRequestId.current
    const timer = setTimeout(() => {
      // 제목은 영화 카드(id/title/year)를, 배우/감독은 "사람 이름" 문자열만 돌려받습니다 - 두 응답이
      // 서로 다른 모양이라, 아래 렌더링/클릭 처리도 searchType으로 나눠서 다룹니다.
      const fetchSuggestions =
        searchType === 'title' ? getMovieSuggestions(searchText, 5) : getPersonSuggestions(searchText, searchType, 5)
      fetchSuggestions
        .then((results) => {
          if (suggestionRequestId.current === requestId) {
            setSuggestions(results)
            setShowSuggestions(true)
          }
        })
        .catch(() => {})
    }, SUGGESTION_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [searchText, searchType])

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const handleSearchSubmit = (event) => {
    event.preventDefault()
    setShowSuggestions(false)
    navigate(`/movies?query=${encodeURIComponent(searchText)}&field=${searchType}`)
  }

  // 제목 후보(movie 객체)는 바로 그 영화 상세 페이지로 이동하지만, 배우/감독 후보(이름 문자열)는
  // 특정 영화 하나를 가리키는 게 아니므로 그 이름으로 검색을 "제출"해서 결과 목록을 보여줍니다.
  const handleSuggestionClick = (suggestion) => {
    setShowSuggestions(false)
    if (searchType === 'title') {
      setSearchText('')
      navigate(`/movies/${encodeURIComponent(suggestion.id)}`)
      return
    }
    programmaticText.current = suggestion
    typedText.current = suggestion
    setSearchText(suggestion)
    navigate(`/movies?query=${encodeURIComponent(suggestion)}&field=${searchType}`)
  }

  // ↓/↑로 후보를 오가고(끝에서 순환), 하이라이트된 상태의 Enter는 그 후보를 선택합니다.
  const handleSearchKeyDown = (event) => {
    const listOpen = showSuggestions && suggestions.length > 0
    if (event.key === 'Escape') {
      setShowSuggestions(false)
      if (activeIndex >= 0) {
        programmaticText.current = typedText.current
        setSearchText(typedText.current)
      }
    } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      if (suggestions.length === 0) return
      event.preventDefault()
      if (!listOpen) {
        setShowSuggestions(true)
        return
      }
      const last = suggestions.length - 1
      const next =
        event.key === 'ArrowDown'
          ? activeIndex >= last ? 0 : activeIndex + 1
          : activeIndex <= 0 ? last : activeIndex - 1
      setActiveIndex(next)
      // 검색창에도 하이라이트된 후보의 글자를 바로 보여줍니다.
      const picked = suggestions[next]
      const pickedText = searchType === 'title' ? picked.title : picked
      programmaticText.current = pickedText
      setSearchText(pickedText)
    } else if (event.key === 'Enter' && listOpen && activeIndex >= 0) {
      event.preventDefault()
      handleSuggestionClick(suggestions[activeIndex])
    }
  }

  const searchForm = (
    <form onSubmit={handleSearchSubmit} className="flex flex-1 items-center gap-2">
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
      <div className="relative flex flex-1 items-center gap-2 rounded-lg bg-slate-900 px-3 py-2 text-sm text-gray-400">
        <span>🔍</span>
        <input
          name="query"
          type="text"
          value={searchText}
          onChange={(event) => {
            typedText.current = event.target.value
            programmaticText.current = null
            setSearchText(event.target.value)
          }}
          onFocus={() => suggestions.length > 0 && setShowSuggestions(true)}
          // 드롭다운 항목 클릭(mousedown)이 이 blur보다 먼저 일어나도록 click 대신 onMouseDown에서
          // 처리하므로, 여기서 바로 닫아도 클릭이 씹히지 않습니다.
          onBlur={() => setShowSuggestions(false)}
          onKeyDown={handleSearchKeyDown}
          role="combobox"
          aria-expanded={showSuggestions && suggestions.length > 0}
          aria-autocomplete="list"
          autoComplete="off"
          placeholder={`${selectedSearchType.label}${selectedSearchType.particle} 입력해주세요`}
          className="w-full bg-transparent text-gray-100 placeholder:text-gray-500 focus:outline-none"
        />

        {showSuggestions && suggestions.length > 0 && (
          <ul
            role="listbox"
            className="absolute left-0 right-0 top-full z-30 mt-2 overflow-hidden rounded-lg border border-white/10 bg-slate-900 shadow-lg"
          >
            {suggestions.map((suggestion, index) => (
              <li
                key={searchType === 'title' ? suggestion.id : suggestion}
                role="option"
                aria-selected={index === activeIndex}
              >
                <button
                  type="button"
                  tabIndex={-1}
                  onMouseEnter={() => setActiveIndex(index)}
                  onMouseDown={(event) => {
                    event.preventDefault()
                    handleSuggestionClick(suggestion)
                  }}
                  className={`flex w-full items-center gap-3 px-3 py-2 text-left text-sm text-gray-200 hover:bg-slate-800 ${
                    index === activeIndex ? 'bg-slate-800' : ''
                  }`}
                >
                  {searchType === 'title' ? (
                    <>
                      <span className="truncate">{suggestion.title}</span>
                      {suggestion.year && <span className="shrink-0 text-xs text-gray-500">{suggestion.year}</span>}
                    </>
                  ) : (
                    <span className="truncate">{suggestion}</span>
                  )}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </form>
  )

  return (
    <header className="sticky top-0 z-20 border-b border-white/5 bg-slate-950 px-4 py-3 md:px-6">
      <div className="flex items-center gap-4 md:gap-6">
        <NavLink to="/" className="flex items-center gap-2 text-lg font-bold text-white">
          <span className="flex h-7 w-7 items-center justify-center rounded-md bg-gradient-to-br from-indigo-500 to-purple-500">
            🎬
          </span>
          <span className="hidden sm:inline">SESAC MOVIE</span>
        </NavLink>

        {/* md 이상(데스크톱)에서만 보이는 내비/검색/사용자 영역 - 한 줄에 전부 들어갈 공간이 있을 때만 */}
        <nav className="hidden items-center gap-5 text-sm font-medium text-gray-300 md:flex">
          {NAV_LINKS.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              className={({ isActive }) => (isActive ? 'text-white' : 'text-gray-400 hover:text-white')}
            >
              {link.label}
            </NavLink>
          ))}
        </nav>

        <div className="ml-4 hidden max-w-xl flex-1 md:flex">{searchForm}</div>

        <div className="ml-auto hidden items-center gap-4 md:flex">
          {/* 화면 폭별 표시: 1100px 이상은 188px 로 넓게 채우고, 900~1099px 은 글자를 16px 로 줄여 작게 표시하며,
              900px 보다 좁으면 숨깁니다(그 폭에서는 배지 때문에 메뉴/로그아웃 글자가 세로로 꺾이기 때문). */}
          <VersionBadge className="block shrink-0 min-[1100px]:w-[188px] max-[1100px]:text-[16px] max-[900px]:hidden" />
          <ThemeToggle />
          <NotificationBell />
          <span className="text-sm text-gray-400">{user?.nickname}</span>
          <NavLink to="/mypage" aria-label="마이페이지">
            <span className="block h-8 w-8 rounded-full bg-gradient-to-br from-orange-400 to-red-500" />
          </NavLink>
          <button type="button" onClick={handleLogout} className="text-xs text-gray-400 hover:text-white">
            로그아웃
          </button>
        </div>

        {/* md 미만(모바일)에서만 보이는 햄버거 - 내비/검색/사용자 메뉴를 전부 아래 펼침 패널로 옮깁니다. */}
        <div className="ml-auto flex items-center gap-3 md:hidden">
          <ThemeToggle />
          <NotificationBell />
          <button
            type="button"
            onClick={() => setMenuOpen((open) => !open)}
            aria-label={menuOpen ? '메뉴 닫기' : '메뉴 열기'}
            className="text-xl text-gray-300 hover:text-white"
          >
            {menuOpen ? '✕' : '☰'}
          </button>
        </div>
      </div>

      {menuOpen && (
        <div className="mt-3 space-y-4 border-t border-white/5 pt-3 md:hidden">
          <nav className="flex flex-col gap-3 text-sm font-medium text-gray-300">
            {NAV_LINKS.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                className={({ isActive }) => (isActive ? 'text-white' : 'text-gray-400 hover:text-white')}
              >
                {link.label}
              </NavLink>
            ))}
          </nav>

          {searchForm}

          <div className="flex items-center justify-between border-t border-white/5 pt-3 text-sm text-gray-400">
            <NavLink to="/mypage" aria-label="마이페이지" className="flex items-center gap-2">
              <span className="block h-8 w-8 rounded-full bg-gradient-to-br from-orange-400 to-red-500" />
              <span>{user?.nickname}</span>
            </NavLink>
            <button type="button" onClick={handleLogout} className="text-xs text-gray-400 hover:text-white">
              로그아웃
            </button>
          </div>
        </div>
      )}
    </header>
  )
}

export default Header
