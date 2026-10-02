import { useState } from 'react'
import { resetPassword } from '../../../api/authApi'

// 이메일/SMS가 없는 데모 인증이라, 가입 시 입력한 아이디+닉네임이 일치하면 바로 새 비밀번호로 바꿔줍니다.
function FindPasswordDialog({ onClose }) {
  const [username, setUsername] = useState('')
  const [nickname, setNickname] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)

  const handleSubmit = async (event) => {
    event.preventDefault()
    setError('')
    if (newPassword !== confirmPassword) {
      setError('새 비밀번호가 서로 일치하지 않습니다.')
      return
    }
    try {
      await resetPassword({ username, nickname, newPassword })
      setDone(true)
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div className="w-full max-w-sm rounded-xl bg-white p-6">
        {done ? (
          <>
            <p className="text-sm font-semibold text-slate-900">비밀번호가 변경되었습니다.</p>
            <p className="mt-1 text-xs text-slate-500">새 비밀번호로 다시 로그인해주세요.</p>
            <button
              type="button"
              onClick={onClose}
              className="mt-4 w-full rounded-lg bg-indigo-600 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
            >
              확인
            </button>
          </>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-3">
            <h2 className="text-sm font-semibold text-slate-900">비밀번호 찾기</h2>
            <p className="text-xs text-slate-500">
              가입 시 입력한 아이디와 닉네임이 일치하면 새 비밀번호로 바로 변경할 수 있습니다.
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
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-700">새 비밀번호</label>
              <input
                type="password"
                required
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-700">새 비밀번호 확인</label>
              <input
                type="password"
                required
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
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
                비밀번호 변경
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}

export default FindPasswordDialog
