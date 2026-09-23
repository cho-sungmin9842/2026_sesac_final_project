const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

async function request(path) {
  const response = await fetch(`${BASE_URL}${path}`)
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message ?? `요청이 실패했습니다 (status ${response.status})`)
  }
  return response.json()
}

/**
 * query가 빈 문자열이면 KMDB 전체 카탈로그를 대상으로 검색합니다(제목 필터 없음).
 * genre/year는 KMDB가 실제로 지원하는 필터라 서버(KMDB)에서 걸러진 결과가 옵니다.
 * genre는 문자열 하나 또는 배열(여러 장르 동시 필터, 예: 선호 장르가 여러 개인 경우) 둘 다 받습니다.
 * sort(latest/name)는 KMDB에 정렬 파라미터가 없어서 백엔드가 여러 페이지 분량을 미리 받아와 직접 정렬합니다.
 * 응답은 { movies, page, pageSize, totalCount, totalPages } 형태입니다.
 */
export function searchMovies(query, { genre, year, sort, page = 1, pageSize = 20 } = {}) {
  const params = new URLSearchParams({ query, page, pageSize })
  const genreList = Array.isArray(genre) ? genre.filter(Boolean) : [genre].filter(Boolean)
  genreList.forEach((value) => params.append('genre', value))
  if (year) params.set('year', year)
  if (sort) params.set('sort', sort)
  return request(`/api/movies?${params.toString()}`)
}

export function getMovieDetail(id) {
  return request(`/api/movies/${encodeURIComponent(id)}`)
}

/**
 * 예매 화면의 "상영중인 영화" 목록. 오늘 기준 최근 2개월 내 개봉일자(releaseDts~releaseDte)인 KMDB 영화를 가져옵니다.
 */
export function getNowShowing(listCount = 10) {
  return request(`/api/movies/now-showing?listCount=${listCount}`)
}

/**
 * KMDB는 "인기순"/"추천" 개념이 없어서, 제목 목록을 하나씩 검색해 첫 번째 결과만 모아
 * 화면에 보여줄 진열대를 구성합니다. 검색이 실패하거나 결과가 없는 제목은 건너뜁니다.
 */
export async function getFirstMatches(titles) {
  const results = await Promise.all(
    titles.map((title) =>
      searchMovies(title, { pageSize: 1 })
        .then((result) => result.movies[0] ?? null)
        .catch(() => null),
    ),
  )
  return results.filter(Boolean)
}
