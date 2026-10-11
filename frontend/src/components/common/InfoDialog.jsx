// 네이티브 window.alert() 대신 쓰는, 서비스 전반과 같은 톤의 안내/결과 다이얼로그입니다(버튼 1개).
// message는 "\n"로 줄바꿈을 표현할 수 있습니다. tone="error"면 제목을 빨간색으로 강조합니다.
function InfoDialog({ title, message, onClose, tone = 'default', closeLabel = '확인' }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
      <div className="w-full max-w-sm rounded-xl bg-slate-900 p-6">
        {title && (
          <h2 className={`text-sm font-semibold ${tone === 'error' ? 'text-rose-400' : 'text-white'}`}>{title}</h2>
        )}
        <p className="mt-2 whitespace-pre-line text-sm text-gray-300">{message}</p>
        <button
          type="button"
          onClick={onClose}
          className="mt-5 w-full rounded-lg bg-indigo-600 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
        >
          {closeLabel}
        </button>
      </div>
    </div>
  )
}

export default InfoDialog
