import { Link } from 'react-router-dom'
import { formatRelative } from '../../utils/formatRelative'

// 알림 1건 - NotificationBell(드롭다운)과 NotificationsPage(전체보기)가 공용으로 씁니다.
// 리뷰 신고 알림인지는 reviewReportId 유무로 구분합니다(NotificationService.notifyReviewReported 참고).
// 예매 관련(bookingId) 알림과 달리, 신고 알림은 마이페이지로 보내는 대신 신고 내용을 다이얼로그로 보여줍니다.
// 관리자는 마이페이지 접근 권한이 없어(RequireAuth) 예매 쪽 "보러가기"는 관리자가 아닐 때만 보여줍니다.
// 리뷰 "도움이 됐어요" 알림(reviewId/movieId)은 그 리뷰가 있는 영화 상세로 보냅니다(MovieDetailPage가
// ?reviewId= 쿼리로 해당 리뷰까지 스크롤/하이라이트합니다) - 본인 리뷰라 관리자 구분 없이 누구나 이동합니다.
function NotificationItem({ notification, isAdmin, onMarkRead, onViewReviewReport }) {
  const isReviewReport = isAdmin && notification.reviewReportId != null
  const isReviewHelpful = notification.reviewId != null && notification.movieId != null
  const isBookingLinked = notification.bookingId && !isAdmin
  const isLinked = isReviewReport || isReviewHelpful || isBookingLinked

  const itemClassName = `block w-full border-b border-white/5 px-4 py-3 text-left text-sm last:border-0 hover:bg-slate-800 ${
    notification.read ? 'text-gray-400' : 'text-gray-100'
  }`
  const itemContent = (
    <>
      <p className="leading-relaxed">{notification.message}</p>
      <p className="mt-1 flex items-center gap-1.5 text-xs text-gray-500">
        {!notification.read && <span className="h-1.5 w-1.5 rounded-full bg-indigo-500" />}
        {formatRelative(notification.createdAt)}
        {isLinked && <span className="ml-auto font-semibold text-indigo-400">보러가기 →</span>}
      </p>
    </>
  )

  if (isReviewReport) {
    return (
      <button type="button" onClick={() => onViewReviewReport(notification)} className={itemClassName}>
        {itemContent}
      </button>
    )
  }

  if (isReviewHelpful) {
    return (
      <Link
        to={`/movies/${notification.movieId}?reviewId=${notification.reviewId}`}
        onClick={() => onMarkRead(notification)}
        className={itemClassName}
      >
        {itemContent}
      </Link>
    )
  }

  if (isBookingLinked) {
    return (
      <Link to="/mypage" onClick={() => onMarkRead(notification)} className={itemClassName}>
        {itemContent}
      </Link>
    )
  }

  return (
    <button type="button" onClick={() => onMarkRead(notification)} className={itemClassName}>
      {itemContent}
    </button>
  )
}

export default NotificationItem
