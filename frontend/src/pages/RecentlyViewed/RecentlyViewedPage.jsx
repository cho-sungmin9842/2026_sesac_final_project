import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { getRecentlyViewed } from '../../api/recentlyViewedApi'
import { enrichAsMovie } from '../../utils/enrichMovie'
import MovieCard from '../../components/common/MovieCard'
import Pagination from '../../components/common/Pagination'

// 홈 화면 "최근 조회한 영화" 섹션의 "전체보기" - 2줄(14개) 캡 없이, 서버에 저장된 전체 기록(최대 100건,
// 백엔드에서 사용자당 상한을 관리)을 보여줍니다.
const RECENTLY_VIEWED_FULL_LIMIT = 100
const ITEMS_PER_PAGE = 15

function RecentlyViewedPage() {
  const { user } = useAuth()
  const [movies, setMovies] = useState([])
  const [status, setStatus] = useState('loading') // loading | ready | error
  // 페이지 번호는 로컬 state가 아니라 URL 쿼리스트링에서 읽습니다 - 그래야 앱 전역 ScrollToTop이
  // "페이지 이동"으로 인식해서 화면을 자동으로 맨 위로 올려줍니다(MovieListPage와 동일한 패턴).
  const [searchParams, setSearchParams] = useSearchParams()
  const page = (() => {
    const fromUrl = Number.parseInt(searchParams.get('page'), 10)
    return Number.isInteger(fromUrl) && fromUrl > 0 ? fromUrl : 1
  })()

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

  useEffect(() => {
    let cancelled = false
    setStatus('loading')

    getRecentlyViewed(user.id, RECENTLY_VIEWED_FULL_LIMIT)
      .then((items) => Promise.all(items.map(enrichAsMovie)))
      .then((result) => {
        if (cancelled) return
        setMovies(result)
        setStatus('ready')
      })
      .catch(() => {
        if (cancelled) return
        setStatus('error')
      })

    return () => {
      cancelled = true
    }
  }, [user.id])

  return (
    <div className="mx-auto max-w-7xl px-6 py-6">
      <h1 className="text-lg font-bold text-gray-100">
        최근 조회한 영화
        {status === 'ready' && <span className="text-gray-400"> {movies.length.toLocaleString()}건</span>}
      </h1>

      {status === 'loading' && <p className="mt-4 text-gray-400">불러오는 중...</p>}
      {status === 'error' && <p className="mt-4 text-red-400">목록을 불러오지 못했습니다.</p>}
      {status === 'ready' && movies.length === 0 && (
        <p className="mt-4 text-gray-400">최근에 조회한 영화가 없습니다.</p>
      )}

      {status === 'ready' && movies.length > 0 && (
        <>
          <div className="mt-4 grid grid-cols-2 gap-6 sm:grid-cols-3 lg:grid-cols-5">
            {movies.slice((page - 1) * ITEMS_PER_PAGE, page * ITEMS_PER_PAGE).map((movie) => (
              <MovieCard key={movie.id} movie={movie} />
            ))}
          </div>
          <Pagination
            page={page}
            totalPages={Math.ceil(movies.length / ITEMS_PER_PAGE)}
            onChange={handlePageChange}
          />
        </>
      )}
    </div>
  )
}

export default RecentlyViewedPage
