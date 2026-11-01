import request from './httpClient'

// 홈 화면 "최근 본 영화" 섹션용 - 최근에 조회한 순서대로 최대 limit개.
export function getRecentlyViewed(userId, limit = 10) {
  return request(`/api/recently-viewed?limit=${limit}`, { userId })
}

// 영화 상세 페이지를 열 때마다 호출합니다. 이미 본 영화면 서버에서 시간만 갱신합니다.
export function recordMovieView(userId, { movieId, movieTitle, posterUrl }) {
  return request('/api/recently-viewed', { method: 'POST', userId, body: { movieId, movieTitle, posterUrl } })
}
