import HorizontalScroll from '../../../components/common/HorizontalScroll'

function formatDateTime(dateTimeStr) {
  const date = new Date(dateTimeStr)
  return date.toLocaleString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

// "리뷰 관리" 화면의 "삭제 이력" 탭 - 리뷰/신고는 삭제되면 영영 사라지지만, 이 표는 삭제 당시 값의
// 스냅샷(review_deletion_logs)이라 그 뒤로도 계속 남아있습니다. 리뷰 내용이 길면 표에서는
// 말줄임표로 잘려 보이므로, 누르면 DeletionLogDetailDialog로 전체 내용을 볼 수 있습니다
// (2026-11-21 요청 - ReportTable의 "리뷰 내용 클릭 → 상세 다이얼로그"와 같은 패턴).
function DeletionLogTable({ logs, onViewDetail }) {
  return (
    <HorizontalScroll>
      <table className="w-full min-w-[760px] text-left text-sm">
        <thead>
          <tr className="border-b border-white/5 text-gray-400">
            <th className="py-3 font-medium">영화</th>
            <th className="py-3 font-medium">작성자</th>
            <th className="py-3 font-medium">평점</th>
            <th className="py-3 font-medium">리뷰 내용</th>
            <th className="py-3 font-medium">신고 사유</th>
            <th className="py-3 font-medium">삭제한 관리자</th>
            <th className="py-3 font-medium">삭제 일시</th>
          </tr>
        </thead>
        <tbody>
          {logs.map((log) => (
            <tr key={log.id} className="border-b border-white/5 text-gray-200">
              <td className="py-4 font-semibold">{log.movieTitle}</td>
              <td className="py-4 text-gray-300">{log.reviewAuthorNickname}</td>
              <td className="py-4 text-gray-300">★ {log.reviewScore}</td>
              <td className="py-4 max-w-xs">
                <button
                  type="button"
                  onClick={() => onViewDetail(log)}
                  className="max-w-xs truncate text-left text-gray-400 hover:text-gray-200 hover:underline"
                  title={log.reviewContent}
                >
                  {log.reviewContent}
                </button>
              </td>
              <td className="py-4 text-gray-400">{log.reportReasons ?? '-'}</td>
              <td className="py-4 text-gray-300">{log.deletedByAdminNickname}</td>
              <td className="py-4 whitespace-nowrap text-gray-400">{formatDateTime(log.deletedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </HorizontalScroll>
  )
}

export default DeletionLogTable
