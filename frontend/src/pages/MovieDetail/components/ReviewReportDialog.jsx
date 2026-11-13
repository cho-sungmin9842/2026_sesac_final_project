import { useState } from 'react'
import { REVIEW_REPORT_REASONS } from './reviewReportReasons'

// 리뷰 목록의 "신고" 버튼을 눌렀을 때 사유를 먼저 고르게 하는 다이얼로그입니다(관리자 "회원 정지"의
// SuspendReasonDialog와 같은 톤) - 사유 선택 전에는 "신고" 버튼이 비활성화됩니다.
function ReviewReportDialog({ onConfirm, onCancel }) {
  const [selectedReason, setSelectedReason] = useState('')

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
      <div className="w-full max-w-sm rounded-xl bg-slate-900 p-6">
        <h2 className="text-sm font-semibold text-white">리뷰 신고</h2>
        <p className="mt-2 text-sm text-gray-300">신고 사유를 선택해주세요. 관리자가 확인 후 처리합니다.</p>

        <div className="mt-4 space-y-2">
          {REVIEW_REPORT_REASONS.map((reason) => (
            <label
              key={reason.key}
              className={`flex cursor-pointer items-center gap-2.5 rounded-lg border px-3 py-2.5 text-sm ${
                selectedReason === reason.key
                  ? 'border-rose-500/60 bg-rose-500/10 text-gray-200'
                  : 'border-slate-700 text-gray-300 hover:bg-slate-800'
              }`}
            >
              <input
                type="radio"
                name="review-report-reason"
                value={reason.key}
                checked={selectedReason === reason.key}
                onChange={() => setSelectedReason(reason.key)}
                className="accent-rose-500"
              />
              {reason.label}
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
            신고
          </button>
        </div>
      </div>
    </div>
  )
}

export default ReviewReportDialog
