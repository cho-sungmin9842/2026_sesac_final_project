import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'

const ADMIN_LINKS = [
  { to: '/admin', label: '대시보드', icon: '📊' },
  { to: '/admin/reports', label: '리뷰 신고 관리', icon: '🚩' },
  { to: '/admin/members', label: '회원 관리', icon: '👤' },
  { to: '/admin/tmdb-sync', label: 'TMDB 동기화', icon: '🎞️' },
  { to: '/admin/screenings', label: '예매/상영관 (확장)', icon: '🎟️' },
]

function AdminSidebar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <aside className="flex w-60 shrink-0 flex-col border-r border-white/5 bg-slate-950 px-4 py-6">
      <div className="mb-8 flex items-center gap-2 px-2 text-lg font-bold text-white">
        <span>🎬</span> MOVIEPICK
        <span className="text-xs font-normal text-gray-500">Admin</span>
      </div>

      <nav className="flex-1 space-y-1">
        {ADMIN_LINKS.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            end={link.to === '/admin'}
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
        <p className="mb-2 truncate">{user?.email}</p>
        <button type="button" onClick={handleLogout} className="text-xs hover:text-white">
          로그아웃
        </button>
      </div>
    </aside>
  )
}

export default AdminSidebar
