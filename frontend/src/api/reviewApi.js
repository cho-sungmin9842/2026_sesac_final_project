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

// 본인 리뷰에 사진을 첨부합니다(다시 올리면 이전 사진을 대체) - 리뷰를 쓰거나 수정한 직후, 사진을
// 골랐을 때만 이어서 호출합니다. 갱신된 리뷰(photoUrl 포함)를 돌려받습니다.
export function uploadReviewPhoto(movieId, reviewId, userId, photoFile) {
  const formData = new FormData()
  formData.append('photo', photoFile)
  return request(`/api/movies/${encodeURIComponent(movieId)}/reviews/${reviewId}/photo`, {
    method: 'POST',
    userId,
    body: formData,
  })
}

// 수정 중 사진 선택 창을 열었다가 취소해 미리보기를 비운 경우 - 새 사진을 올리는 대신 기존 사진을 뗍니다.
export function removeReviewPhoto(movieId, reviewId, userId) {
  return request(`/api/movies/${encodeURIComponent(movieId)}/reviews/${reviewId}/photo`, {
    method: 'DELETE',
    userId,
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

// 관리자 "리뷰 관리" 화면 - 검색/정렬/페이지네이션은 서버에서 처리합니다. page는 0부터 시작합니다
// (Pageable 관례 - AdminReportsPage의 1-based 화면 페이지 번호를 호출 시 변환해서 넘겨줍니다).
export function getAllReviewsForAdmin(adminUserId, params = {}) {
  const query = new URLSearchParams()
  if (params.page != null) query.set('page', params.page)
  if (params.size != null) query.set('size', params.size)
  if (params.sort) query.set('sort', params.sort)
  if (params.movieTitle) query.set('movieTitle', params.movieTitle)
  if (params.authorNickname) query.set('authorNickname', params.authorNickname)
  if (params.reportedOnly) query.set('reportedOnly', 'true')
  if (params.dateFrom) query.set('dateFrom', params.dateFrom)
  if (params.dateTo) query.set('dateTo', params.dateTo)
  if (params.content) query.set('content', params.content)
  const queryString = query.toString()
  return request(`/api/admin/reviews${queryString ? `?${queryString}` : ''}`, { userId: adminUserId })
}

// "전체 선택"(페이지를 넘나드는 선택) - 지금 필터에 맞는 리뷰 전체의 id만 가볍게 받아옵니다.
export function getAdminReviewIds(adminUserId, params = {}) {
  const query = new URLSearchParams()
  if (params.movieTitle) query.set('movieTitle', params.movieTitle)
  if (params.authorNickname) query.set('authorNickname', params.authorNickname)
  if (params.reportedOnly) query.set('reportedOnly', 'true')
  if (params.dateFrom) query.set('dateFrom', params.dateFrom)
  if (params.dateTo) query.set('dateTo', params.dateTo)
  if (params.content) query.set('content', params.content)
  const queryString = query.toString()
  return request(`/api/admin/reviews/ids${queryString ? `?${queryString}` : ''}`, { userId: adminUserId })
}

// "리뷰 관리" 필터 드롭다운(영화 제목/작성자 닉네임) 선택지 - 실제로 리뷰가 존재하는 조합만.
export function getAdminReviewFilterOptions(adminUserId) {
  return request('/api/admin/reviews/filter-options', { userId: adminUserId })
}

// "리뷰 관리" 상단 통계 카드("전체 리뷰"/"오늘 작성된 리뷰") - 검색/필터와 무관하게 DB 전체 기준.
export function getAdminReviewStats(adminUserId) {
  return request('/api/admin/reviews/stats', { userId: adminUserId })
}

export function deleteReviewAsAdmin(reviewId, adminUserId) {
  return request(`/api/admin/reviews/${reviewId}`, { method: 'DELETE', userId: adminUserId })
}

// "리뷰 관리" 테이블의 "신고 N건" 뱃지 - 이 리뷰에 달린 신고 전부(사유/신고자/시각)를 받아옵니다.
export function getReportsForReview(adminUserId, reviewId) {
  return request(`/api/admin/reviews/${reviewId}/reports`, { userId: adminUserId })
}

// "리뷰 관리" 테이블의 일괄 선택 삭제 - 실패한 건(이미 삭제됨 등)이 있으면 "id: 사유" 문자열 목록을 돌려받습니다.
export function bulkDeleteReviewsAsAdmin(reviewIds, adminUserId) {
  return request('/api/admin/reviews/bulk-delete', { method: 'POST', userId: adminUserId, body: reviewIds })
}

// "리뷰 관리" 화면의 "삭제 이력" 탭 - 최근 삭제된 것부터.
export function getReviewDeletionLogs(adminUserId) {
  return request('/api/admin/reviews/deletion-logs', { userId: adminUserId })
}

// 관리자 알림 벨의 리뷰 신고 알림 "보러가기" - 신고된 리뷰 내용/작성자와 신고 사유/신고자 상세.
export function getReviewReportDetail(adminUserId, reportId) {
  return request(`/api/admin/review-reports/${reportId}`, { userId: adminUserId })
}

// 신고 상세 다이얼로그의 "리뷰 삭제 취소" - 리뷰는 그대로 두고 이 신고 건만 없앱니다.
export function cancelReviewDeletion(adminUserId, reportId) {
  return request(`/api/admin/review-reports/${reportId}`, { method: 'DELETE', userId: adminUserId })
}
