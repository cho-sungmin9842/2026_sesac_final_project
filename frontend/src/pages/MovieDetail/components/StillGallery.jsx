import { useState } from 'react'

// KMDB 스틸컷 원본은 대체로 200px대의 작은 썸네일이라(실측: 207×138px 수준), 그대로 작게 띄우면
// 휑해 보입니다. CGV 등 극장 사이트의 이미지 갤러리처럼 모든 컷이 같은 크기의 고정 프레임 안에서
// 슬라이드로 넘어가도록 하고, 프레임 자체는 원본보다 한 단계 넉넉하게(약 1.25배 수준) 잡았습니다.
const FRAME_WIDTH = 320
const FRAME_HEIGHT = 213

function StillDialog({ stillUrls, index, onClose, onPrev, onNext }) {
  const hasMultiple = stillUrls.length > 1

  return (
    <div
      className="theme-fixed fixed inset-0 z-50 flex items-center justify-center bg-black/80 px-4"
      onClick={onClose}
    >
      <div className="relative" onClick={(event) => event.stopPropagation()}>
        {/* 카운터/닫기 버튼은 사진 위에 얹지 않고, 프레임 바로 위에 별도 줄로 둡니다(일반 문서
            흐름이라 사진과 겹칠 일이 없습니다). */}
        <div className="mb-2 flex items-center justify-between" style={{ maxWidth: '92vw' }}>
          {hasMultiple ? (
            <p className="text-sm font-medium text-gray-200">
              {index + 1} / {stillUrls.length}
            </p>
          ) : (
            <span />
          )}
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="flex h-8 w-8 items-center justify-center rounded-full bg-white/10 text-lg text-white hover:bg-white/20"
          >
            ✕
          </button>
        </div>

        {/* 고정 크기 프레임 안에서 사진들을 가로로 나란히 두고, index에 맞춰 translateX로 밀어
            CGV 갤러리처럼 좌우로 슬라이드되는 전환 효과를 줍니다. object-cover로 프레임을 빈틈없이
            채웁니다(비율이 안 맞는 가장자리만 살짝 잘립니다). */}
        <div
          className="relative overflow-hidden rounded-lg bg-black"
          style={{ width: FRAME_WIDTH, height: FRAME_HEIGHT, maxWidth: '92vw', maxHeight: '70svh' }}
        >
          <div
            className="flex h-full transition-transform duration-300 ease-out"
            style={{ width: `${stillUrls.length * 100}%`, transform: `translateX(-${(index * 100) / stillUrls.length}%)` }}
          >
            {stillUrls.map((url, i) => (
              <div key={url} className="h-full shrink-0" style={{ width: `${100 / stillUrls.length}%` }}>
                <img src={url} alt={`스틸컷 ${i + 1}`} loading="lazy" className="h-full w-full object-cover" />
              </div>
            ))}
          </div>
        </div>

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
