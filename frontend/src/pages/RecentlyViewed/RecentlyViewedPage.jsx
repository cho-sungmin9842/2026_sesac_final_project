import { useEffect, useState } from 'react'
import { useAuth } from '../../auth/AuthContext'
import { getRecentlyViewed } from '../../api/recentlyViewedApi'
import { enrichAsMovie } from '../../utils/enrichMovie'
import MovieCard from '../../components/common/MovieCard'

// 홈 화면 "최근 조회한 영화" 섹션의 "전체보기" - 2줄(14개) 캡 없이 기록된 전체 목록을 보여줍니다.
const RECENTLY_VIEWED_FULL_LIMIT = 200

function RecentlyViewedPage() {
  const { user } = useAuth()
  const [movies, setMovies] = useState([])
  const [status, setStatus] = useState('loading') // loading | ready | error

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
        <div className="mt-4 grid grid-cols-2 gap-6 sm:grid-cols-3 lg:grid-cols-5">
          {movies.map((movie) => (
            <MovieCard key={movie.id} movie={movie} />
          ))}
        </div>
      )}
    </div>
  )
}

export default RecentlyViewedPage
