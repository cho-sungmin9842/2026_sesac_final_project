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
 * 응답은 { movies, page, pageSize, totalCount, totalPages } 형태입니다.
 */
export function searchMovies(query, { genre, year, page = 1, pageSize = 20 } = {}) {
  const params = new URLSearchParams({ query, page, pageSize })
  if (genre) params.set('genre', genre)
  if (year) params.set('year', year)
  return request(`/api/movies?${params.toString()}`)
}

export function getMovieDetail(id) {
  return request(`/api/movies/${encodeURIComponent(id)}`)
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
