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

// "리뷰 관리" 테이블에서 리뷰 내용을 누르면 뜨는 상세 다이얼로그 - 표에는 다 들어가지 않는 전체
// 내용·사진·신고 여부까지 한 번에 보여줍니다. 사진은 있을 때만 보여주고(review.photoUrl이 없으면
// 그 영역 자체를 렌더링하지 않음), 신고 N건 뱃지를 누르면 신고 사유/신고자를 보는 ReviewReportsDialog로
// 바로 이어갈 수 있습니다.
function ReviewDetailDialog({ review, onClose, onViewReports }) {
  const isReported = review.reportCount > 0

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
        <h2 className="text-sm font-semibold text-white">리뷰 상세</h2>

        <div className="mt-4 space-y-3 text-sm">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-xs text-gray-500">영화</p>
              <p className="font-semibold text-gray-100">{review.movieTitle}</p>
            </div>
            <span className="text-amber-400">
              {'★'.repeat(review.score)}
              <span className="text-gray-600">{'★'.repeat(5 - review.score)}</span>
            </span>
          </div>

          <div>
            <p className="text-xs text-gray-500">작성자</p>
            <p className="font-semibold text-gray-100">{review.authorNickname}</p>
          </div>

          <div className="rounded-lg bg-slate-800 p-3">
            <p className="whitespace-pre-wrap text-gray-200">{review.content}</p>
          </div>

          {review.photoUrl && (
            <img
              src={`${BASE_URL}${review.photoUrl}`}
              alt="리뷰 첨부 사진"
              className="max-h-80 max-w-full rounded-lg bg-black object-contain"
            />
          )}

          <div className="flex items-center justify-between text-xs text-gray-500">
            <span>작성일: {formatDateTime(review.createdAt)}</span>
            {isReported ? (
              <button
                type="button"
                onClick={() => onViewReports(review.id)}
                className="rounded-full bg-rose-500/15 px-2.5 py-1 font-semibold text-rose-400 hover:bg-rose-500/25"
              >
                신고 {review.reportCount}건 보기
              </button>
            ) : (
              <span>신고 없음</span>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

export default ReviewDetailDialog
