import { useState } from 'react'
import { findUsername } from '../../../api/authApi'
import { EyeIcon, EyeOffIcon } from '../../../components/icons/EyeIcons'

// 이메일/SMS가 없는 데모 인증이라, 가입 시 입력한 닉네임+비밀번호가 일치하면 아이디를 알려줍니다
// (FindPasswordDialog의 반대 방향: 거긴 아이디+닉네임으로 비밀번호를, 여긴 닉네임+비밀번호로 아이디를 찾습니다).
function FindUsernameDialog({ onClose, onFound }) {
  const [nickname, setNickname] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [foundUsername, setFoundUsername] = useState(null)

  const handleSubmit = async (event) => {
    event.preventDefault()
    setError('')
    try {
      const { username } = await findUsername({ nickname, password })
      setFoundUsername(username)
    } catch (err) {
      setError(err.message)
    }
  }

  const handleConfirm = () => {
    onFound?.(foundUsername)
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div className="w-full max-w-sm rounded-xl bg-white p-6">
        {foundUsername ? (
          <>
            <p className="text-sm font-semibold text-slate-900">아이디를 찾았습니다.</p>
            <p className="mt-3 rounded-lg bg-slate-100 px-3 py-2 text-center font-mono text-sm font-semibold text-slate-900">
              {foundUsername}
            </p>
            <button
              type="button"
              onClick={handleConfirm}
              className="mt-4 w-full rounded-lg bg-indigo-600 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
            >
              이 아이디로 로그인하기
            </button>
          </>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-3">
            <h2 className="text-sm font-semibold text-slate-900">아이디 찾기</h2>
            <p className="text-xs text-slate-500">가입 시 입력한 닉네임과 비밀번호가 일치하면 아이디를 알려드립니다.</p>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-700">닉네임</label>
              <input
                type="text"
                required
                value={nickname}
                onChange={(event) => setNickname(event.target.value)}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-700">비밀번호</label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  required
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 pr-10 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none"
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
            </div>

            {error && <p className="text-xs text-red-500">{error}</p>}

            <div className="flex gap-2 pt-1">
              <button
                type="button"
                onClick={onClose}
                className="flex-1 rounded-lg border border-slate-300 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50"
              >
                취소
              </button>
              <button
                type="submit"
                className="flex-1 rounded-lg bg-indigo-600 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
              >
                아이디 찾기
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}

export default FindUsernameDialog
