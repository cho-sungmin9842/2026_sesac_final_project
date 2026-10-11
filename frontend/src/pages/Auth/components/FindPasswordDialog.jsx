import { useState } from 'react'
import { useAuth } from '../../../auth/AuthContext'
import { resetPassword } from '../../../api/authApi'

// 이메일/SMS가 없는 데모 인증이라, 가입 시 입력한 아이디+닉네임이 일치하면 서버가 임시 비밀번호를
// 발급하고 바로 그 계정으로 로그인시켜줍니다(사용자가 새 비밀번호를 직접 입력하지 않습니다).
function FindPasswordDialog({ onClose, onAutoLogin }) {
  const { setSession } = useAuth()
  const [username, setUsername] = useState('')
  const [nickname, setNickname] = useState('')
  const [error, setError] = useState('')
  const [result, setResult] = useState(null)

  const handleSubmit = async (event) => {
    event.preventDefault()
    setError('')
    try {
      const { user, tempPassword } = await resetPassword({ username, nickname })
      setSession(user)
      // 여기서 바로 이동시키면 임시 비밀번호를 보여주기도 전에 화면이 넘어가버리니, 사용자가 "확인"을
      // 눌러 다 읽은 뒤에 이동시킵니다.
      setResult({ user, tempPassword })
    } catch (err) {
      setError(err.message)
    }
  }

  const handleConfirm = () => {
    onAutoLogin?.(result.user)
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div className="w-full max-w-sm rounded-xl bg-white p-6">
        {result ? (
          <>
            <p className="text-sm font-semibold text-slate-900">로그인되었습니다.</p>
            <p className="mt-2 text-xs text-slate-500">
              임시 비밀번호가 발급되어 자동으로 로그인되었습니다. 혹시 로그아웃되면 아래 임시 비밀번호로 다시
              로그인할 수 있어요. 마이페이지에서 원하는 비밀번호로 바꿔주세요.
            </p>
            <p className="mt-3 rounded-lg bg-slate-100 px-3 py-2 text-center font-mono text-sm font-semibold text-slate-900">
              {result.tempPassword}
            </p>
            <button
              type="button"
              onClick={handleConfirm}
              className="mt-4 w-full rounded-lg bg-indigo-600 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
            >
              확인
            </button>
          </>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-3">
            <h2 className="text-sm font-semibold text-slate-900">비밀번호 찾기</h2>
            <p className="text-xs text-slate-500">
              가입 시 입력한 아이디와 닉네임이 일치하면 임시 비밀번호를 발급해 바로 로그인해드립니다.
            </p>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-700">아이디</label>
              <input
                type="text"
                required
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none"
              />
            </div>
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
                임시 비밀번호 발급
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}

export default FindPasswordDialog
