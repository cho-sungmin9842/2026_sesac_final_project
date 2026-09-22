// 리뷰 작성 백엔드가 아직 없어서, 영화 id별 리뷰 목록을 브라우저(localStorage)에만 저장합니다.
const STORAGE_KEY = 'moviepick_reviews'

function readAll() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY)) ?? {}
  } catch {
    return {}
  }
}

function writeAll(data) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
}

export function getReviews(movieId) {
  return readAll()[movieId] ?? []
}

export function addReview(movieId, { author, score, content }) {
  const all = readAll()
  const review = {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    author,
    score,
    content,
    createdAt: new Date().toISOString(),
  }
  all[movieId] = [review, ...(all[movieId] ?? [])]
  writeAll(all)
  return review
}

export function updateReview(movieId, reviewId, { score, content }) {
  const all = readAll()
  all[movieId] = (all[movieId] ?? []).map((review) =>
    review.id === reviewId
      ? { ...review, score, content, updatedAt: new Date().toISOString() }
      : review,
  )
  writeAll(all)
  return getReviews(movieId).find((review) => review.id === reviewId)
}

export function deleteReview(movieId, reviewId) {
  const all = readAll()
  all[movieId] = (all[movieId] ?? []).filter((review) => review.id !== reviewId)
  writeAll(all)
}
