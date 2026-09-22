import { useEffect, useState } from 'react'
import { getFirstMatches } from '../../api/movieApi'
import AiRecommendBanner from './components/AiRecommendBanner'
import MovieSection from './components/MovieSection'
import { POPULAR_QUERIES, THRILLER_QUERIES } from './curatedQueries'

function HomePage() {
  const [popularMovies, setPopularMovies] = useState([])
  const [thrillerMovies, setThrillerMovies] = useState([])
  const [status, setStatus] = useState('loading') // loading | ready | error

  useEffect(() => {
    let cancelled = false
    setStatus('loading')

    Promise.all([getFirstMatches(POPULAR_QUERIES), getFirstMatches(THRILLER_QUERIES)])
      .then(([popular, thriller]) => {
        if (cancelled) return
        setPopularMovies(popular)
        setThrillerMovies(thriller)
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
    <div className="mx-auto max-w-7xl px-6 py-6">
      <AiRecommendBanner />

      {status === 'loading' && <p className="mt-8 text-gray-400">KMDB에서 영화 정보를 불러오는 중...</p>}
      {status === 'error' && <p className="mt-8 text-red-400">영화 정보를 불러오지 못했습니다.</p>}

      {status === 'ready' && (
        <>
          <MovieSection icon="🔥" title="지금 인기 있는 영화" movies={popularMovies} />
          <MovieSection icon="🎯" title="취향저격 신작 (장르: 스릴러)" movies={thrillerMovies} />
        </>
      )}
    </div>
  )
}

export default HomePage
