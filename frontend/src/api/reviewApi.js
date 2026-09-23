import request from './httpClient'

export function getReviews(movieId) {
  return request(`/api/movies/${encodeURIComponent(movieId)}/reviews`)
}

export function addReview(movieId, userId, { movieTitle, score, content }) {
  return request(`/api/movies/${encodeURIComponent(movieId)}/reviews`, {
    method: 'POST',
    userId,
    body: { movieTitle, score, content },
  })
}

export function updateReview(movieId, reviewId, userId, { movieTitle, score, content }) {
  return request(`/api/movies/${encodeURIComponent(movieId)}/reviews/${reviewId}`, {
    method: 'PUT',
    userId,
    body: { movieTitle, score, content },
  })
}

export function deleteReview(movieId, reviewId, userId) {
  return request(`/api/movies/${encodeURIComponent(movieId)}/reviews/${reviewId}`, {
    method: 'DELETE',
    userId,
  })
}

// 마이페이지 "내가 쓴 리뷰" 탭 - 영화 구분 없이 이 사용자가 쓴 모든 리뷰.
export function getMyReviews(userId) {
  return request('/api/reviews/mine', { userId })
}
