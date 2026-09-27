import { useEffect, useState } from 'react'
import { getAiSummary } from '../../../api/movieApi'

// "AI 줄거리 요약 (스포방지)" 버튼을 누르면 뜨는 다이얼로그. 열릴 때마다 Gemini에 요약을 요청합니다.
function AiSummaryDialog({ movieId, movieTitle, onClose }) {
  const [status, setStatus] = useState('loading') // loading | ready | error
  const [summary, setSummary] = useState('')
  const [errorMessage, setErrorMessage] = useState('')

  useEffect(() => {
    let cancelled = false
    setStatus('loading')

    getAiSummary(movieId)
      .then((dto) => {
        if (cancelled) return
        setSummary(dto.summary)
        setStatus('ready')
      })
      .catch((error) => {
        if (cancelled) return
        setErrorMessage(error.message)
        setStatus('error')
      })

    return () => {
      cancelled = true
    }
  }, [movieId])

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4" onClick={onClose}>
      <div
        className="w-full max-w-md rounded-xl bg-slate-900 p-6"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between">
          <div>
            <h3 className="flex items-center gap-1 text-lg font-bold text-white">✨ AI 줄거리 요약</h3>
            <p className="mt-1 text-sm text-gray-400">{movieTitle} · 스포방지</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="flex h-8 w-8 items-center justify-center rounded-full text-lg text-gray-400 hover:bg-slate-800 hover:text-white"
          >
            ✕
          </button>
        </div>

        <div className="mt-4 min-h-[4rem]">
          {status === 'loading' && (
            <p className="text-sm text-gray-400">Gemini가 스포일러 없이 요약하는 중...</p>
          )}
          {status === 'error' && <p className="text-sm text-red-400">{errorMessage}</p>}
          {status === 'ready' && <p className="text-sm leading-relaxed text-gray-200">{summary}</p>}
        </div>
      </div>
    </div>
  )
}

function AiSummaryBox({ movieId, movieTitle }) {
  const [isOpen, setOpen] = useState(false)

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="rounded-lg bg-indigo-500/10 px-4 py-2 text-sm font-semibold text-indigo-300 hover:bg-indigo-500/20"
      >
        ✨ AI 줄거리 요약 (스포방지)
      </button>

      {isOpen && (
        <AiSummaryDialog movieId={movieId} movieTitle={movieTitle} onClose={() => setOpen(false)} />
      )}
    </>
  )
}

export default AiSummaryBox
