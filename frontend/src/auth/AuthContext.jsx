import { createContext, useContext, useEffect, useState } from 'react'

// 백엔드 인증 서버가 아직 없어서, 로그인 상태를 로컬(브라우저)에만 저장하는 데모용 인증입니다.
// 이메일이 "admin"으로 시작하면 관리자 계정으로 취급합니다(목업 1번 화면 설명과 동일한 규칙).
const AuthContext = createContext(null)
const STORAGE_KEY = 'moviepick_auth_user'

function buildUser(nickname, email) {
  return {
    email,
    nickname: nickname || email.split('@')[0],
    isAdmin: email.trim().toLowerCase().startsWith('admin'),
  }
}

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

  const login = (email) => {
    const nextUser = buildUser(null, email)
    setUser(nextUser)
    return nextUser
  }

  const signup = (nickname, email) => {
    const nextUser = buildUser(nickname, email)
    setUser(nextUser)
    return nextUser
  }

  const logout = () => setUser(null)

  return <AuthContext.Provider value={{ user, login, signup, logout }}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth는 AuthProvider 안에서만 사용할 수 있습니다.')
  }
  return context
}
