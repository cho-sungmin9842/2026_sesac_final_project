import { genreLabel, genres, runtimeFilters, years } from './filterOptions'

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
  return (
    <aside className="w-56 shrink-0 space-y-6">
      <FilterGroup
        title="장르"
        options={genres}
        isActive={(option) => (option === '전체' ? filters.genres.length === 0 : filters.genres.includes(option))}
        onSelect={onToggleGenre}
        getLabel={genreLabel}
      />
      <YearSelect options={years} active={filters.year} onSelect={(value) => onChange('year', value)} />
      <div>
        <FilterGroup
          title="러닝타임"
          options={runtimeFilters}
          isActive={(option) => filters.runtime === option}
          onSelect={(value) => onChange('runtime', value)}
        />
        {filters.runtime !== '전체' && (
          <p className="mt-2 text-xs text-gray-500">현재 페이지에 불러온 결과에만 적용됩니다.</p>
        )}
      </div>
    </aside>
  )
}

export default FilterSidebar
