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

function ReviewList({ reviews, currentUser, onEdit, onDelete }) {
  return (
    <div className="space-y-4">
      {reviews.map((review) => {
        const isOwner = currentUser && review.author === currentUser
        const date = formatDate(review.createdAt)

        return (
          <div key={review.id ?? review.author} className="rounded-lg bg-slate-900 p-4">
            <div className="flex items-center justify-between">
              <p className="flex items-center gap-2 text-sm font-semibold text-gray-100">
                📄 {review.author}
              </p>
              <div className="flex items-center gap-3">
                <Stars score={review.score} />
                {isOwner && (
                  <div className="flex gap-2 text-xs text-gray-400">
                    <button type="button" onClick={() => onEdit(review)} className="hover:text-white">
                      수정
                    </button>
                    <button type="button" onClick={() => onDelete(review)} className="hover:text-red-400">
                      삭제
                    </button>
                  </div>
                )}
              </div>
            </div>
            {date && <p className="mt-1 text-right text-xs text-gray-500">작성일: {date}</p>}
            <p className="mt-2 text-sm text-gray-400">{review.content}</p>
          </div>
        )
      })}
    </div>
  )
}

export default ReviewList
