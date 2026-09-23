import { useEffect, useState } from 'react'
import { useAuth } from '../../auth/AuthContext'
import { getFirstMatches } from '../../api/movieApi'
import { addDownload, getDownloads, removeDownload } from '../../api/downloadApi'
import DownloadCard from './components/DownloadCard'
import { CURATED_DOWNLOAD_TITLES } from './curatedDownloadTitles'

// 실제 다운로드 백엔드가 없어서, 진행률을 화면에서만 흉내 냅니다.
const PROGRESS_STEP = 20
const PROGRESS_INTERVAL_MS = 250

function estimateSizeMb(movie) {
  return Math.round((movie.runtimeMinutes ?? 120) * 14)
}

function DownloadPage() {
  const { user } = useAuth()
  const [movies, setMovies] = useState([])
  const [status, setStatus] = useState('loading') // loading | ready | error
  const [downloads, setDownloads] = useState([])
  const [progressById, setProgressById] = useState({})

  useEffect(() => {
    let cancelled = false
    Promise.all([getFirstMatches(CURATED_DOWNLOAD_TITLES), getDownloads(user.id)])
      .then(([results, downloadList]) => {
        if (cancelled) return
        setMovies(results)
        setDownloads(downloadList)
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

  const handleDownload = (movie) => {
    setProgressById((prev) => ({ ...prev, [movie.id]: 0 }))

    const interval = setInterval(() => {
      setProgressById((prev) => {
        const current = prev[movie.id] ?? 0
        const next = Math.min(current + PROGRESS_STEP, 100)

        if (next >= 100) {
          clearInterval(interval)
          addDownload(user.id, {
            movieId: movie.id,
            movieTitle: movie.title,
            posterUrl: movie.posterUrl,
            sizeMb: estimateSizeMb(movie),
          }).then(() => getDownloads(user.id).then(setDownloads))
          const { [movie.id]: _removed, ...rest } = prev
          return rest
        }

        return { ...prev, [movie.id]: next }
      })
    }, PROGRESS_INTERVAL_MS)
  }

  const handleRemove = (movieId) => {
    removeDownload(user.id, movieId).then(() => getDownloads(user.id).then(setDownloads))
  }

  const totalSizeMb = downloads.reduce((sum, item) => sum + (item.sizeMb ?? 0), 0)

  return (
    <div className="mx-auto max-w-3xl px-6 py-6">
      <h1 className="text-xl font-bold text-gray-100">다운로드</h1>
      <p className="mt-1 text-sm text-gray-400">다운로드한 영화는 인터넷 연결 없이도 시청할 수 있어요.</p>

      <div className="mt-4 rounded-lg bg-slate-900 px-4 py-3 text-sm text-gray-300">
        다운로드한 영화 {downloads.length}개 ·{' '}
        {totalSizeMb >= 1024 ? `${(totalSizeMb / 1024).toFixed(1)}GB` : `${totalSizeMb}MB`} 사용 중
      </div>

      {status === 'loading' && <p className="mt-6 text-gray-400">KMDB에서 불러오는 중...</p>}
      {status === 'error' && <p className="mt-6 text-red-400">영화 목록을 불러오지 못했습니다.</p>}

      {status === 'ready' && (
        <div className="mt-6 space-y-3">
          {movies.map((movie) => (
            <DownloadCard
              key={movie.id}
              movie={movie}
              sizeMb={estimateSizeMb(movie)}
              progress={progressById[movie.id] ?? null}
              isDownloaded={downloads.some((item) => item.movieId === movie.id)}
              onDownload={() => handleDownload(movie)}
              onRemove={() => handleRemove(movie.id)}
            />
          ))}
        </div>
      )}
    </div>
  )
}

export default DownloadPage
