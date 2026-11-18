import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import AuthShell from './components/AuthShell'
import FindPasswordDialog from './components/FindPasswordDialog'
import FindUsernameDialog from './components/FindUsernameDialog'
import InfoDialog from '../../components/common/InfoDialog'
import { josa } from '../../utils/korean'
import { GoogleIcon } from '../../components/icons/SocialIcons'
import { EyeIcon, EyeOffIcon } from '../../components/icons/EyeIcons'
import { BASE_URL } from '../../api/httpClient'

// UserService.SUSPENDED_MESSAGE_PREFIX와 반드시 같아야 합니다 - 이 문구가 포함돼 있을 때만 다른
// 로그인 실패(아이디/비밀번호 오류 등)와 달리 인라인 경고 대신 다이얼로그로 보여줍니다(더 눈에 띄게
// 안내하기 위함). 정지 사유가 있으면 이 문구보다 앞에 붙어서 위치가 바뀌므로, startsWith가 아니라
// includes로 비교합니다.
const SUSPENDED_MESSAGE_PREFIX = '이용이 정지된 사용자입니다.'

// 로그인 버튼의 로딩 스피너입니다.
function Spinner() {
  return (
    <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.37 0 0 5.37 0 12h4z" />
    </svg>
  )
}

// 실제 구글 로그인입니다 - 클릭하면 백엔드 OAuthController가 구글 로그인/동의 화면으로 브라우저를
// 이동시키고, 끝나면 /oauth/complete로 돌아와 로그인이 완료됩니다.
const SOCIAL_PROVIDERS = [
  { key: 'google', label: '구글로 로그인', icon: <GoogleIcon />, className: 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50' },
]

function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [capsLockOn, setCapsLockOn] = useState(false)
  const [keepLoggedIn, setKeepLoggedIn] = useState(false)
  const [error, setError] = useState('')
  const [suspendedMessage, setSuspendedMessage] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [showFindPassword, setShowFindPassword] = useState(false)
  const [showFindUsername, setShowFindUsername] = useState(false)

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
      const lastMissing = missing[missing.length - 1]
      setError(`${missing.join(', ')}${josa(lastMissing, ['을', '를'])} 입력해주세요.`)
      return
    }

    setError('')
    setIsSubmitting(true)
    try {
      goAfterLogin(await login(username, password, keepLoggedIn))
    } catch (err) {
      if (err.message.includes(SUSPENDED_MESSAGE_PREFIX)) {
        setSuspendedMessage(err.message)
      } else {
        setError(err.message)
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  // 비밀번호 칸에서 키를 누르거나 뗄 때마다 Caps Lock이 켜져 있는지 확인합니다 - 로그인 실패의
  // 흔한 원인이라, 입력 중에 바로 알려주면 "비밀번호가 틀립니다" 에러를 보기 전에 스스로 고칠 수 있습니다.
  const handlePasswordKeyEvent = (event) => {
    if (typeof event.getModifierState === 'function') {
      setCapsLockOn(event.getModifierState('CapsLock'))
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
          <div className="relative">
            <input
              id="password"
              type={showPassword ? 'text' : 'password'}
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              onKeyDown={handlePasswordKeyEvent}
              onKeyUp={handlePasswordKeyEvent}
              onBlur={() => setCapsLockOn(false)}
              placeholder="비밀번호를 입력하세요"
              className={`${inputClassName(passwordHasError)} pr-10`}
            />
            <button
              type="button"
              onClick={() => setShowPassword((prev) => !prev)}
              aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
            >
              {showPassword ? <EyeOffIcon /> : <EyeIcon />}
            </button>
          </div>
          {capsLockOn && (
            <p className="mt-1.5 text-xs font-semibold text-amber-600">⚠ Caps Lock이 켜져 있습니다</p>
          )}
        </div>

        {error && (
          <p className="rounded-lg border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-600">
            ⚠ {error}
          </p>
        )}

        <div className="flex items-center justify-between text-xs text-slate-500">
          <label className="flex items-center gap-1.5">
            <input
              type="checkbox"
              checked={keepLoggedIn}
              onChange={(event) => setKeepLoggedIn(event.target.checked)}
              className="rounded border-slate-300"
            />
            로그인 상태 유지
          </label>
          <span className="flex gap-2">
            <button type="button" onClick={() => setShowFindUsername(true)} className="text-indigo-600 hover:underline">
              아이디 찾기
            </button>
            <span className="text-slate-300">|</span>
            <button type="button" onClick={() => setShowFindPassword(true)} className="text-indigo-600 hover:underline">
              비밀번호 찾기
            </button>
          </span>
        </div>

        <button
          type="submit"
          disabled={isSubmitting}
          className="flex w-full items-center justify-center gap-2 rounded-lg bg-indigo-600 py-2.5 text-sm font-semibold text-white hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {isSubmitting && <Spinner />}
          {isSubmitting ? '로그인 중...' : '로그인'}
        </button>
      </form>

      <div className="mt-5 flex items-center gap-3">
        <span className="h-px flex-1 bg-slate-200" />
        <span className="text-xs text-slate-400">다른 방법으로 로그인</span>
        <span className="h-px flex-1 bg-slate-200" />
      </div>

      <div className="mt-3 space-y-2">
        {SOCIAL_PROVIDERS.map((provider) => (
          <a
            key={provider.key}
            href={`${BASE_URL}/api/auth/oauth/${provider.key}/start`}
            className={`relative block w-full rounded-lg py-2.5 text-center text-sm font-semibold transition ${provider.className}`}
          >
            <span className="absolute left-4 top-1/2 flex -translate-y-1/2 items-center">{provider.icon}</span>
            {provider.label}
          </a>
        ))}
      </div>

      {showFindPassword && (
        <FindPasswordDialog onClose={() => setShowFindPassword(false)} onAutoLogin={goAfterLogin} />
      )}

      {showFindUsername && (
        <FindUsernameDialog onClose={() => setShowFindUsername(false)} onFound={setUsername} />
      )}

      {suspendedMessage && (
        <InfoDialog title="로그인 불가" message={suspendedMessage} tone="error" onClose={() => setSuspendedMessage('')} />
      )}
    </AuthShell>
  )
}

export default LoginPage
