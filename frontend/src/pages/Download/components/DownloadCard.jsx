import PosterPlaceholder from '../../../components/common/PosterPlaceholder'

function formatSize(sizeMb) {
  return sizeMb >= 1024 ? `${(sizeMb / 1024).toFixed(1)}GB` : `${sizeMb}MB`
}

function DownloadCard({ movie, sizeMb, progress, isDownloaded, onDownload, onRemove }) {
  const isDownloading = progress != null

  return (
    <div className="flex items-center gap-4 rounded-lg bg-slate-900 p-4">
      <div className="h-20 w-14 shrink-0 overflow-hidden rounded-md">
        {movie.posterUrl ? (
          <img src={movie.posterUrl} alt={movie.title} className="h-full w-full object-cover" />
        ) : (
          <PosterPlaceholder compact />
        )}
      </div>

      <div className="min-w-0 flex-1">
        <p className="truncate font-semibold text-gray-100">{movie.title}</p>
        <p className="text-xs text-gray-400">
          {movie.year} · {formatSize(sizeMb)}
        </p>

        {isDownloading && (
          <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-slate-700">
            <div
              className="h-full rounded-full bg-indigo-500 transition-all"
              style={{ width: `${progress}%` }}
            />
          </div>
        )}
      </div>

      {isDownloading ? (
        <span className="shrink-0 text-sm text-indigo-300">{progress}%</span>
      ) : isDownloaded ? (
        <div className="flex shrink-0 items-center gap-3">
          <span className="text-sm text-emerald-400">✓ 다운로드 완료</span>
          <button type="button" onClick={onRemove} className="text-xs text-gray-400 hover:text-red-400">
            삭제
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={onDownload}
          className="shrink-0 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
        >
          ⬇ 다운로드
        </button>
      )}
    </div>
  )
}

export default DownloadCard
