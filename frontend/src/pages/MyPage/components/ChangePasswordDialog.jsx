import { useState } from 'react'

// 회원가입과 동일한 규칙: 8자 이상, 영문+숫자 조합 또는 영문+숫자+특수문자 조합만(공백/한글 불가).
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z0-9!@#$%^&*()_+\-=[\]{}:;,.<>/?~]{8,}$/

function ChangePasswordDialog({ onClose, onSubmit }) {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState(false)

  const handleSubmit = async (event) => {
    event.preventDefault()
    setError('')
    if (!PASSWORD_PATTERN.test(newPassword)) {
      setError('비밀번호는 영문과 숫자를 포함해 8자 이상, 영문+숫자 또는 영문+숫자+특수문자 조합만 가능합니다.')
      return
    }
    if (newPassword !== confirmPassword) {
      setError('새 비밀번호가 서로 일치하지 않습니다.')
      return
    }
    setSubmitting(true)
    try {
      await onSubmit({ currentPassword, newPassword })
      setDone(true)
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
      <div className="w-full max-w-sm rounded-xl bg-slate-900 p-6">
        {done ? (
          <>
            <p className="text-sm font-semibold text-white">비밀번호가 변경되었습니다.</p>
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
            <h2 className="text-sm font-semibold text-white">비밀번호 변경</h2>
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-400">현재 비밀번호</label>
              <input
                type="password"
                required
                value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)}
                className="w-full rounded-lg bg-slate-800 px-3 py-2 text-sm text-white focus:outline-none"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-400">새 비밀번호</label>
              <input
                type="password"
                required
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                placeholder="영문, 숫자 포함 8자 이상(특수문자 가능)"
                className="w-full rounded-lg bg-slate-800 px-3 py-2 text-sm text-white focus:outline-none"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-400">새 비밀번호 확인</label>
              <input
                type="password"
                required
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                className="w-full rounded-lg bg-slate-800 px-3 py-2 text-sm text-white focus:outline-none"
              />
            </div>

            {error && <p className="text-xs text-red-400">{error}</p>}

            <div className="flex gap-2 pt-1">
              <button
                type="button"
                onClick={onClose}
                className="flex-1 rounded-lg border border-slate-700 py-2 text-sm font-semibold text-gray-300 hover:bg-slate-800"
              >
                취소
              </button>
              <button
                type="submit"
                disabled={submitting}
                className="flex-1 rounded-lg bg-indigo-600 py-2 text-sm font-semibold text-white hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {submitting ? '변경 중...' : '비밀번호 변경'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}

export default ChangePasswordDialog
