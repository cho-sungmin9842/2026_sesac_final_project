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

// "삭제 이력" 표에서 리뷰 내용을 누르면 뜨는 상세 다이얼로그 - 표에는 말줄임표로 잘려 보이는 전체
// 내용을 포함해, 이 삭제 이력에 남은 정보를 전부 보여줍니다(ReviewDetailDialog와 같은 패턴). 삭제된
// 리뷰의 스냅샷이라 사진이나 "신고 N건 보기" 같은 그 이후 조작은 없고 읽기 전용입니다.
function DeletionLogDetailDialog({ log, onClose }) {
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
        <h2 className="text-sm font-semibold text-white">삭제 이력 상세</h2>

        <div className="mt-4 space-y-3 text-sm">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-xs text-gray-500">영화</p>
              <p className="font-semibold text-gray-100">{log.movieTitle}</p>
            </div>
            <span className="text-amber-400">
              {'★'.repeat(log.reviewScore)}
              <span className="text-gray-600">{'★'.repeat(5 - log.reviewScore)}</span>
            </span>
          </div>

          <div>
            <p className="text-xs text-gray-500">작성자</p>
            <p className="font-semibold text-gray-100">{log.reviewAuthorNickname}</p>
          </div>

          <div className="rounded-lg bg-slate-800 p-3">
            <p className="whitespace-pre-wrap text-gray-200">{log.reviewContent}</p>
          </div>

          <div>
            <p className="text-xs text-gray-500">신고 사유</p>
            <p className="font-semibold text-gray-100">{log.reportReasons ?? '신고 없음(관리자가 직접 삭제)'}</p>
          </div>

          <div className="flex items-center justify-between text-xs text-gray-500">
            <span>삭제한 관리자: {log.deletedByAdminNickname}</span>
            <span>삭제 일시: {formatDateTime(log.deletedAt)}</span>
          </div>
        </div>
      </div>
    </div>
  )
}

export default DeletionLogDetailDialog
