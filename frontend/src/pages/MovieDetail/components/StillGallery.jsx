import { useEffect, useState } from 'react'

// KMDB 스틸컷 원본이 실제로 200x100px 안팎일 정도로 작아서, 너무 키우면 흐려집니다.
// 화질과 크기의 절충으로 원본의 2배까지만 확대합니다.
const MAX_UPSCALE = 2

// 2배 정도의 적당한 확대에서도 번져 보이는 걸 줄이기 위한 SVG 샤프닝(언샵 마스크) 필터입니다.
// 8~10배로 늘렸을 때는 이 필터로도 한계가 있었지만, 지금처럼 배율이 낮으면 효과가 분명합니다.
function SharpenFilterDefs() {
  return (
    <svg width="0" height="0" className="absolute">
      <filter id="still-sharpen">
        <feConvolveMatrix order="3" kernelMatrix="0 -1 0 -1 5 -1 0 -1 0" preserveAlpha="true" />
      </filter>
    </svg>
  )
}

function StillDialog({ stillUrls, index, onClose, onPrev, onNext }) {
  const hasMultiple = stillUrls.length > 1
  const [naturalSize, setNaturalSize] = useState(null)

  // 이미지가 바뀌면(이전/다음) 새 원본 크기를 다시 재야 합니다.
  useEffect(() => {
    setNaturalSize(null)
  }, [index])

  const capStyle = naturalSize
    ? { maxWidth: naturalSize.width * MAX_UPSCALE, maxHeight: naturalSize.height * MAX_UPSCALE }
    : {}

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 px-4"
      onClick={onClose}
    >
      <SharpenFilterDefs />
      <div
        className="relative flex h-[85svh] w-[90vw] max-w-5xl items-center justify-center"
        onClick={(event) => event.stopPropagation()}
      >
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

        <img
          src={stillUrls[index]}
          alt={`스틸컷 ${index + 1}`}
          onLoad={(event) =>
            setNaturalSize({ width: event.target.naturalWidth, height: event.target.naturalHeight })
          }
          className="h-full w-full rounded-lg object-contain"
          style={{ ...capStyle, filter: 'url(#still-sharpen) contrast(1.06)', imageRendering: '-webkit-optimize-contrast' }}
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
