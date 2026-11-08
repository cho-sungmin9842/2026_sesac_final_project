import { useEffect, useState } from 'react'
import { getAiSummary } from '../../../api/movieApi'

// "AI 줄거리 요약 (스포방지)" 버튼을 누르면 뜨는 다이얼로그. 열릴 때마다 Gemini에 요약을 요청합니다.
function AiSummaryDialog({ movieId, movieTitle, onClose }) {
  const [status, setStatus] = useState('loading') // loading | ready | error
  const [summary, setSummary] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const [readState, setReadState] = useState('idle') // idle | speaking | paused

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

  // 다이얼로그가 닫히거나 사라질 때 읽다 만 음성이 계속 나오지 않도록 멈춥니다.
  useEffect(() => {
    return () => {
      window.speechSynthesis?.cancel()
    }
  }, [])

  const handleToggleRead = () => {
    if (!window.speechSynthesis) return

    if (readState === 'speaking') {
      // cancel()은 처음부터 다시 읽게 만들어서, 멈춘 지점부터 이어가려면 pause()를 씁니다.
      window.speechSynthesis.pause()
      setReadState('paused')
      return
    }

    if (readState === 'paused') {
      window.speechSynthesis.resume()
      setReadState('speaking')
      return
    }

    const utterance = new SpeechSynthesisUtterance(summary)
    utterance.lang = 'ko-KR'
    utterance.onend = () => setReadState('idle')
    utterance.onerror = () => setReadState('idle')
    window.speechSynthesis.cancel()
    window.speechSynthesis.speak(utterance)
    setReadState('speaking')
  }

  const handleStopRead = () => {
    window.speechSynthesis?.cancel()
    setReadState('idle')
  }

  const handleClose = () => {
    window.speechSynthesis?.cancel()
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4" onClick={handleClose}>
      <div
        className="w-full max-w-md rounded-xl bg-slate-900 p-6"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between">
          <div>
            <h3 className="flex items-center gap-1 text-lg font-bold text-white">✨ AI 줄거리 요약</h3>
            <p className="mt-1 text-sm text-gray-400">{movieTitle} · 스포방지</p>
          </div>
          <div className="flex items-center gap-2">
            {status === 'ready' && (
              <>
                <button
                  type="button"
                  onClick={handleToggleRead}
                  aria-label={readState === 'speaking' ? '일시정지' : readState === 'paused' ? '이어서 읽기' : '내용 읽기'}
                  title={readState === 'speaking' ? '일시정지' : readState === 'paused' ? '이어서 읽기' : '내용 읽기'}
                  className="flex h-8 w-8 items-center justify-center rounded-full bg-indigo-500/10 text-base text-indigo-300 hover:bg-indigo-500/20"
                >
                  {readState === 'speaking' ? '⏸' : readState === 'paused' ? '▶' : '🔊'}
                </button>
                {readState !== 'idle' && (
                  <button
                    type="button"
                    onClick={handleStopRead}
                    aria-label="중지"
                    title="중지"
                    className="flex h-8 w-8 items-center justify-center rounded-full bg-indigo-500/10 text-base text-indigo-300 hover:bg-indigo-500/20"
                  >
                    ⏹
                  </button>
                )}
              </>
            )}
            <button
              type="button"
              onClick={handleClose}
              aria-label="닫기"
              className="flex h-8 w-8 items-center justify-center rounded-full text-lg text-gray-400 hover:bg-slate-800 hover:text-white"
            >
              ✕
            </button>
          </div>
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
