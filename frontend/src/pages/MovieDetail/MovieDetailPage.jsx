import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { getMovieDetail } from '../../api/movieApi'
import { addReview, deleteReview, getReviews, updateReview } from '../../api/reviewApi'
import { addWishlist, isWishlisted, removeWishlist } from '../../api/wishlistApi'
import { addWatched, isWatched, removeWatched } from '../../api/watchedApi'
import AiSummaryBox from './components/AiSummaryBox'
import PosterLink from './components/PosterLink'
import ReviewList from './components/ReviewList'
import WriteReviewDialog from './components/WriteReviewDialog'
import { normalizeDetail } from './normalizeDetail'

function MovieDetailPage() {
  const { id } = useParams()
  const { user } = useAuth()
  const [status, setStatus] = useState('loading') // loading | ready | error
  const [detail, setDetail] = useState(null)
  const [errorMessage, setErrorMessage] = useState('')
  const [reviews, setReviews] = useState([])
  const [isDialogOpen, setDialogOpen] = useState(false)
  const [editingReview, setEditingReview] = useState(null)
  const [wishlisted, setWishlisted] = useState(false)
  const [watched, setWatched] = useState(false)

  useEffect(() => {
    let cancelled = false
    setStatus('loading')

    Promise.all([getMovieDetail(id), getReviews(id), isWishlisted(user.id, id), isWatched(user.id, id)])
      .then(([dto, reviewList, wishlistedFlag, watchedFlag]) => {
        if (cancelled) return
        setDetail(normalizeDetail(dto))
        setReviews(reviewList)
        setWishlisted(wishlistedFlag)
        setWatched(watchedFlag)
        setStatus('ready')
      })
      .catch((error) => {
        if (cancelled) return
        setErrorMessage(error.message)
        setStatus('error')
      })

    return () => {
      cancelled = true
    }
  }, [id, user.id])

  const handleToggleWishlist = async () => {
    try {
      if (wishlisted) {
        await removeWishlist(user.id, id)
        setWishlisted(false)
      } else {
        await addWishlist(user.id, { movieId: id, movieTitle: detail.title, posterUrl: detail.posterUrl })
        setWishlisted(true)
      }
    } catch (error) {
      window.alert(error.message)
    }
  }

  const handleToggleWatched = async () => {
    try {
      if (watched) {
        await removeWatched(user.id, id)
        setWatched(false)
      } else {
        await addWatched(user.id, { movieId: id, movieTitle: detail.title, posterUrl: detail.posterUrl })
        setWatched(true)
      }
    } catch (error) {
      window.alert(error.message)
    }
  }

  const handleReviewSubmit = async ({ score, content }) => {
    try {
      if (editingReview) {
        await updateReview(id, editingReview.id, user.id, { movieTitle: detail.title, score, content })
      } else {
        await addReview(id, user.id, { movieTitle: detail.title, score, content })
      }
      setReviews(await getReviews(id))
      setDialogOpen(false)
      setEditingReview(null)
    } catch (error) {
      window.alert(error.message)
    }
  }

  const handleReviewEdit = (review) => {
    setEditingReview(review)
    setDialogOpen(true)
  }

  const handleReviewDelete = async (review) => {
    if (!window.confirm('이 리뷰를 삭제할까요?')) return
    try {
      await deleteReview(id, review.id, user.id)
      setReviews(await getReviews(id))
    } catch (error) {
      window.alert(error.message)
    }
  }

  const closeDialog = () => {
    setDialogOpen(false)
    setEditingReview(null)
  }

  const scoredReviews = reviews.filter((review) => typeof review.score === 'number')
  const averageScore =
    scoredReviews.length > 0
      ? scoredReviews.reduce((sum, review) => sum + review.score, 0) / scoredReviews.length
      : null

  if (status === 'loading') {
    return <p className="p-6 text-gray-400">불러오는 중...</p>
  }

  if (status === 'error' || !detail) {
    return <p className="p-6 text-gray-400">영화를 찾을 수 없습니다: {errorMessage}</p>
  }

  return (
    <div className="mx-auto max-w-6xl px-6 py-6">
      <div className="flex gap-6">
        <PosterLink posterUrl={detail.posterUrl} video={detail.video} title={detail.title} />

        <div className="flex-1">
          <h1 className="flex items-baseline gap-2 text-2xl font-bold text-white">
            {detail.title}
            <span className="text-base font-normal text-gray-400">{detail.year}</span>
          </h1>
          <p className="mt-1 text-sm text-gray-400">
            {detail.directorsLabel} 감독 · {detail.castLabel} 출연
            {detail.runtimeMinutes ? ` · ${detail.runtimeMinutes}분` : ''}
            {detail.ageRating ? ` · ${detail.ageRating}` : ''}
          </p>

          {averageScore != null ? (
            <p className="mt-3 flex items-center gap-2 text-lg font-bold text-amber-400">
              ★ {averageScore.toFixed(1)}
              <span className="text-sm font-normal text-gray-400">
                사용자 평점 · 리뷰 {scoredReviews.length}개
              </span>
            </p>
          ) : (
            <p className="mt-3 text-sm text-gray-500">아직 평점이 없어요, 첫 리뷰를 남겨보세요!</p>
          )}

          <div className="mt-3 flex flex-wrap gap-2">
            {detail.tags.map((tag) => (
              <span
                key={tag}
                className="rounded-full bg-indigo-500/10 px-3 py-1 text-xs font-medium text-indigo-300"
              >
                #{tag}
              </span>
            ))}
          </div>

          <p className="mt-4 max-w-2xl text-sm leading-relaxed text-gray-300">{detail.plot}</p>

          <div className="mt-5 flex gap-3">
            <button
              type="button"
              className="rounded-lg bg-indigo-500/10 px-4 py-2 text-sm font-semibold text-indigo-300 hover:bg-indigo-500/20"
            >
              ✨ AI 줄거리 요약 (스포방지)
            </button>
            <button
              type="button"
              onClick={handleToggleWishlist}
              className={`rounded-lg px-4 py-2 text-sm font-semibold ${
                wishlisted
                  ? 'bg-pink-500/20 text-pink-300 hover:bg-pink-500/30'
                  : 'bg-indigo-600 text-white hover:bg-indigo-500'
              }`}
            >
              {wishlisted ? '♥ 찜한 영화' : '♡ 찜하기'}
            </button>
            <button
              type="button"
              onClick={handleToggleWatched}
              className={`rounded-lg border px-4 py-2 text-sm font-semibold ${
                watched
                  ? 'border-emerald-600 bg-emerald-500/10 text-emerald-300 hover:bg-emerald-500/20'
                  : 'border-gray-700 text-gray-200 hover:bg-slate-800'
              }`}
            >
              {watched ? '✓ 시청완료' : '시청완료 표시'}
            </button>
          </div>
        </div>
      </div>

      <div className="mt-10 flex gap-8">
        <section className="flex-1">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-bold text-gray-100">사용자 리뷰{reviews.length > 0 ? ` (${reviews.length})` : ''}</h2>
            <button
              type="button"
              onClick={() => setDialogOpen(true)}
              className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
            >
              리뷰 쓰기
            </button>
          </div>
          {reviews.length > 0 ? (
            <ReviewList
              reviews={reviews}
              currentUser={user?.id}
              onEdit={handleReviewEdit}
              onDelete={handleReviewDelete}
            />
          ) : (
            <p className="text-sm text-gray-500">아직 등록된 리뷰가 없습니다.</p>
          )}
        </section>

        {detail.aiSummary && <AiSummaryBox summary={detail.aiSummary} />}
      </div>

      {isDialogOpen && (
        <WriteReviewDialog
          movieTitle={detail.title}
          initialReview={editingReview}
          onSubmit={handleReviewSubmit}
          onClose={closeDialog}
        />
      )}
    </div>
  )
}

export default MovieDetailPage
