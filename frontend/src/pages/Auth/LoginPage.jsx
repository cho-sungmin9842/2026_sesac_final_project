import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import AuthShell from './components/AuthShell'
import FindPasswordDialog from './components/FindPasswordDialog'

function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [showFindPassword, setShowFindPassword] = useState(false)

  const goAfterLogin = (user) => {
    if (user.isAdmin) {
      navigate('/admin/reports', { replace: true })
      return
    }
    navigate(location.state?.from?.pathname ?? '/', { replace: true })
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    if (isSubmitting) return

    // 브라우저 기본 검증 팝업(required) 대신, 다른 에러와 똑같은 스타일의 경고 박스로 어떤 항목을
    // 안 채웠는지 알려줍니다 - 하나라도 비어있으면 로그인을 시도하지 않고 여기서 멈춥니다.
    const missing = []
    if (!username.trim()) missing.push('아이디')
    if (!password) missing.push('비밀번호')
    if (missing.length > 0) {
      setError(`${missing.join(', ')}을(를) 입력해주세요.`)
      return
    }

    setError('')
    setIsSubmitting(true)
    try {
      goAfterLogin(await login(username, password))
    } catch (err) {
      setError(err.message)
    } finally {
      setIsSubmitting(false)
    }
  }

  const usernameHasError = Boolean(error) && (!username.trim() || error.includes('아이디'))
  const passwordHasError = Boolean(error) && (!password || error.includes('비밀번호'))

  const inputClassName = (hasError) =>
    `w-full rounded-lg border px-3 py-2 text-sm text-slate-900 focus:outline-none ${
      hasError
        ? 'border-red-400 focus:border-red-500'
        : 'border-slate-300 focus:border-indigo-500'
    }`

  return (
    <AuthShell mode="login" tagline="정보 탐색부터 예매까지, AI 하나로 끝내세요">
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <div>
          <label htmlFor="username" className="mb-1 block text-sm font-medium text-slate-700">
            아이디
          </label>
          <input
            id="username"
            type="text"
            required
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            placeholder="아이디를 입력하세요"
            className={inputClassName(usernameHasError)}
          />
        </div>

        <div>
          <label htmlFor="password" className="mb-1 block text-sm font-medium text-slate-700">
            비밀번호
          </label>
          <input
            id="password"
            type="password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="비밀번호를 입력하세요"
            className={inputClassName(passwordHasError)}
          />
        </div>

        {error && (
          <p className="rounded-lg border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-600">
            ⚠ {error}
          </p>
        )}

        <div className="flex items-center justify-between text-xs text-slate-500">
          <label className="flex items-center gap-1.5">
            <input type="checkbox" className="rounded border-slate-300" />
            로그인 상태 유지
          </label>
          <button type="button" onClick={() => setShowFindPassword(true)} className="text-indigo-600 hover:underline">
            비밀번호 찾기
          </button>
        </div>

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full rounded-lg bg-indigo-600 py-2.5 text-sm font-semibold text-white hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {isSubmitting ? '로그인 중...' : '로그인'}
        </button>

        <p className="pt-2 text-center text-xs text-slate-500">
          관리자 계정으로 로그인하면 자동으로 관리자 화면으로 이동합니다.
        </p>
      </form>

      {showFindPassword && (
        <FindPasswordDialog onClose={() => setShowFindPassword(false)} onAutoLogin={goAfterLogin} />
      )}
    </AuthShell>
  )
}

export default LoginPage
