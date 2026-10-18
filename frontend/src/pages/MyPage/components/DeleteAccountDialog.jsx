import { useState } from 'react'

function DeleteAccountDialog({ onClose, onSubmit }) {
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (event) => {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      await onSubmit(password)
    } catch (err) {
      setError(err.message)
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
      <div className="w-full max-w-sm rounded-xl bg-slate-900 p-6">
        <h2 className="text-sm font-semibold text-white">회원 탈퇴</h2>
        <p className="mt-2 text-xs text-gray-400">
          탈퇴하면 찜한 영화, 리뷰, 예매 내역, 시청완료 기록 등 모든 정보가 삭제되며 되돌릴 수 없습니다.
          본인 확인을 위해 비밀번호를 입력해주세요.
        </p>

        <form onSubmit={handleSubmit} className="mt-4 space-y-3">
          <input
            type="password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="비밀번호"
            className="w-full rounded-lg bg-slate-800 px-3 py-2 text-sm text-white focus:outline-none"
          />

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
              className="flex-1 rounded-lg bg-rose-600 py-2 text-sm font-semibold text-white hover:bg-rose-500 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {submitting ? '탈퇴 처리 중...' : '탈퇴하기'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default DeleteAccountDialog
