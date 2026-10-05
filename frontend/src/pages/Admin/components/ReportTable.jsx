function formatDateTime(dateTimeStr) {
  const date = new Date(dateTimeStr)
  return date.toLocaleString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

// movieId/userId별 필터링 없이, DB에 저장된 모든 사용자의 리뷰를 그대로 보여줍니다.
function ReportTable({ reviews, onDelete }) {
  return (
    <table className="w-full text-left text-sm">
      <thead>
        <tr className="border-b border-white/5 text-gray-400">
          <th className="py-3 font-medium">영화</th>
          <th className="py-3 font-medium">작성자</th>
          <th className="py-3 font-medium">평점</th>
          <th className="py-3 font-medium">리뷰 내용</th>
          <th className="py-3 font-medium">작성일</th>
          <th className="py-3 font-medium">처리</th>
        </tr>
      </thead>
      <tbody>
        {reviews.map((review) => (
          <tr key={review.id} className="border-b border-white/5 text-gray-200">
            <td className="py-4 font-semibold">{review.movieTitle}</td>
            <td className="py-4 text-gray-300">{review.authorNickname}</td>
            <td className="py-4 text-gray-300">★ {review.score}</td>
            <td className="py-4 max-w-xs truncate text-gray-400" title={review.content}>
              {review.content}
            </td>
            <td className="py-4 whitespace-nowrap text-gray-400">{formatDateTime(review.createdAt)}</td>
            <td className="py-4">
              <button
                type="button"
                onClick={() => onDelete(review.id)}
                className="rounded-md bg-slate-800 px-3 py-1.5 text-xs font-semibold text-gray-200 hover:bg-rose-600"
              >
                삭제
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

export default ReportTable
