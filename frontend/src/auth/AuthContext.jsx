import { createContext, useContext, useEffect, useState } from 'react'
import * as authApi from '../api/authApi'

// 로그인 상태 자체는 여전히 브라우저(localStorage)에만 남겨두는 데모 수준 세션입니다.
// 다만 회원가입/로그인은 이제 MySQL에 저장된 실제 계정으로 검증되고, 응답으로 받은 user.id가
// 리뷰/예매/다운로드 API 호출 시 "누가 요청했는지"를 알려주는 X-User-Id 헤더로 쓰입니다.
const AuthContext = createContext(null)
const STORAGE_KEY = 'moviepick_auth_user'

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  })

  useEffect(() => {
    if (user) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(user))
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  }, [user])

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

  const loginSocial = async (provider) => {
    const nextUser = await authApi.socialLogin(provider)
    setUser(nextUser)
    return nextUser
  }

  const logout = () => setUser(null)

  return (
    <AuthContext.Provider value={{ user, login, signup, loginSocial, logout }}>{children}</AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth는 AuthProvider 안에서만 사용할 수 있습니다.')
  }
  return context
}
