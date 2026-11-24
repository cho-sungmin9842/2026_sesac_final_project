import { useEffect, useRef, useState } from 'react'

export const EMPTY_REVIEW_FILTERS = {
  movieTitle: '',
  authorNickname: '',
  reportedOnly: false,
  dateFrom: '',
  dateTo: '',
  content: '',
}

function hasActiveFilters(filters) {
  return (
    filters.movieTitle !== '' ||
    filters.authorNickname !== '' ||
    filters.reportedOnly ||
    filters.dateFrom !== '' ||
    filters.dateTo !== '' ||
    filters.content !== ''
  )
}

const inputClassName =
  'rounded-md bg-slate-800 px-3 py-1.5 text-sm text-gray-200 placeholder:text-gray-500 focus:outline-none'

// 리뷰 내용 검색은 글자를 입력할 때마다 바로 서버에 보내지 않고, 타이핑이 잠깐 멈췄을 때만 반영합니다
// (한 글자 입력할 때마다 요청을 보내면 서버에도 부담이고 화면도 계속 깜빡입니다. 2026-11-24 요청).
const CONTENT_SEARCH_DEBOUNCE_MS = 400

// "리뷰 관리" 테이블의 검색/필터 바 - 실제 검색/정렬/페이지네이션은 서버에서 처리합니다
// (AdminReportsPage가 filters가 바뀔 때마다 새로 조회). movieTitleOptions/authorNicknameOptions:
// 직접 타이핑하는 대신, 실제로 리뷰가 존재하는 조합만 골라 쓸 수 있도록 AdminReportsPage가 넘겨줍니다
// (2026-11-20 요청). 두 드롭다운은 서로 연동됩니다 - 예를 들어 작성자를 먼저 고르면 영화 제목 쪽은
// 그 작성자가 실제로 쓴 영화만 남습니다(2026-11-21 요청).
function ReviewFilterBar({ filters, onChange, movieTitleOptions, authorNicknameOptions }) {
  const update = (patch) => onChange({ ...filters, ...patch })

  // 디바운스 타이머가 실제로 발동하는 시점에는 최신 filters를 기준으로 합쳐야 합니다 - update()
  // 클로저 그대로 쓰면, 글자를 입력한 뒤 타이머가 돌기 전에 다른 필터(영화 제목 등)를 바꿨을 때
  // 그 변경이 디바운스가 끝나는 순간 옛 값으로 덮어써질 수 있습니다.
  const filtersRef = useRef(filters)
  filtersRef.current = filters

  const [contentInput, setContentInput] = useState(filters.content)
  const debounceRef = useRef(null)

  useEffect(() => {
    setContentInput(filters.content)
  }, [filters.content])

  useEffect(() => {
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
    }
  }, [])

  const handleContentInputChange = (event) => {
    const value = event.target.value
    setContentInput(value)
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => {
      onChange({ ...filtersRef.current, content: value })
    }, CONTENT_SEARCH_DEBOUNCE_MS)
  }

  return (
    <div className="mb-4 flex flex-wrap items-end gap-3">
      <div>
        <label className="mb-1 block text-xs text-gray-500">리뷰 내용 검색</label>
        <input
          type="text"
          value={contentInput}
          onChange={handleContentInputChange}
          placeholder="내용에 포함된 단어"
          className={inputClassName}
        />
      </div>
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
          onClick={() => {
            setContentInput('')
            onChange(EMPTY_REVIEW_FILTERS)
          }}
          className="pb-1.5 text-xs text-gray-500 underline hover:text-gray-300"
        >
          필터 초기화
        </button>
      )}
    </div>
  )
}

export default ReviewFilterBar
