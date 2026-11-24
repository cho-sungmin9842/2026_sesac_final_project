export const EMPTY_LOG_FILTERS = {
  movieTitle: '',
  authorNickname: '',
  reportedOnly: false,
  dateFrom: '',
  dateTo: '',
}

function hasActiveFilters(filters) {
  return (
    filters.movieTitle !== '' ||
    filters.authorNickname !== '' ||
    filters.reportedOnly ||
    filters.dateFrom !== '' ||
    filters.dateTo !== ''
  )
}

const inputClassName =
  'rounded-md bg-slate-800 px-3 py-1.5 text-sm text-gray-200 placeholder:text-gray-500 focus:outline-none'

// "삭제 이력" 탭의 검색/필터 바 - 리뷰 목록 탭(ReviewFilterBar)과 같은 모양이지만, 날짜 기준이
// "작성일"이 아니라 "삭제일"이고(삭제 이력은 리뷰 작성일을 따로 남기지 않음), "신고된 것만" 대신
// "신고로 삭제된 것만"을 씁니다 - 삭제 이력은 전부 이미 삭제된 리뷰라 report_reasons가 있는지 없는지로
// "신고돼서 지워졌는지"와 "관리자가 직접 지웠는지"를 구분합니다(ReviewDeletionLog 참고). 이미 전부
// 불러온 deletionLogs 배열을 클라이언트에서 거르는 용도라 movieTitleOptions/authorNicknameOptions도
// AdminReportsPage가 그 배열에서 뽑아 넘겨줍니다(2026-11-21 요청).
function DeletionLogFilterBar({ filters, onChange, movieTitleOptions, authorNicknameOptions }) {
  const update = (patch) => onChange({ ...filters, ...patch })

  return (
    <div className="mb-4 flex flex-wrap items-end gap-3">
      <div>
        <label className="mb-1 block text-xs text-gray-500">영화 제목</label>
        <select
          value={filters.movieTitle}
          onChange={(event) => update({ movieTitle: event.target.value })}
          className={inputClassName}
        >
          <option value="">전체</option>
          {movieTitleOptions.map((title) => (
            <option key={title} value={title}>
              {title}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="mb-1 block text-xs text-gray-500">작성자 닉네임</label>
        <select
          value={filters.authorNickname}
          onChange={(event) => update({ authorNickname: event.target.value })}
          className={inputClassName}
        >
          <option value="">전체</option>
          {authorNicknameOptions.map((nickname) => (
            <option key={nickname} value={nickname}>
              {nickname}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="mb-1 block text-xs text-gray-500">삭제일(부터)</label>
        <input
          type="date"
          value={filters.dateFrom}
          onChange={(event) => update({ dateFrom: event.target.value })}
          className={inputClassName}
        />
      </div>
      <div>
        <label className="mb-1 block text-xs text-gray-500">삭제일(까지)</label>
        <input
          type="date"
          value={filters.dateTo}
          onChange={(event) => update({ dateTo: event.target.value })}
          className={inputClassName}
        />
      </div>
      <label className="flex items-center gap-1.5 pb-1.5 text-sm text-gray-300">
        <input
          type="checkbox"
          checked={filters.reportedOnly}
          onChange={(event) => update({ reportedOnly: event.target.checked })}
        />
        신고로 삭제된 것만
      </label>
      {hasActiveFilters(filters) && (
        <button
          type="button"
          onClick={() => onChange(EMPTY_LOG_FILTERS)}
          className="pb-1.5 text-xs text-gray-500 underline hover:text-gray-300"
        >
          필터 초기화
        </button>
      )}
    </div>
  )
}

export default DeletionLogFilterBar
