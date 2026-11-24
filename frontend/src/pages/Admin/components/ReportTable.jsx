import { useEffect, useRef } from 'react'
import HorizontalScroll from '../../../components/common/HorizontalScroll'

function formatDateTime(dateTimeStr) {
  const date = new Date(dateTimeStr)
  return date.toLocaleString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

// movieId/userId별 필터링 없이, 걸러지고 정렬된 리뷰를 그대로 보여줍니다. reportCount > 0인 행은
// 신고된 리뷰라는 걸 한눈에 알 수 있도록 테두리를 강조하고, "신고 N건" 뱃지를 눌러서 그 자리에서 바로
// 신고 사유/신고자를 확인하고 처리(삭제/삭제 취소)할 수 있게 합니다. 체크박스로 여러 건을 골라
// 한 번에 삭제할 수 있고, 리뷰 내용을 누르면 전체 내용·사진·신고 여부까지 모아서 보여주는
// ReviewDetailDialog가 뜹니다(AdminReportsPage 참고).
// 컬럼이 많아 좁은 화면(모바일)에서는 표가 찌그러지므로, 가로 스크롤로 보게끔 감쌉니다.
// allSelected: 지금 페이지가 아니라 검색/필터에 맞는 전체 기준 - AdminReportsPage가 계산해서 넘겨줍니다
// (이 페이지만 보고 판단하면, 전체 선택 후 다른 페이지로 넘어갔을 때 헤더 체크박스가 틀리게 보입니다).
// someSelected: 전체는 아니지만 일부라도 선택됐는지 - 체크박스의 "일부 선택" 모양(indeterminate)은
// checked 속성처럼 JSX로 선언할 수 없고 DOM 엘리먼트에 직접 값을 넣어줘야만 해서 ref로 처리합니다
// (2026-11-24 요청 - 그 전에는 일부만 선택돼도 체크박스가 그냥 빈 채로 보여서 구분이 안 갔습니다).
function ReportTable({
  reviews,
  selectedIds,
  allSelected,
  someSelected,
  onToggleSelect,
  onToggleSelectAll,
  onDelete,
  onViewReports,
  onViewDetail,
}) {
  const selectAllRef = useRef(null)

  useEffect(() => {
    if (selectAllRef.current) {
      selectAllRef.current.indeterminate = someSelected && !allSelected
    }
  }, [someSelected, allSelected])

  return (
    <HorizontalScroll>
      <table className="w-full min-w-[760px] text-left text-sm">
        <thead>
          <tr className="border-b border-white/5 text-gray-400">
            <th className="w-8 py-3">
              <input
                ref={selectAllRef}
                type="checkbox"
                checked={allSelected}
                onChange={onToggleSelectAll}
                aria-label="전체 선택"
              />
            </th>
            <th className="py-3 font-medium">영화</th>
            <th className="py-3 font-medium">작성자</th>
            <th className="py-3 font-medium">평점</th>
            <th className="py-3 font-medium">리뷰 내용</th>
            <th className="py-3 font-medium">작성일</th>
            <th className="py-3 font-medium">신고</th>
            <th className="py-3 font-medium">처리</th>
          </tr>
        </thead>
        <tbody>
          {reviews.map((review) => {
            const isReported = review.reportCount > 0
            return (
              <tr key={review.id} className={`border-b border-white/5 text-gray-200 ${isReported ? 'bg-rose-500/5' : ''}`}>
                <td className="py-4">
                  <input
                    type="checkbox"
                    checked={selectedIds.has(review.id)}
                    onChange={() => onToggleSelect(review.id)}
                    aria-label={`${review.movieTitle} 리뷰 선택`}
                  />
                </td>
                <td className={`py-4 font-semibold ${isReported ? 'border-l-2 border-rose-500 pl-3' : ''}`}>
                  {review.movieTitle}
                </td>
                <td className="py-4 text-gray-300">{review.authorNickname}</td>
                <td className="py-4 text-gray-300">★ {review.score}</td>
                <td className="py-4 max-w-xs">
                  <button
                    type="button"
                    onClick={() => onViewDetail(review)}
                    className="max-w-xs truncate text-left text-gray-400 hover:text-gray-200 hover:underline"
                    title={review.content}
                  >
                    {review.content}
                  </button>
                </td>
                <td className="py-4 whitespace-nowrap text-gray-400">{formatDateTime(review.createdAt)}</td>
                <td className="py-4">
                  {isReported ? (
                    <button
                      type="button"
                      onClick={() => onViewReports(review.id)}
                      className="rounded-full bg-rose-500/15 px-2.5 py-1 text-xs font-semibold text-rose-400 hover:bg-rose-500/25"
                    >
                      신고 {review.reportCount}건
                    </button>
                  ) : (
                    <span className="text-xs text-gray-600">-</span>
                  )}
                </td>
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
            )
          })}
        </tbody>
      </table>
    </HorizontalScroll>
  )
}

export default ReportTable
