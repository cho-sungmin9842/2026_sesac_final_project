import { useLayoutEffect, useRef, useState } from 'react'

// CSS @keyframes 애니메이션은 매번 "밀려나 있는 시작 상태"로 뚝 떨어졌다가 재생되는 느낌이 들어
// 부드럽지 않았습니다. 대신 같은 <img> 엘리먼트를 그대로 두고 transform/opacity를 직접
// transition으로 보간합니다 - 시작 상태를 지정 → 강제 리플로우 → 끝 상태로 전환, 순서로 2프레임
// 뒤에 트랜지션을 거는 방식이라 브라우저가 매 프레임 보간해 훨씬 매끄럽게 움직입니다.
const SLIDE_OFFSET_PX = 32
const SLIDE_DURATION_MS = 320
const SLIDE_EASING = 'cubic-bezier(0.22, 1, 0.36, 1)'

function StillDialog({ stillUrls, index, onClose, onPrev, onNext }) {
  const hasMultiple = stillUrls.length > 1
  const isFirst = index === 0
  const isLast = index === stillUrls.length - 1

  const imgRef = useRef(null)
  const prevIndexRef = useRef(index)
  const [src, setSrc] = useState(stillUrls[index])

  useLayoutEffect(() => {
    const direction = index >= prevIndexRef.current ? 'next' : 'prev'
    prevIndexRef.current = index

    const img = imgRef.current
    if (!img) {
      setSrc(stillUrls[index])
      return
    }

    // 1) 트랜지션을 끈 채로, 이동 방향 쪽에 밀려나 투명한 "시작 상태"로 이미지를 바꿉니다.
    img.style.transition = 'none'
    img.style.opacity = '0'
    img.style.transform = `translateX(${direction === 'next' ? SLIDE_OFFSET_PX : -SLIDE_OFFSET_PX}px)`
    setSrc(stillUrls[index])

    // 2) 두 프레임 뒤(브라우저가 "시작 상태"를 실제로 한 번 그리고 난 뒤) 트랜지션을 켜고
    //    "끝 상태"로 되돌려, 그 사이가 부드럽게 보간되도록 합니다.
    const raf1 = requestAnimationFrame(() => {
      const raf2 = requestAnimationFrame(() => {
        img.style.transition = `transform ${SLIDE_DURATION_MS}ms ${SLIDE_EASING}, opacity ${SLIDE_DURATION_MS}ms ease`
        img.style.opacity = '1'
        img.style.transform = 'translateX(0)'
      })
      img.dataset.raf2 = String(raf2)
    })

    return () => {
      cancelAnimationFrame(raf1)
      if (img.dataset.raf2) cancelAnimationFrame(Number(img.dataset.raf2))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [index])

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

        {/* KMDB 원본 크기 그대로 보여줍니다(확대/크롭 없음) - 뷰포트보다 큰 경우에만 max-width/
            max-height로 줄어듭니다. transform/opacity는 위 useLayoutEffect가 직접 제어합니다. */}
        <img
          ref={imgRef}
          src={src}
          alt={`스틸컷 ${index + 1}`}
          className="block rounded-lg bg-black"
          style={{ maxWidth: '92vw', maxHeight: '70svh' }}
        />

        {/* 맨 처음/마지막 컷에서는 더 이동할 곳이 없으니(순환하지 않음) 그쪽 화살표를 아예 숨깁니다. */}
        {hasMultiple && !isFirst && (
          <button
            type="button"
            onClick={onPrev}
            aria-label="이전 스틸컷"
            className="absolute left-2 top-1/2 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-full bg-white/10 text-xl text-white hover:bg-white/20"
          >
            ‹
          </button>
        )}
        {hasMultiple && !isLast && (
          <button
            type="button"
            onClick={onNext}
            aria-label="다음 스틸컷"
            className="absolute right-2 top-1/2 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-full bg-white/10 text-xl text-white hover:bg-white/20"
          >
            ›
          </button>
        )}
      </div>
    </div>
  )
}

// KMDB stlls(스틸컷)는 "|"로 여러 장이 이어 붙어 오는데, 백엔드가 이미 배열(stillUrls)로 쪼개서 줍니다.
function StillGallery({ stillUrls }) {
  const [selectedIndex, setSelectedIndex] = useState(null)

  if (!stillUrls || stillUrls.length === 0) return null

  // 화살표가 맨 처음/마지막 컷에서는 보이지 않으니, 그 너머로는 넘어가지 않습니다(순환 없음).
  const showPrev = () => setSelectedIndex((prev) => Math.max(prev - 1, 0))
  const showNext = () => setSelectedIndex((prev) => Math.min(prev + 1, stillUrls.length - 1))

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
