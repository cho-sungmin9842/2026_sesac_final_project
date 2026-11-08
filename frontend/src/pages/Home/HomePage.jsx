import { useEffect, useState } from 'react'
import { useAuth } from '../../auth/AuthContext'
import { getPopularMovies, searchMovies } from '../../api/movieApi'
import { getPreferredGenres } from '../../api/userApi'
import { getRecentlyViewed } from '../../api/recentlyViewedApi'
import { genreLabel as translateGenre } from '../MovieList/components/filterOptions'
import { enrichAsMovie } from '../../utils/enrichMovie'
import AiRecommendBanner from './components/AiRecommendBanner'
import MovieSection from './components/MovieSection'

const GENRE_SEARCH_PAGE_SIZE = 20
const RECOMMENDED_SECTION_SIZE = 14
const RECENTLY_VIEWED_SIZE = 14

// "지금 인기 있는 영화" 섹션 제목에 쓸 오늘 날짜(YYYY.MM.DD) - 백엔드가 releaseDts를 접속 시점 날짜로
// 거르는 것과 같은 기준(오늘)이라는 걸 제목에서도 바로 알 수 있게 합니다.
function todayLabel() {
  const today = new Date()
  const month = String(today.getMonth() + 1).padStart(2, '0')
  const day = String(today.getDate()).padStart(2, '0')
  return `${today.getFullYear()}.${month}.${day}`
}

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
  const [recentlyViewedMovies, setRecentlyViewedMovies] = useState([])
  const [status, setStatus] = useState('loading') // loading | ready | error

  useEffect(() => {
    let cancelled = false
    setStatus('loading')

    getPreferredGenres(user.id)
      .then((dto) =>
        Promise.all([getPopularMovies(), fetchGenreRecommendations(dto.genres)]).then(
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

  // 최근 본 영화는 다른 섹션과 달리 실패해도 페이지 전체를 에러로 보내지 않고 그냥 섹션을 숨깁니다.
  useEffect(() => {
    let cancelled = false
    getRecentlyViewed(user.id, RECENTLY_VIEWED_SIZE)
      .then((items) => Promise.all(items.map(enrichAsMovie)))
      .then((movies) => {
        if (cancelled) return
        setRecentlyViewedMovies(movies)
      })
      .catch(() => {})
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
          {recentlyViewedMovies.length > 0 && (
            <MovieSection
              icon="🕒"
              title="최근 조회한 영화"
              movies={recentlyViewedMovies}
              viewAllTo="/recently-viewed"
            />
          )}
          <MovieSection
            icon="🔥"
            title={`오늘(${todayLabel()}) 개봉한 영화`}
            movies={popularMovies}
            maxVisible={popularMovies.length}
            showViewAll={false}
          />
          {hasPreferredGenres && (
            <MovieSection
              icon="🎯"
              title={`취향저격 (장르: ${genreLabelText})`}
              movies={recommendedMovies}
              viewAllTo={recommendedViewAllTo}
              viewAllLabel="더보기"
            />
          )}
        </>
      )}
    </div>
  )
}

export default HomePage
