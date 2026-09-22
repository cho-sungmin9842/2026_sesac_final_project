const VIDEO_LABEL = {
  trailer: '▶ 예고편 보기',
  making: '🎬 메이킹 필름 보기',
}

function PosterImage({ posterUrl, title }) {
  return posterUrl ? (
    <img src={posterUrl} alt={title} className="h-full w-full object-cover" />
  ) : (
    <div className="h-full w-full bg-gradient-to-br from-slate-700 to-slate-900" />
  )
}

function PosterLink({ posterUrl, video, title }) {
  if (video) {
    return (
      <a
        href={video.url}
        target="_blank"
        rel="noopener noreferrer"
        className="group relative block aspect-[3/4] w-56 shrink-0 overflow-hidden rounded-xl bg-slate-800"
      >
        <PosterImage posterUrl={posterUrl} title={title} />
        <div className="absolute inset-0 flex items-center justify-center bg-black/60 text-sm font-semibold text-white opacity-0 transition-opacity group-hover:opacity-100">
          {VIDEO_LABEL[video.kind]}
        </div>
      </a>
    )
  }

  return (
    <div className="group relative aspect-[3/4] w-56 shrink-0 overflow-hidden rounded-xl bg-slate-800">
      <PosterImage posterUrl={posterUrl} title={title} />
      <div className="absolute inset-0 flex items-center justify-center bg-black/60 text-sm font-semibold text-white opacity-0 transition-opacity group-hover:opacity-100">
        예고편 없음
      </div>
    </div>
  )
}

export default PosterLink
