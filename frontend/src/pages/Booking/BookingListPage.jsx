import { useEffect, useState } from 'react'
import { getFirstMatches } from '../../api/movieApi'
import NowShowingCard from './components/NowShowingCard'
import { NOW_SHOWING_TITLES } from './bookingData'

function BookingListPage() {
  const [movies, setMovies] = useState([])
  const [status, setStatus] = useState('loading') // loading | ready | error

  useEffect(() => {
    let cancelled = false
    getFirstMatches(NOW_SHOWING_TITLES)
      .then((results) => {
        if (cancelled) return
        setMovies(results)
        setStatus('ready')
      })
      .catch(() => {
        if (cancelled) return
        setStatus('error')
      })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="mx-auto max-w-5xl px-6 py-6">
      <h1 className="text-xl font-bold text-gray-100">예매</h1>
      <p className="mt-1 text-sm text-gray-400">예매할 영화를 선택해주세요.</p>

      {status === 'loading' && <p className="mt-6 text-gray-400">KMDB에서 상영중인 영화를 불러오는 중...</p>}
      {status === 'error' && <p className="mt-6 text-red-400">영화 목록을 불러오지 못했습니다.</p>}

      {status === 'ready' && (
        <div className="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4">
          {movies.map((movie) => (
            <NowShowingCard key={movie.id} movie={movie} />
          ))}
        </div>
      )}
    </div>
  )
}

export default BookingListPage
