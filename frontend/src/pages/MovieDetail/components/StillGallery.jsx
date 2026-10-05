import { useState } from 'react'

function StillDialog({ stillUrls, index, onClose, onPrev, onNext }) {
  const hasMultiple = stillUrls.length > 1
  const displaySrc = stillUrls[index]

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 px-4"
      onClick={onClose}
    >
      <div className="relative" onClick={(event) => event.stopPropagation()}>
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className="absolute -top-10 right-0 flex h-8 w-8 items-center justify-center rounded-full bg-white/10 text-lg text-white hover:bg-white/20"
        >
          ✕
        </button>

        {hasMultiple && (
          <p className="absolute -top-10 left-0 text-sm text-gray-300">
            {index + 1} / {stillUrls.length}
          </p>
        )}

        {/* 다이얼로그를 사진 원본 크기 그대로 보여줍니다(화면보다 큰 경우에만 화면 안에 들어오게 줄임). */}
        <img
          src={displaySrc}
          alt={`스틸컷 ${index + 1}`}
          className="max-h-[90svh] max-w-[92vw] rounded-lg object-contain"
        />

        {hasMultiple && (
          <>
            <button
              type="button"
              onClick={onPrev}
              aria-label="이전 스틸컷"
              className="absolute left-2 top-1/2 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-full bg-white/10 text-xl text-white hover:bg-white/20"
            >
              ‹
            </button>
            <button
              type="button"
              onClick={onNext}
              aria-label="다음 스틸컷"
              className="absolute right-2 top-1/2 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-full bg-white/10 text-xl text-white hover:bg-white/20"
            >
              ›
            </button>
          </>
        )}
      </div>
    </div>
  )
}

// KMDB stlls(스틸컷)는 "|"로 여러 장이 이어 붙어 오는데, 백엔드가 이미 배열(stillUrls)로 쪼개서 줍니다.
function StillGallery({ stillUrls }) {
  const [selectedIndex, setSelectedIndex] = useState(null)

  if (!stillUrls || stillUrls.length === 0) return null

  const showPrev = () => setSelectedIndex((prev) => (prev - 1 + stillUrls.length) % stillUrls.length)
  const showNext = () => setSelectedIndex((prev) => (prev + 1) % stillUrls.length)

  return (
    <div className="mt-10">
      <h2 className="mb-3 text-lg font-bold text-gray-100">스틸컷 ({stillUrls.length})</h2>
      <div className="flex gap-3 overflow-x-auto pb-2">
        {stillUrls.map((url, index) => (
          <button
            key={url}
            type="button"
            onClick={() => setSelectedIndex(index)}
            className="block h-32 w-52 shrink-0 overflow-hidden rounded-lg bg-slate-800 transition-opacity hover:opacity-80"
          >
            <img src={url} alt={`스틸컷 ${index + 1}`} loading="lazy" className="h-full w-full object-cover" />
          </button>
        ))}
      </div>

      {selectedIndex !== null && (
        <StillDialog
          stillUrls={stillUrls}
          index={selectedIndex}
          onClose={() => setSelectedIndex(null)}
          onPrev={showPrev}
          onNext={showNext}
        />
      )}
    </div>
  )
}

export default StillGallery
