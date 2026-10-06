import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'

function RequireAuth({ children, adminOnly = false }) {
  const { user } = useAuth()
  const location = useLocation()

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  if (adminOnly && !user.isAdmin) {
    return <Navigate to="/" replace />
  }

  // 관리자 계정은 일반 사용자 화면(마이페이지/예매/AI 추천/영화 목록 등)에 접근하지 못하고, 항상
  // 관리자 화면으로 보냅니다(로그인 직후 리다이렉트와 같은 목적지 - LoginPage.jsx의 goAfterLogin 참고).
  if (!adminOnly && user.isAdmin) {
    return <Navigate to="/admin/reports" replace />
  }

  return children
}

export default RequireAuth
