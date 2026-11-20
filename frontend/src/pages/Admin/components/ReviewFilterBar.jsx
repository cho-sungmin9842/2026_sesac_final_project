export const EMPTY_REVIEW_FILTERS = {
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

// "리뷰 관리" 테이블의 검색/필터 바 - 전부 클라이언트 쪽에서 이미 불러온 reviews 배열을 걸러내는
// 용도입니다(AdminReportsPage.filteredReviews 참고). 리뷰 수가 지금처럼 적을 때는 이 정도로 충분하고,
// 나중에 리뷰가 많이 쌓이면 서버 쪽 페이지네이션/필터로 옮겨야 합니다.
// movieTitleOptions/authorNicknameOptions: 직접 타이핑하는 대신, 지금 불러온 리뷰들에 실제로 등장하는
// 값만 골라 쓸 수 있도록 AdminReportsPage가 중복 제거해서 넘겨줍니다(2026-11-20 요청).
function ReviewFilterBar({ filters, onChange, movieTitleOptions, authorNicknameOptions }) {
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
        <label className="mb-1 block text-xs text-gray-500">작성일(부터)</label>
        <input
          type="date"
          value={filters.dateFrom}
          onChange={(event) => update({ dateFrom: event.target.value })}
          className={inputClassName}
        />
      </div>
      <div>
        <label className="mb-1 block text-xs text-gray-500">작성일(까지)</label>
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
        신고된 것만
      </label>
      {hasActiveFilters(filters) && (
        <button
          type="button"
          onClick={() => onChange(EMPTY_REVIEW_FILTERS)}
          className="pb-1.5 text-xs text-gray-500 underline hover:text-gray-300"
        >
          필터 초기화
        </button>
      )}
    </div>
  )
}

export default ReviewFilterBar
