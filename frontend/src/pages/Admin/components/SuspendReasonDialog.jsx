import { useState } from 'react'
import { SUSPENSION_REASONS } from '../suspensionReasons'

// 관리자가 회원을 정지시킬 때 사유를 먼저 고르게 하는 다이얼로그입니다(ConfirmDialog와 같은 톤).
// 정지 해제는 사유가 필요 없어 이 다이얼로그를 거치지 않습니다 - AdminMembersPage 참고.
function SuspendReasonDialog({ member, onConfirm, onCancel }) {
  const [selectedReason, setSelectedReason] = useState('')

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
      <div className="w-full max-w-sm rounded-xl bg-slate-900 p-6">
        <h2 className="text-sm font-semibold text-white">회원 정지</h2>
        <p className="mt-2 text-sm text-gray-300">
          {`"${member.nickname}"(${member.username}) 계정을 정지할까요? 정지 사유를 선택해주세요.`}
        </p>

        <div className="mt-4 space-y-2">
          {SUSPENSION_REASONS.map((reason) => (
            <label
              key={reason.key}
              className={`flex cursor-pointer items-start gap-2.5 rounded-lg border px-3 py-2.5 text-sm ${
                selectedReason === reason.key
                  ? 'border-rose-500/60 bg-rose-500/10'
                  : 'border-slate-700 hover:bg-slate-800'
              }`}
            >
              <input
                type="radio"
                name="suspend-reason"
                value={reason.key}
                checked={selectedReason === reason.key}
                onChange={() => setSelectedReason(reason.key)}
                className="mt-0.5 accent-rose-500"
              />
              <span>
                <span className="block font-semibold text-gray-200">{reason.category}</span>
                <span className="block text-xs text-gray-400">{reason.detail}</span>
              </span>
            </label>
          ))}
        </div>

        <div className="mt-5 flex gap-2">
          <button
            type="button"
            onClick={onCancel}
            className="flex-1 rounded-lg border border-slate-700 py-2 text-sm font-semibold text-gray-300 hover:bg-slate-800"
          >
            취소
          </button>
          <button
            type="button"
            disabled={!selectedReason}
            onClick={() => onConfirm(selectedReason)}
            className="flex-1 rounded-lg bg-rose-600 py-2 text-sm font-semibold text-white hover:bg-rose-500 disabled:cursor-not-allowed disabled:opacity-50"
          >
            정지
          </button>
        </div>
      </div>
    </div>
  )
}

export default SuspendReasonDialog
