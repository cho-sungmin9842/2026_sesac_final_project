import { Link } from 'react-router-dom'

const FEATURES = ['AI 줄거리 요약 · 스포일러 방지', '대화형 AI 추천 챗봇', '나만의 찜 목록과 리뷰 기록']

function AuthShell({ mode, tagline, children }) {
  return (
    <div className="flex min-h-svh bg-slate-100">
      <div className="relative hidden w-1/2 overflow-hidden bg-slate-950 md:flex md:flex-col md:justify-center md:px-16">
        <div
          className="pointer-events-none absolute inset-0 opacity-70"
          style={{
            backgroundImage:
              'repeating-linear-gradient(115deg, #1e1b4b 0 60px, #0f172a 60px 120px, #3b0764 120px 180px, #0f172a 180px 240px)',
          }}
        />
        <div className="relative">
          <div className="mb-6 flex items-center gap-2 text-xl font-bold text-white">
            <span className="flex h-9 w-9 items-center justify-center rounded-md bg-gradient-to-br from-indigo-500 to-purple-500">
              🎬
            </span>
            MOVIEPICK
          </div>
          <p className="max-w-xs text-lg font-semibold text-white">{tagline}</p>
          <ul className="mt-6 space-y-2 text-sm text-gray-300">
            {FEATURES.map((feature) => (
              <li key={feature} className="flex items-center gap-2">
                <span className="text-emerald-400">✅</span>
                {feature}
              </li>
            ))}
          </ul>
        </div>
      </div>

      <div className="flex w-full items-center justify-center px-6 py-12 md:w-1/2">
        <div className="w-full max-w-sm">
          <div className="mb-6 flex rounded-lg bg-slate-200 p-1">
            <Link
              to="/login"
              className={`flex-1 rounded-md py-2 text-center text-sm font-semibold transition-colors ${
                mode === 'login' ? 'bg-white text-slate-900 shadow' : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              로그인
            </Link>
            <Link
              to="/signup"
              className={`flex-1 rounded-md py-2 text-center text-sm font-semibold transition-colors ${
                mode === 'signup' ? 'bg-white text-slate-900 shadow' : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              회원가입
            </Link>
          </div>

          {children}
        </div>
      </div>
    </div>
  )
}

export default AuthShell
