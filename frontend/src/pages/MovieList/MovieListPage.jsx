import { useEffect, useState } from 'react'
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

  // 검색어/검색대상/장르/연도/러닝타임/정렬이 바뀌면 1페이지부터 다시 봅니다.
  useEffect(() => {
    setPage(1)
  }, [query, field, filters.genres, filters.year, filters.runtime, sort])

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
    <div className="mx-auto flex max-w-7xl gap-8 px-6 py-6">
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
