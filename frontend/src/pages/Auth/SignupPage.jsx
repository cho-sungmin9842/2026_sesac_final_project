import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import AuthShell from './components/AuthShell'
import { josa } from '../../utils/korean'
import { EyeIcon, EyeOffIcon } from '../../components/icons/EyeIcons'

// 비밀번호 규칙 체크리스트용 아이콘 - 만족하면 채워진 체크, 아니면 X 표시입니다.
function RuleIcon({ passed }) {
  return passed ? (
    <svg viewBox="0 0 20 20" width="16" height="16" fill="currentColor" aria-hidden="true">
      <path
        fillRule="evenodd"
        d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.7-9.3a1 1 0 00-1.4-1.4L9 10.6 7.7 9.3a1 1 0 00-1.4 1.4l2 2a1 1 0 001.4 0l4-4z"
        clipRule="evenodd"
      />
    </svg>
  ) : (
    <svg viewBox="0 0 20 20" width="16" height="16" fill="currentColor" aria-hidden="true">
      <path
        fillRule="evenodd"
        d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.7 7.3a1 1 0 00-1.4 1.4L8.6 10l-1.3 1.3a1 1 0 101.4 1.4L10 11.4l1.3 1.3a1 1 0 001.4-1.4L11.4 10l1.3-1.3a1 1 0 00-1.4-1.4L10 8.6 8.7 7.3z"
        clipRule="evenodd"
      />
    </svg>
  )
}

// 아이디: 영문+숫자 조합만(한글/공백/특수문자 불가, 영문과 숫자 둘 다 포함).
const USERNAME_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z0-9]+$/
// 비밀번호: 8자 이상, 영문+숫자 조합 또는 영문+숫자+특수문자 조합만(영문·숫자는 필수, 특수문자는 선택,
// 공백/한글 불가) - 백엔드 SignupRequest의 @Pattern과 동일한 규칙입니다.
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z0-9!@#$%^&*()_+\-=[\]{}:;,.<>/?~]{8,}$/
const PASSWORD_ALLOWED_CHARS = /^[A-Za-z0-9!@#$%^&*()_+\-=[\]{}:;,.<>/?~]*$/

// 비밀번호 입력란 아래 실시간 체크리스트 - 제출 후 에러로만 알려주는 대신, 타이핑하는 동안 바로
// 어떤 조건을 만족했는지 보여줍니다.
const PASSWORD_RULES = [
  { key: 'length', label: '8자 이상', test: (pw) => pw.length >= 8 },
  { key: 'letter', label: '영문 포함', test: (pw) => /[A-Za-z]/.test(pw) },
  { key: 'digit', label: '숫자 포함', test: (pw) => /\d/.test(pw) },
  { key: 'charset', label: '한글·공백 제외', test: (pw) => PASSWORD_ALLOWED_CHARS.test(pw) },
]

function SignupPage() {
  const { signup } = useAuth()
  const navigate = useNavigate()

  const [nickname, setNickname] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false)
  const [passwordFocused, setPasswordFocused] = useState(false)
  const [agreed, setAgreed] = useState(false)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSubmit = async (event) => {
    event.preventDefault()
    if (isSubmitting) return

    // 브라우저 기본 검증 팝업(required) 대신, 다른 에러와 똑같은 스타일의 경고 박스로 어떤 항목을
    // 안 채웠는지 알려줍니다 - 하나라도 비어있으면 가입을 시도하지 않고 여기서 멈춥니다.
    const missing = []
    if (!nickname.trim()) missing.push('닉네임')
    if (!username.trim()) missing.push('아이디')
    if (!password) missing.push('비밀번호')
    if (!passwordConfirm) missing.push('비밀번호 확인')
    if (missing.length > 0) {
      const lastMissing = missing[missing.length - 1]
      setError(`${missing.join(', ')}${josa(lastMissing, ['을', '를'])} 입력해주세요.`)
      return
    }

    if (!USERNAME_PATTERN.test(username.trim())) {
      setError('아이디는 영문과 숫자 조합만 가능합니다.')
      return
    }
    if (!PASSWORD_PATTERN.test(password)) {
      setError('비밀번호는 영문과 숫자를 포함해 8자 이상, 영문+숫자 또는 영문+숫자+특수문자 조합만 가능합니다.')
      return
    }
    if (password !== passwordConfirm) {
      setError('비밀번호가 서로 일치하지 않습니다.')
      return
    }
    if (!agreed) {
      setError('이용약관 및 개인정보 처리방침에 동의해주세요.')
      return
    }

    setError('')
    setIsSubmitting(true)
    try {
      const user = await signup(nickname, username, password)
      navigate(user.isAdmin ? '/admin/reports' : '/', { replace: true })
    } catch (err) {
      setError(err.message)
    } finally {
      setIsSubmitting(false)
    }
  }

  // 비어있는 항목이거나 에러 문구에 "닉네임"/"아이디"/"비밀번호"가 들어있으면 그 입력란 테두리를
  // 빨갛게 강조합니다(어떤 칸이 문제인지 빨간 텍스트 한 줄만으로는 눈에 잘 안 띄던 점을 보완).
  const nicknameHasError = Boolean(error) && (!nickname.trim() || error.includes('닉네임'))
  const usernameHasError = Boolean(error) && (!username.trim() || error.includes('아이디'))
  const passwordHasError = Boolean(error) && (!password || !passwordConfirm || error.includes('비밀번호'))

  const inputClassName = (hasError) =>
    `w-full rounded-lg border px-3 py-2 text-sm text-slate-900 focus:outline-none ${
      hasError
        ? 'border-red-400 focus:border-red-500'
        : 'border-slate-300 focus:border-indigo-500'
    }`

  return (
    <AuthShell mode="signup" tagline="정보 탐색부터 예매까지, AI 하나로 끝내세요">
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <div>
          <label htmlFor="nickname" className="mb-1 block text-sm font-medium text-slate-700">
            닉네임
          </label>
          <input
            id="nickname"
            type="text"
            required
            value={nickname}
            onChange={(event) => setNickname(event.target.value)}
            placeholder="영화조아"
            className={inputClassName(nicknameHasError)}
          />
        </div>

        <div>
          <label htmlFor="signup-username" className="mb-1 block text-sm font-medium text-slate-700">
            아이디
          </label>
          <input
            id="signup-username"
            type="text"
            required
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            placeholder="영문, 숫자 조합"
            className={inputClassName(usernameHasError)}
          />
        </div>

        <div>
          <label htmlFor="signup-password" className="mb-1 block text-sm font-medium text-slate-700">
            비밀번호
          </label>
          <div className="relative">
            <input
              id="signup-password"
              type={showPassword ? 'text' : 'password'}
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              onFocus={() => setPasswordFocused(true)}
              onBlur={() => setPasswordFocused(false)}
              placeholder="비밀번호를 입력해주세요"
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

          {(passwordFocused || password.length > 0) && (
            <ul className="mt-2 grid grid-cols-2 gap-x-3 gap-y-1.5 text-sm">
              {PASSWORD_RULES.map((rule) => {
                const passed = rule.test(password)
                return (
                  <li
                    key={rule.key}
                    className={`flex items-center gap-1.5 font-semibold ${
                      passed ? 'text-emerald-600' : 'text-slate-500'
                    }`}
                  >
                    <RuleIcon passed={passed} />
                    {rule.label}
                  </li>
                )
              })}
            </ul>
          )}
        </div>

        <div>
          <label htmlFor="password-confirm" className="mb-1 block text-sm font-medium text-slate-700">
            비밀번호 확인
          </label>
          <div className="relative">
            <input
              id="password-confirm"
              type={showPasswordConfirm ? 'text' : 'password'}
              required
              value={passwordConfirm}
              onChange={(event) => setPasswordConfirm(event.target.value)}
              placeholder="비밀번호를 다시 입력해주세요"
              className={`${inputClassName(passwordHasError)} pr-10`}
            />
            <button
              type="button"
              onClick={() => setShowPasswordConfirm((prev) => !prev)}
              aria-label={showPasswordConfirm ? '비밀번호 숨기기' : '비밀번호 보기'}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
            >
              {showPasswordConfirm ? <EyeOffIcon /> : <EyeIcon />}
            </button>
          </div>

          {passwordConfirm.length > 0 && (
            <p
              className={`mt-2 flex items-center gap-1.5 text-sm font-semibold ${
                password === passwordConfirm ? 'text-emerald-600' : 'text-rose-600'
              }`}
            >
              <RuleIcon passed={password === passwordConfirm} />
              {password === passwordConfirm ? '비밀번호가 일치합니다' : '비밀번호가 일치하지 않습니다'}
            </p>
          )}
        </div>

        <label className="flex items-center gap-1.5 text-xs text-slate-500">
          <input
            type="checkbox"
            checked={agreed}
            onChange={(event) => setAgreed(event.target.checked)}
            className="rounded border-slate-300"
          />
          (필수) 이용약관 및 개인정보 처리방침에 동의합니다
        </label>

        {error && (
          <p className="rounded-lg border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-600">
            ⚠ {error}
          </p>
        )}

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full rounded-lg bg-indigo-600 py-2.5 text-sm font-semibold text-white hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {isSubmitting ? '가입 처리 중...' : '가입하기'}
        </button>

        <p className="pt-2 text-center text-xs text-slate-500">
          이미 계정이 있으신가요?{' '}
          <Link to="/login" className="font-semibold text-indigo-600 hover:underline">
            로그인
          </Link>
        </p>
      </form>
    </AuthShell>
  )
}

export default SignupPage
