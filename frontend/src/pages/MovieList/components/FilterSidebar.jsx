import { useEffect, useState } from 'react'
import { getGenres } from '../../../api/movieApi'
import { genreLabel, runtimeFilters, years } from './filterOptions'

function FilterGroup({ title, options, isActive, onSelect, getLabel = (option) => option }) {
  return (
    <div>
      <h4 className="mb-2 text-sm font-semibold text-gray-300">{title}</h4>
      <div className="flex flex-wrap gap-2">
        {options.map((option) => (
          <button
            key={option}
            type="button"
            onClick={() => onSelect(option)}
            className={`rounded-md px-3 py-1.5 text-sm ${
              isActive(option)
                ? 'bg-indigo-600 text-white'
                : 'bg-slate-800 text-gray-300 hover:bg-slate-700'
            }`}
          >
            {getLabel(option)}
          </button>
        ))}
      </div>
    </div>
  )
}

function YearSelect({ options, active, onSelect }) {
  return (
    <div>
      <h4 className="mb-2 text-sm font-semibold text-gray-300">개봉연도</h4>
      <select
        value={active}
        onChange={(event) => onSelect(event.target.value)}
        className="w-full rounded-md bg-slate-800 px-3 py-2 text-sm text-gray-300"
      >
        {options.map((year) => (
          <option key={year} value={year}>
            {year}
          </option>
        ))}
      </select>
    </div>
  )
}

function FilterSidebar({ filters, onChange, onToggleGenre }) {
  // 장르 목록은 백엔드(MovieGenres.ALL)에서 받아옵니다 - "전체"는 화면 전용 선택지라 받아온 뒤 앞에 붙입니다.
  const [genres, setGenres] = useState(['전체'])
  // 모바일(md 미만)에서는 필터 패널이 기본적으로 접혀 있고, "필터" 버튼으로 펼칩니다 - 고정폭 사이드바가
  // 화면 1/3 이상을 차지해 영화 목록이 비좁아지는 문제를 막습니다. md 이상(데스크톱)에서는 이 상태와
  // 무관하게 항상 펼쳐진 사이드바로 보입니다.
  const [mobileOpen, setMobileOpen] = useState(false)

  useEffect(() => {
    let cancelled = false
    getGenres().then((list) => {
      if (!cancelled) setGenres(['전체', ...list])
    })
    return () => {
      cancelled = true
    }
  }, [])

  const activeFilterCount =
    (filters.genres.length > 0 ? 1 : 0) + (filters.year !== '전체' ? 1 : 0) + (filters.runtime !== '전체' ? 1 : 0)

  return (
    <aside className="w-full shrink-0 md:w-56">
      <button
        type="button"
        onClick={() => setMobileOpen((open) => !open)}
        className="mb-4 flex w-full items-center justify-between rounded-lg bg-slate-800 px-4 py-2.5 text-sm font-semibold text-gray-200 md:hidden"
      >
        <span>필터{activeFilterCount > 0 ? ` (${activeFilterCount})` : ''}</span>
        <span className="text-xs text-gray-400">{mobileOpen ? '접기 ▲' : '펼치기 ▼'}</span>
      </button>

      <div className={`space-y-6 ${mobileOpen ? 'block' : 'hidden'} md:block`}>
        <FilterGroup
          title="장르"
          options={genres}
          isActive={(option) => (option === '전체' ? filters.genres.length === 0 : filters.genres.includes(option))}
          onSelect={onToggleGenre}
          getLabel={genreLabel}
        />
        <YearSelect options={years} active={filters.year} onSelect={(value) => onChange('year', value)} />
        <FilterGroup
          title="러닝타임"
          options={runtimeFilters}
          isActive={(option) => filters.runtime === option}
          onSelect={(value) => onChange('runtime', value)}
        />
      </div>
    </aside>
  )
}

export default FilterSidebar
