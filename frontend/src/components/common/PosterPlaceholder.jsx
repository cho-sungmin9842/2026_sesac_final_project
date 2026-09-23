// 포스터도 스틸컷도 없는 극소수 영화용 공통 대체 화면(KMDB 외 다른 이미지 소스는 쓰지 않기로 한 방침 유지).
function PosterPlaceholder({ compact = false, className = 'bg-gradient-to-br from-slate-700 to-slate-900' }) {
  return (
    <div className={`flex h-full w-full flex-col items-center justify-center gap-1 text-gray-400 ${className}`}>
      <span className={compact ? 'text-base' : 'text-3xl'}>🎬</span>
      {!compact && <span className="text-[11px]">포스터 없음</span>}
    </div>
  )
}

export default PosterPlaceholder
