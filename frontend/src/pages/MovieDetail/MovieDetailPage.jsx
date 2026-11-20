import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { useWishlist } from '../../wishlist/WishlistContext'
import { getMovieDetail } from '../../api/movieApi'
import {
  addReview,
  deleteReview,
  getReviews,
  removeReviewPhoto,
  reportReview,
  toggleReviewHelpful,
  updateReview,
  uploadReviewPhoto,
} from '../../api/reviewApi'
import { addWatched, isWatched, removeWatched } from '../../api/watchedApi'
import { recordMovieView } from '../../api/recentlyViewedApi'
import AiSummaryBox from './components/AiSummaryBox'
import OtherVideoButtons from './components/OtherVideoButtons'
import PosterLink from './components/PosterLink'
import ReviewList from './components/ReviewList'
import ReviewReportDialog from './components/ReviewReportDialog'
import StillGallery from './components/StillGallery'
import WriteReviewDialog from './components/WriteReviewDialog'
import { normalizeDetail } from './normalizeDetail'
import ConfirmDialog from '../../components/common/ConfirmDialog'
import InfoDialog from '../../components/common/InfoDialog'
import SortDropdown from '../MovieList/components/SortDropdown'

// 백엔드 에러 메시지가 이미 "영화를 찾을 수 없습니다: <id>" 형태라, 화면에 그대로 쓰면 아래 헤딩과
// 겹쳐서 문구가 두 번 보입니다. 그 접두어를 떼고 상세 내용(영화 id)만 보조 텍스트로 보여줍니다.
const NOT_FOUND_PREFIX = '영화를 찾을 수 없습니다: '

function stripNotFoundPrefix(message) {
  return message.startsWith(NOT_FOUND_PREFIX) ? message.slice(NOT_FOUND_PREFIX.length) : message
}

const REVIEW_SORT_OPTIONS = [
  { value: 'latest', label: '최신순' },
  { value: 'rating', label: '평점순' },
  { value: 'helpful', label: '추천순' },
]

function MovieDetailPage() {
  const { id } = useParams()
  // 리뷰 "도움이 됐어요" 알림을 눌러 들어오면 ?reviewId=로 어떤 리뷰인지 넘어옵니다 - 리뷰 목록이
  // 뜨면 그 리뷰까지 스크롤하고 잠깐 하이라이트합니다.
  const [searchParams] = useSearchParams()
  const highlightedReviewId = searchParams.get('reviewId')
  const { user } = useAuth()
  const { wishlistedIds, toggle: toggleWishlist } = useWishlist()
  const [status, setStatus] = useState('loading') // loading | ready | error
  const [detail, setDetail] = useState(null)
  const [errorMessage, setErrorMessage] = useState('')
  const [reviews, setReviews] = useState([])
  const [isDialogOpen, setDialogOpen] = useState(false)
  const [editingReview, setEditingReview] = useState(null)
  const [watched, setWatched] = useState(false)
  const [pendingDeleteReview, setPendingDeleteReview] = useState(null)
  const [deleteErrorMessage, setDeleteErrorMessage] = useState('')
  const [reviewSort, setReviewSort] = useState('latest')
  const [pendingReportReview, setPendingReportReview] = useState(null)
  // 이번 화면 접속 동안 신고 처리된 리뷰 id 모음(새로고침하면 초기화) - ReviewList의 "신고 완료" 표시용.
  const [reportedReviewIds, setReportedReviewIds] = useState(new Set())
  const [reportDialog, setReportDialog] = useState(null) // { message, tone }

  const wishlisted = wishlistedIds.has(id)

  useEffect(() => {
    let cancelled = false
    setStatus('loading')

    Promise.all([getMovieDetail(id), getReviews(id, user.id), isWatched(user.id, id)])
      .then(([dto, reviewList, watchedFlag]) => {
        if (cancelled) return
        const normalized = normalizeDetail(dto)
        setDetail(normalized)
        setReviews(reviewList)
        setWatched(watchedFlag)
        setStatus('ready')
        // 홈 화면 "최근 본 영화" 섹션용 기록 - 실패해도 상세 페이지 열람 자체에는 영향 없어야 하므로 조용히 무시합니다.
        recordMovieView(user.id, { movieId: id, movieTitle: normalized.title, posterUrl: normalized.posterUrl }).catch(() => {})
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

  // 리뷰 목록이 뜨고 나서야 실제 DOM에 해당 리뷰가 존재하므로, status가 ready로 바뀐 뒤에 스크롤합니다.
  useEffect(() => {
    if (status !== 'ready' || !highlightedReviewId) return
    const element = document.getElementById(`review-${highlightedReviewId}`)
    if (!element) return
    element.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }, [status, highlightedReviewId])

  const handleToggleWishlist = () => {
    toggleWishlist({ id, title: detail.title, posterUrl: detail.posterUrl })
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

  const handleReviewSubmit = async ({ score, content, photoFile, photoRemoved }) => {
    try {
      const saved = editingReview
        ? await updateReview(id, editingReview.id, user.id, { movieTitle: detail.title, score, content })
        : await addReview(id, user.id, { movieTitle: detail.title, score, content })
      // 사진은 리뷰 자체가 저장되어 reviewId가 확정된 뒤에야 올리거나 뗄 수 있어서, 필요할 때만 이어서
      // 호출합니다(실패해도 리뷰 자체는 이미 저장됐으니, 사진 처리만 빠진 상태로 안내하고 넘어갑니다).
      // photoFile(새 사진)이 photoRemoved(기존 사진 떼기)보다 우선입니다 - 다이얼로그에서 미리보기가
      // 새 사진으로 바뀐 순간 photoRemoved는 이미 false로 돌아가므로 실제로 겹칠 일은 없습니다.
      if (photoFile) {
        try {
          await uploadReviewPhoto(id, saved.id, user.id, photoFile)
        } catch (photoError) {
          window.alert(`리뷰는 저장됐지만 사진 첨부에 실패했습니다: ${photoError.message}`)
        }
      } else if (photoRemoved && saved.photoUrl) {
        try {
          await removeReviewPhoto(id, saved.id, user.id)
        } catch (photoError) {
          window.alert(`리뷰는 저장됐지만 사진 삭제에 실패했습니다: ${photoError.message}`)
        }
      }
      setReviews(await getReviews(id, user.id))
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

  const handleReviewDelete = (review) => {
    setPendingDeleteReview(review)
  }

  const confirmReviewDelete = async () => {
    const review = pendingDeleteReview
    setPendingDeleteReview(null)
    try {
      await deleteReview(id, review.id, user.id)
      setReviews(await getReviews(id, user.id))
    } catch (error) {
      setDeleteErrorMessage(error.message)
    }
  }

  const closeDialog = () => {
    setDialogOpen(false)
    setEditingReview(null)
  }

  const handleReviewReport = (review) => {
    setPendingReportReview(review)
  }

  const confirmReviewReport = async (reasonKey) => {
    const review = pendingReportReview
    setPendingReportReview(null)
    try {
      await reportReview(id, review.id, user.id, reasonKey)
      setReportedReviewIds((prev) => new Set(prev).add(review.id))
      setReportDialog({ message: '신고가 접수되었습니다. 관리자가 확인 후 처리합니다.' })
    } catch (error) {
      setReportDialog({ message: error.message, tone: 'error' })
    }
  }

  // 리뷰 "도움이 됐어요" - 서버가 토글 결과(갱신된 추천 수/내 추천 여부)를 바로 돌려줘서, 전체
  // 리뷰 목록을 다시 불러오지 않고 그 리뷰 하나만 로컬 상태에서 갱신합니다.
  const handleToggleHelpful = async (review) => {
    try {
      const { helpfulCount, helpfulByMe } = await toggleReviewHelpful(id, review.id, user.id)
      setReviews((prev) =>
        prev.map((candidate) => (candidate.id === review.id ? { ...candidate, helpfulCount, helpfulByMe } : candidate))
      )
    } catch (error) {
      window.alert(error.message)
    }
  }

  const hasReviewed = reviews.some((review) => review.userId === user?.id)

  const sortedReviews = [...reviews].sort((a, b) => {
    if (reviewSort === 'rating') {
      const scoreDiff = (b.score ?? -1) - (a.score ?? -1)
      if (scoreDiff !== 0) return scoreDiff
    } else if (reviewSort === 'helpful') {
      const helpfulDiff = (b.helpfulCount ?? 0) - (a.helpfulCount ?? 0)
      if (helpfulDiff !== 0) return helpfulDiff
    }
    return new Date(b.createdAt) - new Date(a.createdAt)
  })

  const scoredReviews = reviews.filter((review) => typeof review.score === 'number')
  const averageScore =
    scoredReviews.length > 0
      ? scoredReviews.reduce((sum, review) => sum + review.score, 0) / scoredReviews.length
      : null

  if (status === 'loading') {
    return <p className="p-6 text-gray-400">불러오는 중...</p>
  }

  if (status === 'error' || !detail) {
    return (
      <div className="mx-auto flex max-w-md flex-col items-center gap-3 px-6 py-24 text-center">
        <span className="text-4xl">🎬</span>
        <h1 className="text-lg font-semibold text-white">영화를 찾을 수 없습니다</h1>
        <p className="text-sm text-gray-500">{stripNotFoundPrefix(errorMessage)}</p>
        <Link
          to="/movies"
          className="mt-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
        >
          영화 목록으로 돌아가기
        </Link>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-6 sm:px-6">
      <div className="flex flex-col gap-6 sm:flex-row">
        <PosterLink posterUrls={detail.posterUrls} video={detail.video} title={detail.title} />

        <div className="min-w-0 flex-1">
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
            {detail.tags.length > 0 ? (
              detail.tags.map((tag) => (
                <span
                  key={tag}
                  className="rounded-full bg-indigo-500/10 px-3 py-1 text-xs font-medium text-indigo-300"
                >
                  #{tag}
                </span>
              ))
            ) : (
              <span className="text-sm text-gray-500">관련 키워드 없음</span>
            )}
          </div>

          <p className="mt-4 max-w-2xl text-sm leading-relaxed text-gray-300">{detail.plot}</p>

          <div className="mt-5 flex flex-col gap-3 sm:flex-row sm:flex-wrap">
            <AiSummaryBox movieId={id} movieTitle={detail.title} />
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

          <OtherVideoButtons videos={detail.otherVideos} />
        </div>
      </div>

      <StillGallery stillUrls={detail.stillUrls} />

      <div className="mt-10 flex gap-8">
        <section className="flex-1">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-bold text-gray-100">사용자 리뷰{reviews.length > 0 ? ` (${reviews.length})` : ''}</h2>
            <div className="flex items-center gap-2">
              {reviews.length > 0 && (
                <SortDropdown value={reviewSort} options={REVIEW_SORT_OPTIONS} onChange={setReviewSort} />
              )}
              <button
                type="button"
                onClick={() => setDialogOpen(true)}
                disabled={hasReviewed}
                title={hasReviewed ? '이미 작성한 리뷰가 있습니다. 기존 리뷰를 수정해주세요.' : undefined}
                className={`rounded-lg px-4 py-2 text-sm font-semibold ${
                  hasReviewed
                    ? 'cursor-not-allowed bg-slate-700 text-gray-400'
                    : 'bg-indigo-600 text-white hover:bg-indigo-500'
                }`}
              >
                리뷰 쓰기
              </button>
            </div>
          </div>
          {reviews.length > 0 ? (
            <ReviewList
              reviews={sortedReviews}
              currentUser={user?.id}
              onEdit={handleReviewEdit}
              onDelete={handleReviewDelete}
              onReport={handleReviewReport}
              reportedReviewIds={reportedReviewIds}
              onToggleHelpful={handleToggleHelpful}
              highlightedReviewId={highlightedReviewId}
            />
          ) : (
            <p className="text-sm text-gray-500">아직 등록된 리뷰가 없습니다.</p>
          )}
        </section>
      </div>

      {isDialogOpen && (
        <WriteReviewDialog
          movieTitle={detail.title}
          initialReview={editingReview}
          onSubmit={handleReviewSubmit}
          onClose={closeDialog}
        />
      )}

      {pendingDeleteReview && (
        <ConfirmDialog
          title="리뷰 삭제"
          message="이 리뷰를 삭제할까요?"
          confirmLabel="삭제"
          danger
          onConfirm={confirmReviewDelete}
          onCancel={() => setPendingDeleteReview(null)}
        />
      )}

      {deleteErrorMessage && (
        <InfoDialog title="리뷰 삭제 실패" message={deleteErrorMessage} tone="error" onClose={() => setDeleteErrorMessage('')} />
      )}

      {pendingReportReview && (
        <ReviewReportDialog onConfirm={confirmReviewReport} onCancel={() => setPendingReportReview(null)} />
      )}

      {reportDialog && (
        <InfoDialog
          title={reportDialog.tone === 'error' ? '신고 실패' : '신고 완료'}
          message={reportDialog.message}
          tone={reportDialog.tone}
          onClose={() => setReportDialog(null)}
        />
      )}
    </div>
  )
}

export default MovieDetailPage
