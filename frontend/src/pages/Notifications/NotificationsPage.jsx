import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import {
  getNotifications,
  getUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
} from '../../api/notificationApi'
import { getReviewReportDetail } from '../../api/reviewApi'
import NotificationItem from '../../components/layout/NotificationItem'
import ReviewReportDetailDialog from '../../components/common/ReviewReportDetailDialog'
import Pagination from '../../components/common/Pagination'

// 알림 벨 드롭다운의 "전체보기" - 최근 것만 미리보기로 보여주던 드롭다운과 달리, 지난 알림을
// 포함한 전체 내역을 한 화면에서 페이지를 넘겨가며 볼 수 있습니다.
const ITEMS_PER_PAGE = 15

function NotificationsPage() {
  const { user } = useAuth()
  const [notifications, setNotifications] = useState([])
  const [status, setStatus] = useState('loading') // loading | ready | error
  const [unreadCount, setUnreadCount] = useState(0)
  const [reportDialog, setReportDialog] = useState(null)
  const [searchParams, setSearchParams] = useSearchParams()
  const page = (() => {
    const fromUrl = Number.parseInt(searchParams.get('page'), 10)
    return Number.isInteger(fromUrl) && fromUrl > 0 ? fromUrl : 1
  })()

  const handlePageChange = (value) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      if (value > 1) {
        next.set('page', String(value))
      } else {
        next.delete('page')
      }
      return next
    })
  }

  useEffect(() => {
    let cancelled = false
    setStatus('loading')
    getNotifications(user.id)
      .then((result) => {
        if (cancelled) return
        setNotifications(result)
        setStatus('ready')
      })
      .catch(() => {
        if (cancelled) return
        setStatus('error')
      })
    return () => {
      cancelled = true
    }
  }, [user.id])

  const refreshUnreadCount = () => {
    getUnreadCount(user.id)
      .then((dto) => setUnreadCount(dto.count))
      .catch(() => {})
  }

  useEffect(() => {
    refreshUnreadCount()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user.id])

  const handleMarkRead = (notification) => {
    if (notification.read) return
    markNotificationRead(user.id, notification.id)
      .then(() => {
        setNotifications((prev) => prev.map((n) => (n.id === notification.id ? { ...n, read: true } : n)))
        refreshUnreadCount()
      })
      .catch(() => {})
  }

  const handleMarkAllRead = () => {
    markAllNotificationsRead(user.id)
      .then(() => {
        setNotifications((prev) => prev.map((n) => ({ ...n, read: true })))
        setUnreadCount(0)
      })
      .catch(() => {})
  }

  // 리뷰 신고 알림의 "보러가기" - NotificationBell과 동일하게, 신고 상세를 다이얼로그로 보여줍니다.
  const handleViewReviewReport = (notification) => {
    handleMarkRead(notification)
    setReportDialog({ loading: true, error: '', detail: null })
    getReviewReportDetail(user.id, notification.reviewReportId)
      .then((detail) => setReportDialog({ loading: false, error: '', detail }))
      .catch((err) => setReportDialog({ loading: false, error: err.message, detail: null }))
  }

  const pageItems = notifications.slice((page - 1) * ITEMS_PER_PAGE, page * ITEMS_PER_PAGE)

  return (
    <div className="mx-auto max-w-2xl px-6 py-6">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-bold text-gray-100">
          알림
          {status === 'ready' && <span className="text-gray-400"> {notifications.length.toLocaleString()}건</span>}
        </h1>
        {unreadCount > 0 && (
          <button type="button" onClick={handleMarkAllRead} className="text-sm text-indigo-400 hover:underline">
            모두 읽음
          </button>
        )}
      </div>

      {status === 'loading' && <p className="mt-4 text-gray-400">불러오는 중...</p>}
      {status === 'error' && <p className="mt-4 text-red-400">알림을 불러오지 못했습니다.</p>}
      {status === 'ready' && notifications.length === 0 && (
        <p className="mt-4 text-gray-400">알림이 없습니다.</p>
      )}

      {status === 'ready' && notifications.length > 0 && (
        <>
          <div className="mt-4 overflow-hidden rounded-lg border border-white/10 bg-slate-900">
            {pageItems.map((notification) => (
              <NotificationItem
                key={notification.id}
                notification={notification}
                isAdmin={user.isAdmin}
                onMarkRead={handleMarkRead}
                onViewReviewReport={handleViewReviewReport}
              />
            ))}
          </div>
          <Pagination
            page={page}
            totalPages={Math.ceil(notifications.length / ITEMS_PER_PAGE)}
            onChange={handlePageChange}
          />
        </>
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

export default NotificationsPage
