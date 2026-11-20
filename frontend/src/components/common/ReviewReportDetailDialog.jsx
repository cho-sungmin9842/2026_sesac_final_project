import { useState } from 'react'
import { deleteReviewAsAdmin, cancelReviewDeletion } from '../../api/reviewApi'
import { BASE_URL } from '../../api/httpClient'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

function formatDateTime(isoString) {
  const date = new Date(isoString)
  const yyyy = date.getFullYear()
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  const weekday = WEEKDAYS[date.getDay()]
  const hh = String(date.getHours()).padStart(2, '0')
  const min = String(date.getMinutes()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}(${weekday}) ${hh}:${min}`
}

// 관리자 알림 벨의 리뷰 신고 알림 "보러가기" - 신고된 리뷰 내용/작성자와 신고 사유/신고자를 한 번에
// 보여줍니다. loading/error/detail 중 하나만 전달되는 상태 기계입니다(NotificationBell.jsx 참고).
// adminUserId가 주어지면 그 자리에서 바로 "리뷰 삭제"/"리뷰 삭제 취소"를 선택할 수 있는 버튼을 보여줍니다 -
// 둘 다 신고자에게 처리 결과를 알리는 서로 다른 알림으로 이어집니다(ReviewService 참고).
function ReviewReportDetailDialog({ loading, error, detail, adminUserId, onClose, onResolved }) {
  const [acting, setActing] = useState(null) // null | 'delete' | 'cancel'
  const [actionError, setActionError] = useState('')
  const [resolution, setResolution] = useState(null) // null | 'deleted' | 'cancelled'

  const handleDeleteReview = () => {
    if (!window.confirm('이 리뷰를 삭제할까요? 삭제하면 되돌릴 수 없습니다.')) return
    setActing('delete')
    setActionError('')
    deleteReviewAsAdmin(detail.reviewId, adminUserId)
      .then(() => {
        setResolution('deleted')
        onResolved?.()
      })
      .catch((err) => setActionError(err.message))
      .finally(() => setActing(null))
  }

  const handleCancelDeletion = () => {
    setActing('cancel')
    setActionError('')
    cancelReviewDeletion(adminUserId, detail.id)
      .then(() => {
        setResolution('cancelled')
        onResolved?.()
      })
      .catch((err) => setActionError(err.message))
      .finally(() => setActing(null))
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
      <div className="relative w-full max-w-md rounded-xl bg-slate-900 p-6">
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className="absolute right-4 top-4 flex h-7 w-7 items-center justify-center rounded-full border border-slate-700 text-gray-400 hover:border-rose-500 hover:bg-rose-500 hover:text-white"
        >
          ✕
        </button>
        <h2 className="text-sm font-semibold text-white">리뷰 신고 상세</h2>

        {loading && <p className="mt-4 text-sm text-gray-400">불러오는 중...</p>}

        {error && !loading && <p className="mt-4 text-sm text-rose-400">⚠ {error}</p>}

        {detail && !loading && !error && (
          <div className="mt-4 space-y-3 text-sm">
            <div>
              <p className="text-xs text-gray-500">영화</p>
              <p className="font-semibold text-gray-100">{detail.movieTitle}</p>
            </div>

            <div className="rounded-lg bg-slate-800 p-3">
              <div className="flex items-center justify-between text-xs text-gray-500">
                <span>신고된 리뷰 (작성자: {detail.reviewAuthorNickname})</span>
                <span className="text-amber-400">
                  {'★'.repeat(detail.reviewScore)}
                  <span className="text-gray-600">{'★'.repeat(5 - detail.reviewScore)}</span>
                </span>
              </div>
              <p className="mt-1.5 whitespace-pre-wrap text-gray-200">{detail.reviewContent}</p>
              {/* 사진이 저장된 리뷰일 때만 보여주고, 없으면 이 영역 자체를 렌더링하지 않습니다. */}
              {detail.reviewPhotoUrl && (
                <img
                  src={`${BASE_URL}${detail.reviewPhotoUrl}`}
                  alt="신고된 리뷰 첨부 사진"
                  className="mt-2 max-h-64 max-w-full rounded-lg bg-black object-contain"
                />
              )}
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <p className="text-xs text-gray-500">신고 사유</p>
                <p className="font-semibold text-rose-400">{detail.reasonLabel}</p>
              </div>
              <div>
                <p className="text-xs text-gray-500">신고자</p>
                <p className="font-semibold text-gray-100">{detail.reporterNickname}</p>
              </div>
            </div>

            <p className="text-xs text-gray-500">신고 시각: {formatDateTime(detail.reportedAt)}</p>

            {resolution && (
              <p className="rounded-lg bg-emerald-500/10 p-3 text-sm font-semibold text-emerald-400">
                {resolution === 'deleted' ? '리뷰를 삭제했습니다.' : '리뷰 삭제를 취소했습니다.'}
              </p>
            )}
            {actionError && <p className="text-sm text-rose-400">⚠ {actionError}</p>}
          </div>
        )}

        {detail && !loading && !error && !resolution && adminUserId != null && (
          <div className="mt-4 flex gap-2">
            <button
              type="button"
              disabled={acting !== null}
              onClick={handleCancelDeletion}
              className="flex-1 rounded-lg border border-slate-700 py-2 text-sm font-semibold text-gray-300 hover:bg-slate-800 disabled:opacity-50"
            >
              {acting === 'cancel' ? '처리 중...' : '리뷰 삭제 취소'}
            </button>
            <button
              type="button"
              disabled={acting !== null}
              onClick={handleDeleteReview}
              className="flex-1 rounded-lg bg-rose-600 py-2 text-sm font-semibold text-white hover:bg-rose-500 disabled:opacity-50"
            >
              {acting === 'delete' ? '삭제 중...' : '리뷰 삭제'}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

export default ReviewReportDetailDialog
