import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'

const ADMIN_LINKS = [
  { to: '/admin', label: '대시보드', icon: '📊' },
  { to: '/admin/reports', label: '리뷰 관리', icon: '🚩' },
  { to: '/admin/members', label: '회원 관리', icon: '👤' },
  { to: '/admin/screenings', label: '예매/상영관 현황', icon: '🎟️' },
]

// 모바일(md 미만)에서는 기본적으로 화면 밖에 숨겨뒀다가(open=false) AdminLayout의 햄버거 버튼으로
// 열고 닫는 오버레이로 동작합니다. md 이상(데스크톱)에서는 항상 보이는 고정 사이드바입니다.
function AdminSidebar({ open, onClose }) {
  const { logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <>
      {open && (
        <div className="fixed inset-0 z-40 bg-black/50 md:hidden" onClick={onClose} aria-hidden="true" />
      )}
      <aside
        className={`fixed inset-y-0 left-0 z-50 flex w-60 shrink-0 flex-col border-r border-white/5 bg-slate-950 px-4 py-6 transition-transform duration-200 md:static md:translate-x-0 ${
          open ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="mb-8 flex items-center justify-between gap-2 px-2">
          <div className="flex items-center gap-2 text-lg font-bold text-white">
            <span>🎬</span> SESAC MOVIE
            <span className="text-xs font-normal text-gray-500">Admin</span>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-gray-400 hover:text-white md:hidden"
            aria-label="메뉴 닫기"
          >
            ✕
          </button>
        </div>

        <nav className="flex-1 space-y-1">
          {ADMIN_LINKS.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={link.to === '/admin'}
              onClick={onClose}
              className={({ isActive }) =>
                `flex items-center gap-2 rounded-lg px-3 py-2 text-sm ${
                  isActive
                    ? 'bg-indigo-600 text-white'
                    : 'text-gray-400 hover:bg-slate-900 hover:text-gray-200'
                }`
              }
            >
              <span>{link.icon}</span>
              {link.label}
            </NavLink>
          ))}
        </nav>

        <div className="border-t border-white/5 px-2 pt-4 text-sm text-gray-400">
          <p className="mb-2 truncate">관리자</p>
          <button type="button" onClick={handleLogout} className="text-xs hover:text-white">
            로그아웃
          </button>
        </div>
      </aside>
    </>
  )
}

export default AdminSidebar
