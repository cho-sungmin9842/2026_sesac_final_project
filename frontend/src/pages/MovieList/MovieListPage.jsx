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

// 장르/연도/러닝타임/연령등급/정렬/페이지를 전부 "로컬 state"로 따로 들고 있다가 URL과 어긋나는 문제가
// 반복해서 생겼습니다(2026-11-01 실측 - 페이지 번호만 따로 동기화했더니 이번엔 장르를 바꾸고 다른
// 페이지로 간 뒤 영화를 열었다가 뒤로 가면, 그 URL에 남아있던 "맨 처음 장르"로 되돌아가는 버그로
// 다시 나타남). 그래서 이 화면의 모든 필터/정렬/페이지 상태는 로컬 state를 아예 두지 않고 URL
// 쿼리스트링에서 직접 읽어서 씁니다 - URL이 유일한 출처이므로, 브라우저 "뒤로 가기"가 그 URL을
// 그대로 복원해주기만 하면 화면도 항상 정확히 그때 보고 있던 상태로 돌아갑니다.
function MovieListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('query') ?? ''
  const field = searchParams.get('field') ?? 'title'
  const sort = searchParams.get('sort') ?? 'latest'
  const page = (() => {
    const fromUrl = Number.parseInt(searchParams.get('page'), 10)
    return Number.isInteger(fromUrl) && fromUrl > 0 ? fromUrl : 1
  })()
  const filters = {
    genres: searchParams.getAll('genre'),
    year: searchParams.get('year') ?? '전체',
    runtime: searchParams.get('runtime') ?? '전체',
    ageRating: searchParams.get('ageRating') ?? '전체',
  }

  const [result, setResult] = useState({ movies: [], totalCount: 0, totalPages: 0 })
  const [status, setStatus] = useState('loading') // loading | success | error
  const [errorMessage, setErrorMessage] = useState('')

  // 장르/연도/러닝타임/연령등급/정렬을 바꾸는 조작은 전부 "그 값을 URL에 반영 + page는 지움(1페이지로)"
  // 형태라 공통 헬퍼로 묶습니다. value가 '전체'(필터 해제)면 해당 파라미터 자체를 지웁니다.
  const applyFilterChange = (updateParams) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      updateParams(next)
      next.delete('page')
      return next
    })
  }

  const handleFilterChange = (key, value) => {
    applyFilterChange((next) => {
      if (value === '전체') {
        next.delete(key)
      } else {
        next.set(key, value)
      }
    })
  }

  const handleToggleGenre = (genre) => {
    applyFilterChange((next) => {
      const current = next.getAll('genre')
      next.delete('genre')
      const updated =
        genre === '전체'
          ? []
          : current.includes(genre)
            ? current.filter((g) => g !== genre)
            : [...current, genre]
      updated.forEach((g) => next.append('genre', g))
    })
  }

  const handleSortChange = (value) => {
    applyFilterChange((next) => {
      if (value === 'latest') {
        next.delete('sort')
      } else {
        next.set('sort', value)
      }
    })
  }

  const handlePageChange = (value) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      if (value > 1) {
        next.set('page', String(value))
      } else {
        next.delete('page')
      }
      return next
    })
  }

  // 장르/개봉연도/러닝타임/연령등급/정렬을 바꾸거나 페이지를 넘기면, 스크롤을 내려서 아래쪽 카드를
  // 보던 중이었어도 바뀐 결과를 처음부터 볼 수 있도록 맨 위로 이동합니다. 검색어 자체가 바뀌는 건
  // App.jsx의 전역 ScrollToTop이 이미 처리합니다.
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [filters.genres.join(','), filters.year, filters.runtime, filters.ageRating, sort, page])

  useEffect(() => {
    let cancelled = false
    setStatus('loading')

    searchMovies(query, {
      genre: filters.genres,
      year: filters.year === '전체' ? undefined : filters.year,
      runtime: filters.runtime,
      ageRating: filters.ageRating,
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query, field, filters.genres.join(','), filters.year, filters.runtime, filters.ageRating, sort, page])

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
          <SortDropdown value={sort} options={SORT_OPTIONS} onChange={handleSortChange} />
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
            <Pagination page={result.page} totalPages={result.totalPages} onChange={handlePageChange} />
          </>
        )}
      </section>
    </div>
  )
}

export default MovieListPage
