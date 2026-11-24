import { useEffect, useState } from 'react'
import { cancelReviewDeletion, deleteReviewAsAdmin, getReportsForReview } from '../../../api/reviewApi'
import { BASE_URL } from '../../../api/httpClient'

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

// "리뷰 관리" 테이블의 "신고 N건" 뱃지를 눌렀을 때 - 같은 리뷰에 달린 신고 전부를 한 번에 보여줍니다.
// ReviewReportDetailDialog(알림벨의 "보러가기")와 달리 신고가 여러 건일 수 있어서, 신고 건마다 개별
// "삭제 취소"가 있고 "리뷰 삭제"는 리뷰 전체에 한 번만 적용됩니다.
function ReviewReportsDialog({ adminUserId, reviewId, onClose, onReviewDeleted, onReportCountChange }) {
  const [status, setStatus] = useState('loading') // loading | ready | error
  const [reports, setReports] = useState([])
  // 신고를 전부 취소하면 reports가 빈 배열이 되는데, 그래도 "어떤 리뷰였는지"는 계속 보여줘야 하므로
  // 첫 로딩 시점의 영화/리뷰 정보를 reports와 별도로 따로 기억해둡니다(2026-11-20 실측 - reports[0]에만
  // 의존했더니 마지막 신고를 취소한 순간 다이얼로그가 빈 껍데기로 보였습니다).
  const [reviewInfo, setReviewInfo] = useState(null)
  const [error, setError] = useState('')
  const [actingId, setActingId] = useState(null) // null | 'delete' | 특정 신고 id
  const [actionError, setActionError] = useState('')
  const [reviewDeleted, setReviewDeleted] = useState(false)

  useEffect(() => {
    setStatus('loading')
    getReportsForReview(adminUserId, reviewId)
      .then((data) => {
        setReports(data)
        setReviewInfo(data[0] ?? null)
        setStatus('ready')
      })
      .catch((err) => {
        setError(err.message)
        setStatus('error')
      })
  }, [adminUserId, reviewId])

  const handleCancelOne = (reportId) => {
    setActingId(reportId)
    setActionError('')
    cancelReviewDeletion(adminUserId, reportId)
      .then(() => {
        const next = reports.filter((report) => report.id !== reportId)
        setReports(next)
        onReportCountChange?.(reviewId, next.length)
      })
      .catch((err) => setActionError(err.message))
      .finally(() => setActingId(null))
  }

  const handleDeleteReview = () => {
    if (!window.confirm('이 리뷰를 삭제할까요? 삭제하면 되돌릴 수 없습니다.')) return
    setActingId('delete')
    setActionError('')
    deleteReviewAsAdmin(reviewId, adminUserId)
      .then(() => {
        setReviewDeleted(true)
        onReviewDeleted?.(reviewId)
      })
      .catch((err) => setActionError(err.message))
      .finally(() => setActingId(null))
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
      <div className="relative w-full max-w-lg rounded-xl bg-slate-900 p-6">
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className="absolute right-4 top-4 flex h-7 w-7 items-center justify-center rounded-full border border-slate-700 text-gray-400 hover:border-rose-500 hover:bg-rose-500 hover:text-white"
        >
          ✕
        </button>
        <h2 className="text-sm font-semibold text-white">신고 내역</h2>

        {status === 'loading' && <p className="mt-4 text-sm text-gray-400">불러오는 중...</p>}
        {status === 'error' && <p className="mt-4 text-sm text-rose-400">⚠ {error}</p>}

        {status === 'ready' && reviewInfo && (
          <div className="mt-4 space-y-3 text-sm">
            <div>
              <p className="text-xs text-gray-500">영화</p>
              <p className="font-semibold text-gray-100">{reviewInfo.movieTitle}</p>
            </div>

            <div className="rounded-lg bg-slate-800 p-3">
              <div className="flex items-center justify-between text-xs text-gray-500">
                <span>신고된 리뷰 (작성자: {reviewInfo.reviewAuthorNickname})</span>
                <span className="text-amber-400">
                  {'★'.repeat(reviewInfo.reviewScore)}
                  <span className="text-gray-600">{'★'.repeat(5 - reviewInfo.reviewScore)}</span>
                </span>
              </div>
              <p className="mt-1.5 whitespace-pre-wrap text-gray-200">{reviewInfo.reviewContent}</p>
              {/* 사진이 저장된 리뷰일 때만 보여주고, 없으면 이 영역 자체를 렌더링하지 않습니다. */}
              {reviewInfo.reviewPhotoUrl && (
                <img
                  src={`${BASE_URL}${reviewInfo.reviewPhotoUrl}`}
                  alt="신고된 리뷰 첨부 사진"
                  className="mt-2 max-h-64 max-w-full rounded-lg bg-black object-contain"
                />
              )}
            </div>

            {reports.length > 0 ? (
              <div className="divide-y divide-slate-800 overflow-hidden rounded-lg border border-slate-800">
                {reports.map((report) => (
                  <div key={report.id} className="flex items-center justify-between gap-3 p-3">
                    <div>
                      <p className="font-semibold text-rose-400">{report.reasonLabel}</p>
                      <p className="text-xs text-gray-500">
                        {report.reporterNickname} · {formatDateTime(report.reportedAt)}
                      </p>
                    </div>
                    {/* 리뷰 자체가 삭제되면 이 신고 기록도 DB에서 같이 사라지므로(ON DELETE CASCADE),
                        삭제 후에는 더 이상 취소할 대상이 없어 버튼을 더 보여주지 않습니다. */}
                    {!reviewDeleted && (
                      <button
                        type="button"
                        disabled={actingId !== null}
                        onClick={() => handleCancelOne(report.id)}
                        className="shrink-0 rounded-lg border border-slate-700 px-3 py-1.5 text-xs font-semibold text-gray-300 hover:bg-slate-800 disabled:opacity-50"
                      >
                        {actingId === report.id ? '처리 중...' : '신고 취소'}
                      </button>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              !reviewDeleted && (
                <p className="rounded-lg bg-emerald-500/10 p-3 text-sm font-semibold text-emerald-400">
                  남은 신고가 없습니다 - 전부 취소 처리됐습니다.
                </p>
              )
            )}

            {reviewDeleted && (
              <p className="rounded-lg bg-emerald-500/10 p-3 text-sm font-semibold text-emerald-400">
                리뷰를 삭제했습니다.
              </p>
            )}
            {actionError && <p className="text-sm text-rose-400">⚠ {actionError}</p>}
          </div>
        )}

        {status === 'ready' && reviewInfo && !reviewDeleted && reports.length > 0 && (
          <button
            type="button"
            disabled={actingId !== null}
            onClick={handleDeleteReview}
            className="mt-4 w-full rounded-lg bg-rose-600 py-2 text-sm font-semibold text-white hover:bg-rose-500 disabled:opacity-50"
          >
            {actingId === 'delete' ? '삭제 중...' : '리뷰 삭제'}
          </button>
        )}
      </div>
    </div>
  )
}

export default ReviewReportsDialog
