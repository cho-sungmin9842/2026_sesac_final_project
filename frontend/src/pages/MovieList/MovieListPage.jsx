import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import MovieCard from '../../components/common/MovieCard'
import { searchMovies } from '../../api/movieApi'
import FilterSidebar from './components/FilterSidebar'
import Pagination from './components/Pagination'
import SortDropdown from './components/SortDropdown'

const PAGE_SIZE = 15
const SORT_OPTIONS = [
  { value: 'latest', label: '최신순' },
  { value: 'name', label: '이름순' },
  { value: 'rating', label: '평점순' },
]

function MovieListPage() {
  const [searchParams] = useSearchParams()
  const query = searchParams.get('query') ?? ''
  const field = searchParams.get('field') ?? 'title'

  // 홈 화면 "취향저격 신작"의 "전체보기"처럼 ?genre=코미디&genre=액션 형태로 여러 장르를 넘겨받으면
  // 사이드바가 처음부터 그 장르들을 선택된 상태로 보여줍니다.
  const [filters, setFilters] = useState(() => ({
    genres: searchParams.getAll('genre'),
    year: '전체',
    runtime: '전체',
  }))

  const [sort, setSort] = useState('latest')
  const [page, setPage] = useState(1)
  const [result, setResult] = useState({ movies: [], totalCount: 0, totalPages: 0 })
  const [status, setStatus] = useState('loading') // loading | success | error
  const [errorMessage, setErrorMessage] = useState('')

  const handleFilterChange = (key, value) => {
    setFilters((prev) => ({ ...prev, [key]: value }))
  }

  const handleToggleGenre = (genre) => {
    setFilters((prev) => {
      if (genre === '전체') return { ...prev, genres: [] }
      const isSelected = prev.genres.includes(genre)
      return { ...prev, genres: isSelected ? prev.genres.filter((g) => g !== genre) : [...prev.genres, genre] }
    })
  }

  // 검색어/쿼리스트링이 바뀔 때 맨 위로 스크롤하는 건 App.jsx의 전역 ScrollToTop이 처리합니다(쿼리스트링
  // 변경도 감지합니다). 장르/연도/러닝타임 사이드바 필터는 URL에 안 실리는 순수 로컬 상태라 전역
  // ScrollToTop이 못 보므로, 그건 아래 filters 전용 effect가 따로 처리합니다.

  // 헤더 검색창은 이 페이지를 벗어나지 않고(라우트가 같아 컴포넌트가 다시 마운트되지 않음) query/field만
  // 바꿔서 새 검색을 겁니다. 그런데 사이드바에서 장르/연도/러닝타임을 "전체"가 아닌 값으로 골라둔 채로
  // 새로 검색하면, filters state가 그대로 남아있어 사이드바는 옛 선택 그대로 보이고 결과도 그 옛 조건과
  // 함께 걸러져 나왔습니다 - 새로 검색할 땐 장르/연도/러닝타임을 전체로 리셋하고 그 조건으로 다시 걸러야
  // 합니다(2026-10-14 요청). query/field가 실제로 바뀐 경우에만 리셋해야 하므로("처음 한 번만 건너뛰기"
  // 식의 boolean 플래그는 개발 모드 StrictMode가 마운트 직후 effect를 한 번 더 재실행할 때 "이미 건너뛴
  // 상태"로 착각해 곧바로 리셋을 실행해버리는 버그가 있었습니다 - 2026-10-14 실측, 홈 화면 "전체보기"로
  // 들어오자마자 장르 선택이 바로 풀려버림) 직전에 본 query/field 값을 ref로 기억해두고, 그 값과 실제로
  // 달라졌을 때만 리셋합니다.
  const previousSearchKeyRef = useRef(`${query}|${field}`)
  useEffect(() => {
    const searchKey = `${query}|${field}`
    if (previousSearchKeyRef.current === searchKey) {
      return
    }
    previousSearchKeyRef.current = searchKey
    setFilters({ genres: [], year: '전체', runtime: '전체' })
  }, [query, field])

  // 검색어/검색대상/장르/연도/러닝타임/정렬이 바뀌면 1페이지부터 다시 봅니다.
  useEffect(() => {
    setPage(1)
  }, [query, field, filters.genres, filters.year, filters.runtime, sort])

  // 장르/개봉연도/러닝타임을 고르거나 페이지를 넘기면, 스크롤을 내려서 아래쪽 카드를 보던 중이었어도
  // 바뀐 결과를 처음부터 볼 수 있도록 맨 위로 이동합니다.
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [filters.genres, filters.year, filters.runtime, page])

  useEffect(() => {
    let cancelled = false
    setStatus('loading')

    searchMovies(query, {
      genre: filters.genres,
      year: filters.year === '전체' ? undefined : filters.year,
      runtime: filters.runtime,
      sort,
      page,
      pageSize: PAGE_SIZE,
      field,
    })
      .then((data) => {
        if (cancelled) return
        setResult(data)
        setStatus('success')
      })
      .catch((error) => {
        if (cancelled) return
        setErrorMessage(error.message)
        setStatus('error')
      })

    return () => {
      cancelled = true
    }
  }, [query, field, filters.genres, filters.year, filters.runtime, sort, page])

  // 장르/연도/러닝타임/정렬 모두 백엔드가 직접 걸러서 내려주므로(러닝타임도 totalCount에 반영됨),
  // 받은 결과를 그대로 보여줍니다.
  const visibleMovies = result.movies

  const heading = query ? `"${query}" 검색 결과` : '전체 영화'

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-6 px-4 py-6 sm:px-6 md:flex-row md:gap-8">
      <FilterSidebar filters={filters} onChange={handleFilterChange} onToggleGenre={handleToggleGenre} />

      <section className="flex-1">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-bold text-gray-100">
            {heading}
            {status === 'success' && <span className="text-gray-400"> {result.totalCount.toLocaleString()}건</span>}
          </h2>
          <SortDropdown value={sort} options={SORT_OPTIONS} onChange={setSort} />
        </div>

        {status === 'loading' && <p className="text-gray-400">KMDB에서 불러오는 중...</p>}

        {status === 'error' && <p className="text-red-400">검색에 실패했습니다: {errorMessage}</p>}

        {status === 'success' && visibleMovies.length === 0 && (
          <p className="text-gray-400">검색 결과가 없습니다.</p>
        )}

        {status === 'success' && visibleMovies.length > 0 && (
          <>
            <div className="grid grid-cols-2 gap-6 sm:grid-cols-3 lg:grid-cols-5">
              {visibleMovies.map((movie) => (
                <MovieCard key={movie.id} movie={movie} />
              ))}
            </div>
            <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />
          </>
        )}
      </section>
    </div>
  )
}

export default MovieListPage
