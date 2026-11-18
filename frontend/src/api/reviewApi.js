import request from './httpClient'

// userId(선택): 로그인한 사용자 - 각 리뷰에 "내가 이미 추천했는지"(helpfulByMe)를 함께 받으려면 넘깁니다.
export function getReviews(movieId, userId) {
  return request(`/api/movies/${encodeURIComponent(movieId)}/reviews`, { userId })
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

// 본인이 작성하지 않은 리뷰 신고 - reason은 reviewReportReasons.js의 key 중 하나입니다.
export function reportReview(movieId, reviewId, userId, reason) {
  return request(`/api/movies/${encodeURIComponent(movieId)}/reviews/${reviewId}/report`, {
    method: 'POST',
    userId,
    body: { reason },
  })
}

// 본인이 작성하지 않은 리뷰를 "도움이 됐어요"로 추천하거나(다시 누르면 취소) - 신고(reportReview)와
// 대칭되는 긍정적 상호작용입니다. 갱신된 추천 수/내 추천 여부를 바로 돌려받습니다.
export function toggleReviewHelpful(movieId, reviewId, userId) {
  return request(`/api/movies/${encodeURIComponent(movieId)}/reviews/${reviewId}/helpful`, {
    method: 'POST',
    userId,
  })
}

// 마이페이지 "내가 쓴 리뷰" 탭 - 영화 구분 없이 이 사용자가 쓴 모든 리뷰.
export function getMyReviews(userId) {
  return request('/api/reviews/mine', { userId })
}

// 관리자 "리뷰 관리" 화면 - DB에 저장된 모든 사용자의 리뷰 전체.
export function getAllReviewsForAdmin(adminUserId) {
  return request('/api/admin/reviews', { userId: adminUserId })
}

export function deleteReviewAsAdmin(reviewId, adminUserId) {
  return request(`/api/admin/reviews/${reviewId}`, { method: 'DELETE', userId: adminUserId })
}

// 관리자 알림 벨의 리뷰 신고 알림 "보러가기" - 신고된 리뷰 내용/작성자와 신고 사유/신고자 상세.
export function getReviewReportDetail(adminUserId, reportId) {
  return request(`/api/admin/review-reports/${reportId}`, { userId: adminUserId })
}
