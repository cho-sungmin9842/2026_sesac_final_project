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
function ReviewReportDetailDialog({ loading, error, detail, onClose }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
      <div className="w-full max-w-md rounded-xl bg-slate-900 p-6">
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
          </div>
        )}

        <button
          type="button"
          onClick={onClose}
          className="mt-5 w-full rounded-lg border border-slate-700 py-2 text-sm font-semibold text-gray-300 hover:bg-slate-800"
        >
          닫기
        </button>
      </div>
    </div>
  )
}

export default ReviewReportDetailDialog
