// KMDB vods 중 예고편이 아닌 영상들(메이킹, 인터뷰, TV스팟 등)을 각각 버튼으로 보여줍니다.
// 클릭하면 해당 vodUrl을 새 탭에서 엽니다.
function OtherVideoButtons({ videos }) {
  if (!videos || videos.length === 0) return null

  return (
    <div className="mt-3 flex flex-wrap gap-2">
      {videos.map((clip, index) => (
        <a
          key={`${clip.url}-${index}`}
          href={clip.url}
          target="_blank"
          rel="noopener noreferrer"
          className="rounded-lg border border-gray-700 px-3 py-1.5 text-xs font-semibold text-gray-300 hover:bg-slate-800"
        >
          ▶ {clip.label}
        </a>
      ))}
    </div>
  )
}

export default OtherVideoButtons
