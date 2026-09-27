import { useState } from 'react'
import PosterPlaceholder from '../../../components/common/PosterPlaceholder'

function CarouselNav({ total, index, onPrev, onNext }) {
  if (total <= 1) return null
  return (
    <>
      <button
        type="button"
        onClick={onPrev}
        aria-label="이전 포스터"
        className="absolute left-1 top-1/2 z-10 flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-full bg-black/50 text-sm text-white opacity-0 transition-opacity hover:bg-black/70 group-hover:opacity-100"
      >
        ‹
      </button>
      <button
        type="button"
        onClick={onNext}
        aria-label="다음 포스터"
        className="absolute right-1 top-1/2 z-10 flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-full bg-black/50 text-sm text-white opacity-0 transition-opacity hover:bg-black/70 group-hover:opacity-100"
      >
        ›
      </button>
      <div className="absolute bottom-2 left-1/2 z-10 flex -translate-x-1/2 gap-1">
        {Array.from({ length: total }, (_, i) => (
          <span key={i} className={`h-1.5 w-1.5 rounded-full ${i === index ? 'bg-white' : 'bg-white/40'}`} />
        ))}
      </div>
    </>
  )
}

// posterUrls가 여러 장이면(KMDB posters가 "|"로 여러 개 온 경우) 옆으로 넘겨보는 갤러리로,
// 한 장이면 그 한 장만, 아예 없으면 PosterPlaceholder를 보여줍니다.
function PosterLink({ posterUrls = [], video, title }) {
  const [index, setIndex] = useState(0)
  const total = posterUrls.length
  const currentUrl = total > 0 ? posterUrls[Math.min(index, total - 1)] : null

  const goPrev = (event) => {
    event.preventDefault()
    event.stopPropagation()
    setIndex((prev) => (prev - 1 + total) % total)
  }

  const goNext = (event) => {
    event.preventDefault()
    event.stopPropagation()
    setIndex((prev) => (prev + 1) % total)
  }

  const posterImage = currentUrl ? (
    <img src={currentUrl} alt={title} className="h-full w-full object-cover" />
  ) : (
    <PosterPlaceholder />
  )

  // 예고편(vodClass가 "예고편"류)이 실제로 있을 때만 호버 오버레이를 보여줍니다.
  const overlay = video ? (
    <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-black/60 text-sm font-semibold text-white opacity-0 transition-opacity group-hover:opacity-100">
      ▶ 예고편 보기
    </div>
  ) : null

  const nav = <CarouselNav total={total} index={index} onPrev={goPrev} onNext={goNext} />

  if (video) {
    return (
      <a
        href={video.url}
        target="_blank"
        rel="noopener noreferrer"
        className="group relative block aspect-[3/4] w-56 shrink-0 overflow-hidden rounded-xl bg-slate-800"
      >
        {posterImage}
        {overlay}
        {nav}
      </a>
    )
  }

  return (
    <div className="group relative aspect-[3/4] w-56 shrink-0 overflow-hidden rounded-xl bg-slate-800">
      {posterImage}
      {overlay}
      {nav}
    </div>
  )
}

export default PosterLink
