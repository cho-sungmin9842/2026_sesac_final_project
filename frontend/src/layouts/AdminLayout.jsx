import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import AdminSidebar from '../components/layout/AdminSidebar'
import ThemeToggle from '../components/common/ThemeToggle'
import NotificationBell from '../components/layout/NotificationBell'
import useMediaQuery from '../hooks/useMediaQuery'

// 사이드바가 데스크톱에선 항상 고정폭(w-60)으로 보이고, 모바일에선 기본적으로 숨겨진 오버레이라
// 열고 닫을 햄버거 버튼이 필요합니다 - 그 상태를 여기서 관리해 AdminSidebar에 내려줍니다.
function AdminLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  // 모바일/데스크톱 헤더를 CSS(hidden/md:flex)로만 나누면 둘 다 항상 마운트돼 NotificationBell의
  // 폴링이 두 배로 돕니다 - 실제 화면 폭을 보고 한쪽만 마운트합니다.
  const isDesktop = useMediaQuery('(min-width: 768px)')

  return (
    <div className="flex min-h-svh bg-slate-950 text-gray-100">
      <AdminSidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <div className="flex min-w-0 flex-1 flex-col">
        {!isDesktop && (
          <header className="flex items-center gap-3 border-b border-white/5 bg-slate-950 px-4 py-3">
            <button
              type="button"
              onClick={() => setSidebarOpen(true)}
              className="text-xl text-gray-300 hover:text-white"
              aria-label="메뉴 열기"
            >
              ☰
            </button>
            <span className="text-sm font-semibold text-white">SESAC MOVIE Admin</span>
            <div className="ml-auto flex items-center gap-3">
              <NotificationBell />
              <ThemeToggle />
            </div>
          </header>
        )}
        {isDesktop && (
          <header className="flex items-center justify-end gap-3 border-b border-white/5 bg-slate-950 px-6 py-3">
            <NotificationBell />
          </header>
        )}
        <main className="min-w-0 flex-1">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

export default AdminLayout
