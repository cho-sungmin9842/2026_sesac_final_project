import { useEffect, useRef, useState } from 'react'
import { useAuth } from '../../auth/AuthContext'
import {
  getNotifications,
  getUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
} from '../../api/notificationApi'

const POLL_INTERVAL_MS = 20000

function formatRelative(iso) {
  const diffMs = Date.now() - new Date(iso).getTime()
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '방금 전'
  if (diffMin < 60) return `${diffMin}분 전`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour}시간 전`
  return `${Math.floor(diffHour / 24)}일 전`
}

// 예매 완료 시 본인/관리자에게 쌓이는 알림을 보여주는 종 아이콘. 안 읽은 개수를 주기적으로 폴링하고,
// 클릭하면 전체 목록을 펼쳐서 보여줍니다(클릭 시 해당 알림을 읽음 처리).
function NotificationBell() {
  const { user } = useAuth()
  const [open, setOpen] = useState(false)
  const [unreadCount, setUnreadCount] = useState(0)
  const [notifications, setNotifications] = useState([])
  const containerRef = useRef(null)

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
            {notifications.map((notification) => (
              <button
                key={notification.id}
                type="button"
                onClick={() => handleNotificationClick(notification)}
                className={`block w-full border-b border-white/5 px-4 py-3 text-left text-sm last:border-0 hover:bg-slate-800 ${
                  notification.read ? 'text-gray-400' : 'text-gray-100'
                }`}
              >
                <p className="leading-relaxed">{notification.message}</p>
                <p className="mt-1 flex items-center gap-1.5 text-xs text-gray-500">
                  {!notification.read && <span className="h-1.5 w-1.5 rounded-full bg-indigo-500" />}
                  {formatRelative(notification.createdAt)}
                </p>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

export default NotificationBell
