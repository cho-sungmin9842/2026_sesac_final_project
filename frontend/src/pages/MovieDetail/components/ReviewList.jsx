import { useState } from 'react'

// 이 길이를 넘는 리뷰는 잘라서 보여주고 "더보기"로 펼칠 수 있게 합니다 - 긴 리뷰 하나가 화면
// 전체를 차지해서 다른 리뷰를 한참 아래로 밀어내는 문제가 있었습니다.
const REVIEW_TRUNCATE_LENGTH = 300

function ReviewContent({ content }) {
  const [expanded, setExpanded] = useState(false)
  const isLong = content.length > REVIEW_TRUNCATE_LENGTH
  const displayedContent = expanded || !isLong ? content : `${content.slice(0, REVIEW_TRUNCATE_LENGTH)}...`

  return (
    <div className="mt-2 text-sm text-gray-400">
      <p>{displayedContent}</p>
      {isLong && (
        <button
          type="button"
          onClick={() => setExpanded((prev) => !prev)}
          className="mt-1 text-xs font-semibold text-indigo-400 hover:underline"
        >
          {expanded ? '간략히 보기' : '더보기'}
        </button>
      )}
    </div>
  )
}

function Stars({ score }) {
  return (
    <span className="text-amber-400">
      {'★'.repeat(score)}
      <span className="text-gray-600">{'★'.repeat(5 - score)}</span>
    </span>
  )
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

function formatDate(isoString) {
  if (!isoString) return null
  const date = new Date(isoString)
  const yyyy = date.getFullYear()
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  const weekday = WEEKDAYS[date.getDay()]
  return `${yyyy}-${mm}-${dd}(${weekday})`
}

// reportedReviewIds: 이번에 접속한 화면에서 이미 신고 처리된 리뷰 id 모음(새로고침하면 초기화됨) -
// 서버에 "내가 신고한 목록"을 따로 물어보지 않고, 신고 성공 직후에만 버튼을 "신고 완료"로 바꿔줍니다.
function ReviewList({ reviews, currentUser, onEdit, onDelete, onReport, reportedReviewIds }) {
  return (
    <div className="space-y-4">
      {reviews.map((review) => {
        const isOwner = currentUser != null && review.userId === currentUser
        const isReported = reportedReviewIds?.has(review.id)
        const date = formatDate(review.createdAt)

        return (
          <div key={review.id} className="rounded-lg bg-slate-900 p-4">
            <div className="flex items-center justify-between">
              <p className="flex items-center gap-2 text-sm font-semibold text-gray-100">
                📄 {review.authorNickname}
              </p>
              <Stars score={review.score} />
            </div>
            {date && <p className="mt-1 text-right text-xs text-gray-500">작성일: {date}</p>}
            <ReviewContent content={review.content} />
            {isOwner && (
              <div className="mt-2 flex justify-end gap-2 text-xs text-gray-400">
                <button type="button" onClick={() => onEdit(review)} className="hover:text-white">
                  수정
                </button>
                <button type="button" onClick={() => onDelete(review)} className="hover:text-red-400">
                  삭제
                </button>
              </div>
            )}
            {!isOwner && currentUser != null && (
              <div className="mt-2 flex justify-end text-xs text-gray-400">
                <button
                  type="button"
                  disabled={isReported}
                  onClick={() => onReport(review)}
                  className="disabled:cursor-not-allowed disabled:text-gray-600 hover:text-rose-400"
                >
                  {isReported ? '신고 완료' : '신고'}
                </button>
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

export default ReviewList
