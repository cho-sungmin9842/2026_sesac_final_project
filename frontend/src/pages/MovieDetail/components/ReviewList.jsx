import { useState } from 'react'
import { BASE_URL } from '../../../api/httpClient'

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
// onToggleHelpful: "도움이 됐어요" 버튼 - 신고와 대칭되는 긍정적 상호작용이라, review.helpfulCount/
// helpfulByMe는 서버가 리뷰 목록 조회 시점에 이미 계산해서 내려줍니다(ReviewService.list 참고).
// highlightedReviewId: "도움이 됐어요" 알림을 눌러 들어왔을 때(?reviewId=) 어떤 리뷰로 스크롤해왔는지 -
// 그 리뷰만 잠깐 테두리로 강조해서 눈에 띄게 합니다(MovieDetailPage가 스크롤 자체는 이미 처리).
function ReviewList({ reviews, currentUser, onEdit, onDelete, onReport, reportedReviewIds, onToggleHelpful, highlightedReviewId }) {
  return (
    <div className="space-y-4">
      {reviews.map((review) => {
        const isOwner = currentUser != null && review.userId === currentUser
        const isReported = reportedReviewIds?.has(review.id)
        const date = formatDate(review.createdAt)
        const isHighlighted = highlightedReviewId != null && String(review.id) === String(highlightedReviewId)

        return (
          <div
            key={review.id}
            id={`review-${review.id}`}
            className={`rounded-lg bg-slate-900 p-4 ${
              isHighlighted ? 'ring-2 ring-indigo-500' : ''
            }`}
          >
            <div className="flex items-center justify-between">
              <p className="flex items-center gap-2 text-sm font-semibold text-gray-100">
                📄 {review.authorNickname}
              </p>
              <Stars score={review.score} />
            </div>
            {date && <p className="mt-1 text-right text-xs text-gray-500">작성일: {date}</p>}
            <ReviewContent content={review.content} />
            {review.photoUrl && (
              <img
                src={`${BASE_URL}${review.photoUrl}`}
                alt="리뷰 첨부 사진"
                className="mt-2 max-h-72 max-w-full rounded-lg object-contain"
              />
            )}
            {isOwner && (
              <div className="mt-2 flex items-center justify-between text-xs text-gray-400">
                {/* 본인 리뷰라 추천할 수 없으니(서버도 막음) 버튼이 아니라 숫자만 읽기 전용으로
                    보여줍니다 - 클릭해도 아무 핸들러가 없어 추천 수를 스스로 올리거나 내릴 수 없습니다. */}
                <span>👍 도움이 됐어요 {review.helpfulCount ?? 0}명</span>
                <div className="flex gap-2">
                  <button type="button" onClick={() => onEdit(review)} className="hover:text-white">
                    수정
                  </button>
                  <button type="button" onClick={() => onDelete(review)} className="hover:text-red-400">
                    삭제
                  </button>
                </div>
              </div>
            )}
            {!isOwner && currentUser != null && (
              <div className="mt-2 flex items-center justify-end gap-3 text-xs text-gray-400">
                <button
                  type="button"
                  onClick={() => onToggleHelpful(review)}
                  className={`flex items-center gap-1 font-semibold ${
                    review.helpfulByMe ? 'text-indigo-400' : 'hover:text-indigo-400'
                  }`}
                >
                  👍 도움이 됐어요{review.helpfulCount > 0 ? ` ${review.helpfulCount}` : ''}
                </button>
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
