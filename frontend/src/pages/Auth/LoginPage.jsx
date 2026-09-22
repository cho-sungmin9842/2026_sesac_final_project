import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import AuthShell from './components/AuthShell'

const SOCIAL_BUTTONS = [
  { key: 'kakao', label: '카카오', dot: '●', className: 'border-transparent bg-[#FEE500] text-slate-900' },
  { key: 'naver', label: '네이버', dot: '●', className: 'border-transparent bg-[#03C75A] text-white' },
  { key: 'google', label: '구글', dot: '●', className: 'border-slate-300 bg-white text-slate-700' },
]

function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const goAfterLogin = (user) => {
    if (user.isAdmin) {
      navigate('/admin/reports', { replace: true })
      return
    }
    navigate(location.state?.from?.pathname ?? '/', { replace: true })
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    if (!email || !password) return
    goAfterLogin(login(email))
  }

  const handleSocialLogin = (provider) => {
    goAfterLogin(login(`${provider}-user@moviepick.example.com`))
  }

  return (
    <AuthShell mode="login" tagline="당신의 취향을 배우는 AI와 함께, 오늘 볼 영화를 가장 빠르게 찾아보세요">
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="email" className="mb-1 block text-sm font-medium text-slate-700">
            이메일
          </label>
          <input
            id="email"
            type="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="example@email.com"
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none"
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
            placeholder="••••••••"
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none"
          />
        </div>

        <div className="flex items-center justify-between text-xs text-slate-500">
          <label className="flex items-center gap-1.5">
            <input type="checkbox" className="rounded border-slate-300" />
            로그인 상태 유지
          </label>
          <button type="button" className="text-indigo-600 hover:underline">
            비밀번호 찾기
          </button>
        </div>

        <button
          type="submit"
          className="w-full rounded-lg bg-indigo-600 py-2.5 text-sm font-semibold text-white hover:bg-indigo-500"
        >
          로그인
        </button>

        <div className="flex items-center gap-3 text-xs text-slate-400">
          <span className="h-px flex-1 bg-slate-200" />
          간편 로그인
          <span className="h-px flex-1 bg-slate-200" />
        </div>

        <div className="grid grid-cols-3 gap-2">
          {SOCIAL_BUTTONS.map((social) => (
            <button
              key={social.key}
              type="button"
              onClick={() => handleSocialLogin(social.key)}
              className={`rounded-lg border px-2 py-2 text-xs font-semibold ${social.className}`}
            >
              {social.label}
            </button>
          ))}
        </div>

        <p className="pt-2 text-center text-xs text-slate-500">
          관리자 계정으로 로그인하면 자동으로 관리자 대시보드로 이동합니다.
          <br />
          아직 계정이 없으신가요?{' '}
          <Link to="/signup" className="font-semibold text-indigo-600 hover:underline">
            회원가입
          </Link>
        </p>
      </form>
    </AuthShell>
  )
}

export default LoginPage
