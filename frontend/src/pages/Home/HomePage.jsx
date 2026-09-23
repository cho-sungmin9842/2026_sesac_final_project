import { useEffect, useState } from 'react'
import { useAuth } from '../../auth/AuthContext'
import { getFirstMatches, searchMovies } from '../../api/movieApi'
import { getPreferredGenres } from '../../api/userApi'
import { genreLabel as translateGenre } from '../MovieList/components/filterOptions'
import AiRecommendBanner from './components/AiRecommendBanner'
import MovieSection from './components/MovieSection'
import { POPULAR_QUERIES } from './curatedQueries'

const GENRE_SEARCH_PAGE_SIZE = 20
const RECOMMENDED_SECTION_SIZE = 14

// 선호 장르가 여러 개면 장르별로 나눠 검색한 뒤 하나로 합치고, 최신 개봉작이 앞에 오도록 정렬합니다.
// KMDB genre 검색 파라미터는 한 번에 장르 하나만 받기 때문에(부분일치), 장르별로 따로 호출해야 합니다.
// 선호 장르를 하나도 고르지 않았다면 임의의 기본 장르로 대체하지 않고 그냥 빈 목록을 돌려줍니다.
async function fetchGenreRecommendations(genres) {
  if (genres.length === 0) return []

  const results = await Promise.all(
    genres.map((genre) =>
      searchMovies('', { genre, page: 1, pageSize: GENRE_SEARCH_PAGE_SIZE }).catch(() => ({ movies: [] })),
    ),
  )

  const merged = new Map()
  results.forEach((result) => {
    result.movies.forEach((movie) => {
      if (!merged.has(movie.id)) merged.set(movie.id, movie)
    })
  })

  return [...merged.values()]
    .sort((a, b) => (b.year ?? 0) - (a.year ?? 0))
    .slice(0, RECOMMENDED_SECTION_SIZE)
}

function HomePage() {
  const { user } = useAuth()
  const [popularMovies, setPopularMovies] = useState([])
  const [recommendedMovies, setRecommendedMovies] = useState([])
  const [preferredGenres, setPreferredGenres] = useState([])
  const [status, setStatus] = useState('loading') // loading | ready | error

  useEffect(() => {
    let cancelled = false
    setStatus('loading')

    getPreferredGenres(user.id)
      .then((dto) =>
        Promise.all([getFirstMatches(POPULAR_QUERIES), fetchGenreRecommendations(dto.genres)]).then(
          ([popular, recommended]) => ({ popular, recommended, genres: dto.genres }),
        ),
      )
      .then(({ popular, recommended, genres }) => {
        if (cancelled) return
        setPopularMovies(popular)
        setRecommendedMovies(recommended)
        setPreferredGenres(genres)
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

  const hasPreferredGenres = preferredGenres.length > 0
  const genreLabelText = preferredGenres.map(translateGenre).join(', ')

  // "전체보기"를 누르면 /movies 사이드바에 이 섹션과 동일한 장르(들)가 선택된 채로 넘어가도록,
  // KMDB genre 파라미터와 같은 방식(반복 파라미터)으로 쿼리스트링을 만듭니다.
  const recommendedViewAllTo = `/movies?${preferredGenres.map((g) => `genre=${encodeURIComponent(g)}`).join('&')}`

  return (
    <div className="mx-auto max-w-7xl px-6 py-6">
      <AiRecommendBanner />

      {status === 'loading' && <p className="mt-8 text-gray-400">KMDB에서 영화 정보를 불러오는 중...</p>}
      {status === 'error' && <p className="mt-8 text-red-400">영화 정보를 불러오지 못했습니다.</p>}

      {status === 'ready' && (
        <>
          <MovieSection icon="🔥" title="지금 인기 있는 영화" movies={popularMovies} />
          {hasPreferredGenres && (
            <MovieSection
              icon="🎯"
              title={`취향저격 신작 (장르: ${genreLabelText})`}
              movies={recommendedMovies}
              viewAllTo={recommendedViewAllTo}
            />
          )}
        </>
      )}
    </div>
  )
}

export default HomePage
