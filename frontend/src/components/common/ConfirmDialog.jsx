// 네이티브 window.confirm() 대신 쓰는, 서비스 전반(리뷰 등록·선호장르 저장 등)과 같은 톤의 확인
// 다이얼로그입니다. message는 "\n"로 줄바꿈을 표현할 수 있습니다.
function ConfirmDialog({ title, message, confirmLabel = '확인', cancelLabel = '취소', onConfirm, onCancel, danger = false }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
      <div className="w-full max-w-sm rounded-xl bg-slate-900 p-6">
        {title && <h2 className="text-sm font-semibold text-white">{title}</h2>}
        <p className="mt-2 whitespace-pre-line text-sm text-gray-300">{message}</p>
        <div className="mt-5 flex gap-2">
          <button
            type="button"
            onClick={onCancel}
            className="flex-1 rounded-lg border border-slate-700 py-2 text-sm font-semibold text-gray-300 hover:bg-slate-800"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className={`flex-1 rounded-lg py-2 text-sm font-semibold text-white ${
              danger ? 'bg-rose-600 hover:bg-rose-500' : 'bg-indigo-600 hover:bg-indigo-500'
            }`}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

export default ConfirmDialog
