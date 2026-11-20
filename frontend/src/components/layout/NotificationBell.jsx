import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import {
  getNotifications,
  getUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
} from '../../api/notificationApi'
import { getReviewReportDetail } from '../../api/reviewApi'
import ReviewReportDetailDialog from '../common/ReviewReportDetailDialog'
import NotificationItem from './NotificationItem'

const POLL_INTERVAL_MS = 20000
// 드롭다운에는 최근 것만 미리보기로 보여주고, 전체 내역은 "전체보기"로 /notifications에서 봅니다.
const PREVIEW_COUNT = 8

// 예매 완료 시 본인/관리자에게 쌓이는 알림을 보여주는 종 아이콘. 안 읽은 개수를 주기적으로 폴링하고,
// 클릭하면 최근 알림 미리보기를 펼쳐서 보여줍니다(클릭 시 해당 알림을 읽음 처리).
function NotificationBell() {
  const { user } = useAuth()
  const [open, setOpen] = useState(false)
  const [unreadCount, setUnreadCount] = useState(0)
  const [notifications, setNotifications] = useState([])
  const containerRef = useRef(null)
  // 리뷰 신고 알림의 "보러가기" 다이얼로그 상태 - null이면 닫힌 상태입니다.
  const [reportDialog, setReportDialog] = useState(null)

  const refreshUnreadCount = () => {
    getUnreadCount(user.id)
      .then((dto) => setUnreadCount(dto.count))
      .catch(() => {})
  }

  useEffect(() => {
    refreshUnreadCount()
    const interval = setInterval(refreshUnreadCount, POLL_INTERVAL_MS)
    return () => clearInterval(interval)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user.id])

  useEffect(() => {
    function handleClickOutside(event) {
      if (containerRef.current && !containerRef.current.contains(event.target)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const toggleOpen = () => {
    const next = !open
    setOpen(next)
    if (next) {
      getNotifications(user.id)
        .then(setNotifications)
        .catch(() => {})
    }
  }

  const handleNotificationClick = (notification) => {
    if (notification.read) return
    markNotificationRead(user.id, notification.id)
      .then(() => {
        setNotifications((prev) => prev.map((n) => (n.id === notification.id ? { ...n, read: true } : n)))
        refreshUnreadCount()
      })
      .catch(() => {})
  }

  const handleItemMarkRead = (notification) => {
    setOpen(false)
    handleNotificationClick(notification)
  }

  // 리뷰 신고 알림의 "보러가기" - 회원 관리 화면으로 이동하는 대신, 신고된 리뷰 내용/작성자와
  // 신고 사유/신고자를 바로 다이얼로그로 보여줍니다.
  const handleViewReviewReport = (notification) => {
    setOpen(false)
    handleNotificationClick(notification)
    setReportDialog({ loading: true, error: '', detail: null })
    getReviewReportDetail(user.id, notification.reviewReportId)
      .then((detail) => setReportDialog({ loading: false, error: '', detail }))
      .catch((err) => setReportDialog({ loading: false, error: err.message, detail: null }))
  }

  const handleMarkAllRead = () => {
    markAllNotificationsRead(user.id)
      .then(() => {
        setNotifications((prev) => prev.map((n) => ({ ...n, read: true })))
        setUnreadCount(0)
      })
      .catch(() => {})
  }

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        aria-label="알림"
        onClick={toggleOpen}
        className="relative text-gray-300 hover:text-white"
      >
        🔔
        {unreadCount > 0 && (
          <span className="absolute -right-1.5 -top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-bold text-white">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-full z-30 mt-2 w-80 rounded-lg border border-white/10 bg-slate-900 shadow-xl">
          <div className="flex items-center justify-between border-b border-white/5 px-4 py-3">
            <span className="text-sm font-semibold text-white">알림</span>
            {unreadCount > 0 && (
              <button type="button" onClick={handleMarkAllRead} className="text-xs text-indigo-400 hover:underline">
                모두 읽음
              </button>
            )}
          </div>
          <div className="max-h-96 overflow-y-auto">
            {notifications.length === 0 && (
              <p className="px-4 py-6 text-center text-sm text-gray-500">알림이 없습니다.</p>
            )}
            {notifications.slice(0, PREVIEW_COUNT).map((notification) => (
              <NotificationItem
                key={notification.id}
                notification={notification}
                isAdmin={user.isAdmin}
                onMarkRead={handleItemMarkRead}
                onViewReviewReport={handleViewReviewReport}
              />
            ))}
          </div>
          <Link
            to={user.isAdmin ? '/admin/notifications' : '/notifications'}
            onClick={() => setOpen(false)}
            className="block border-t border-white/5 px-4 py-2.5 text-center text-xs font-semibold text-indigo-400 hover:bg-slate-800 hover:text-indigo-300"
          >
            전체보기
          </Link>
        </div>
      )}

      {reportDialog && (
        <ReviewReportDetailDialog
          loading={reportDialog.loading}
          error={reportDialog.error}
          detail={reportDialog.detail}
          adminUserId={user.isAdmin ? user.id : null}
          onClose={() => setReportDialog(null)}
        />
      )}
    </div>
  )
}

export default NotificationBell
