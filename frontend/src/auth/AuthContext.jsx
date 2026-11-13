import { createContext, useContext, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import * as authApi from '../api/authApi'
import { checkSession } from '../api/userApi'

// 로그인 상태 자체는 여전히 브라우저(localStorage)에만 남겨두는 데모 수준 세션입니다.
// 다만 회원가입/로그인은 이제 MySQL에 저장된 실제 계정으로 검증되고, 응답으로 받은 user.id가
// 리뷰/예매/다운로드 API 호출 시 "누가 요청했는지"를 알려주는 X-User-Id 헤더로 쓰입니다.
const AuthContext = createContext(null)
const STORAGE_KEY = 'moviepick_auth_user'

// 로그인돼 있는 동안 이 주기로 세션이 여전히 유효한지(=정지/탈퇴되지 않았는지) 확인합니다
// (NotificationBell의 알림 폴링과 같은 주기).
const SESSION_CHECK_INTERVAL_MS = 20000

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  })
  const navigate = useNavigate()

  useEffect(() => {
    if (user) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(user))
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  }, [user])

  // 로그인돼 있는 동안, 그 사이 관리자가 이 계정을 정지시키지 않았는지 주기적으로 확인합니다.
  // 토큰/세션이 없는 데모 인증이라 서버가 먼저 끊을 방법이 없어, 폴링으로 감지해 강제 로그아웃시킵니다.
  // 401/403(세션 자체가 무효해짐)일 때만 로그아웃하고, 그 외(네트워크 오류 등 일시적인 문제)는
  // 다음 폴링에서 다시 시도하도록 그냥 넘어갑니다.
  useEffect(() => {
    if (!user) return undefined
    const interval = setInterval(() => {
      checkSession(user.id).catch((error) => {
        if (error.status === 401 || error.status === 403) {
          setUser(null)
          navigate('/login', { replace: true })
        }
      })
    }, SESSION_CHECK_INTERVAL_MS)
    return () => clearInterval(interval)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.id])

  const login = async (username, password) => {
    const nextUser = await authApi.login({ username, password })
    setUser(nextUser)
    return nextUser
  }

  const signup = async (nickname, username, password) => {
    const nextUser = await authApi.signup({ nickname, username, password })
    setUser(nextUser)
    return nextUser
  }

  const logout = () => setUser(null)

  // 비밀번호 찾기로 임시 비밀번호를 발급받은 직후처럼, 이미 서버가 인증을 마치고 돌려준 사용자 정보를
  // 다시 로그인 API 호출 없이 그대로 로그인 상태로 반영합니다.
  const setSession = (nextUser) => setUser(nextUser)

  return (
    <AuthContext.Provider value={{ user, login, signup, logout, setSession }}>{children}</AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth는 AuthProvider 안에서만 사용할 수 있습니다.')
  }
  return context
}
