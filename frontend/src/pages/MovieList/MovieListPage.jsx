import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import MovieCard from '../../components/common/MovieCard'
import { searchMovies } from '../../api/movieApi'
import FilterSidebar from './components/FilterSidebar'
import Pagination from './components/Pagination'

const PAGE_SIZE = 20
const RUNTIME_LIMIT_MINUTES = 120

// KMDB 검색 API에는 정렬 파라미터가 없어서, 현재 페이지에 불러온 결과만 화면에서 정렬합니다.
const SORTERS = {
  latest: (a, b) => (b.year ?? 0) - (a.year ?? 0),
  name: (a, b) => a.title.localeCompare(b.title, 'ko'),
}

function MovieListPage() {
  const [searchParams] = useSearchParams()
  const query = searchParams.get('query') ?? ''

  const [filters, setFilters] = useState({
    genre: '전체',
    year: '전체',
    runtime: '전체',
  })

  const [sort, setSort] = useState('latest')
  const [page, setPage] = useState(1)
  const [result, setResult] = useState({ movies: [], totalCount: 0, totalPages: 0 })
  const [status, setStatus] = useState('loading') // loading | success | error
  const [errorMessage, setErrorMessage] = useState('')

  const handleFilterChange = (key, value) => {
    setFilters((prev) => ({ ...prev, [key]: value }))
  }

  // 검색어나 장르/연도가 바뀌면 1페이지부터 다시 봅니다.
  useEffect(() => {
    setPage(1)
  }, [query, filters.genre, filters.year])

  useEffect(() => {
    let cancelled = false
    setStatus('loading')

    searchMovies(query, {
      genre: filters.genre === '전체' ? undefined : filters.genre,
      year: filters.year === '전체' ? undefined : filters.year,
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
  }, [query, filters.genre, filters.year, page])

  // 러닝타임/정렬 모두 KMDB 검색 API에 해당 파라미터가 없어 현재 페이지 결과 안에서만 처리합니다.
  const visibleMovies = useMemo(() => {
    let filtered = result.movies
    if (filters.runtime === '2시간 미만') {
      filtered = result.movies.filter(
        (movie) => movie.runtimeMinutes != null && movie.runtimeMinutes < RUNTIME_LIMIT_MINUTES,
      )
    } else if (filters.runtime === '2시간 이상') {
      filtered = result.movies.filter(
        (movie) => movie.runtimeMinutes != null && movie.runtimeMinutes >= RUNTIME_LIMIT_MINUTES,
      )
    }
    return [...filtered].sort(SORTERS[sort])
  }, [result.movies, filters.runtime, sort])

  const heading = query ? `"${query}" 검색 결과` : '전체 영화'

  return (
    <div className="mx-auto flex max-w-7xl gap-8 px-6 py-6">
      <FilterSidebar filters={filters} onChange={handleFilterChange} />

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
