import { createContext, useContext, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import * as authApi from '../api/authApi'
import { checkSession } from '../api/userApi'

// 로그인 상태 자체는 여전히 브라우저(localStorage/sessionStorage)에만 남겨두는 데모 수준 세션입니다.
// 다만 회원가입/로그인은 이제 MySQL에 저장된 실제 계정으로 검증되고, 응답으로 받은 user.id가
// 리뷰/예매/다운로드 API 호출 시 "누가 요청했는지"를 알려주는 X-User-Id 헤더로 쓰입니다.
const AuthContext = createContext(null)
const STORAGE_KEY = 'moviepick_auth_user'

// "로그인 상태 유지"를 체크하면 localStorage(브라우저를 껐다 켜도 유지)에, 체크 안 하면
// sessionStorage(이 탭을 닫으면 사라짐)에 저장합니다. 새로고침 시 둘 중 값이 있는 쪽을 그대로 씁니다.
function readStoredUser() {
  const fromLocal = localStorage.getItem(STORAGE_KEY)
  if (fromLocal) return JSON.parse(fromLocal)
  const fromSession = sessionStorage.getItem(STORAGE_KEY)
  return fromSession ? JSON.parse(fromSession) : null
}

// 로그인돼 있는 동안 이 주기로 세션이 여전히 유효한지(=정지/탈퇴되지 않았는지) 확인합니다
// (NotificationBell의 알림 폴링과 같은 주기).
const SESSION_CHECK_INTERVAL_MS = 20000

export function AuthProvider({ children }) {
  const [user, setUser] = useState(readStoredUser)
  // 새로고침 직후에는 어느 storage에서 복원됐는지로 초기값을 맞춥니다 - localStorage에 있었다면
  // "유지" 상태였던 것이고, sessionStorage에만 있었다면 "유지 안 함" 상태였던 것입니다.
  const [keepLoggedIn, setKeepLoggedIn] = useState(() => localStorage.getItem(STORAGE_KEY) != null)
  const navigate = useNavigate()

  useEffect(() => {
    if (!user) {
      localStorage.removeItem(STORAGE_KEY)
      sessionStorage.removeItem(STORAGE_KEY)
      return
    }
    const target = keepLoggedIn ? localStorage : sessionStorage
    const other = keepLoggedIn ? sessionStorage : localStorage
    other.removeItem(STORAGE_KEY)
    target.setItem(STORAGE_KEY, JSON.stringify(user))
  }, [user, keepLoggedIn])

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

  const login = async (username, password, keep = true) => {
    const nextUser = await authApi.login({ username, password })
    setKeepLoggedIn(keep)
    setUser(nextUser)
    return nextUser
  }

  const signup = async (nickname, username, password) => {
    const nextUser = await authApi.signup({ nickname, username, password })
    setKeepLoggedIn(true)
    setUser(nextUser)
    return nextUser
  }

  const logout = () => setUser(null)

  // 비밀번호 찾기로 임시 비밀번호를 발급받은 직후나 소셜 로그인 콜백처럼, 이미 서버가 인증을 마치고
  // 돌려준 사용자 정보를 다시 로그인 API 호출 없이 그대로 로그인 상태로 반영합니다.
  const setSession = (nextUser, keep = true) => {
    setKeepLoggedIn(keep)
    setUser(nextUser)
  }

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
