import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import MovieCard from '../../components/common/MovieCard'
import { searchMovies } from '../../api/movieApi'
import FilterSidebar from './components/FilterSidebar'
import Pagination from './components/Pagination'

const PAGE_SIZE = 15
const RUNTIME_LIMIT_MINUTES = 120

function MovieListPage() {
  const [searchParams] = useSearchParams()
  const query = searchParams.get('query') ?? ''

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

  // 검색어/장르/연도/정렬이 바뀌면 1페이지부터 다시 봅니다.
  useEffect(() => {
    setPage(1)
  }, [query, filters.genres, filters.year, sort])

  useEffect(() => {
    let cancelled = false
    setStatus('loading')

    searchMovies(query, {
      genre: filters.genres,
      year: filters.year === '전체' ? undefined : filters.year,
      sort,
      page,
      pageSize: PAGE_SIZE,
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
  }, [query, filters.genres, filters.year, sort, page])

  // 정렬(최신순/이름순)은 이제 백엔드가 카탈로그 전체 기준으로 미리 정렬해서 내려줍니다.
  // 러닝타임만 KMDB 검색 API에 해당 파라미터가 없어 현재 페이지 결과 안에서 클라이언트 필터링합니다.
  const visibleMovies = useMemo(() => {
    if (filters.runtime === '2시간 미만') {
      return result.movies.filter(
        (movie) => movie.runtimeMinutes != null && movie.runtimeMinutes < RUNTIME_LIMIT_MINUTES,
      )
    }
    if (filters.runtime === '2시간 이상') {
      return result.movies.filter(
        (movie) => movie.runtimeMinutes != null && movie.runtimeMinutes >= RUNTIME_LIMIT_MINUTES,
      )
    }
    return result.movies
  }, [result.movies, filters.runtime])

  const heading = query ? `"${query}" 검색 결과` : '전체 영화'

  return (
    <div className="mx-auto flex max-w-7xl gap-8 px-6 py-6">
      <FilterSidebar filters={filters} onChange={handleFilterChange} onToggleGenre={handleToggleGenre} />

      <section className="flex-1">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-bold text-gray-100">
            {heading}
            {status === 'success' && <span className="text-gray-400"> {result.totalCount.toLocaleString()}건</span>}
          </h2>
          <select
            value={sort}
            onChange={(event) => setSort(event.target.value)}
            className="rounded-md bg-slate-800 px-3 py-1.5 text-sm text-gray-300"
          >
            <option value="latest">최신순</option>
            <option value="name">이름순</option>
          </select>
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
