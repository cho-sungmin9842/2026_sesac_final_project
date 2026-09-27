import { useEffect, useRef, useState } from 'react'

// 네이티브 <select>의 옵션 목록은 브라우저/OS가 직접 그려서 마우스오버 배경색을 페이지 CSS로 제어할 수
// 없어서, 같은 자리에 직접 그린 드롭다운으로 바꿔 옵션에 호버 배경 효과를 줍니다.
function SortDropdown({ value, options, onChange }) {
  const [isOpen, setOpen] = useState(false)
  const containerRef = useRef(null)
  const selected = options.find((option) => option.value === value) ?? options[0]

  useEffect(() => {
    if (!isOpen) return undefined

    const handleClickOutside = (event) => {
      if (!containerRef.current?.contains(event.target)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [isOpen])

  const handleSelect = (optionValue) => {
    onChange(optionValue)
    setOpen(false)
  }

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        className="flex items-center gap-1.5 rounded-md bg-slate-800 px-3 py-1.5 text-sm text-gray-300 hover:bg-slate-700"
      >
        {selected.label}
        <span className={`text-xs transition-transform ${isOpen ? 'rotate-180' : ''}`}>▾</span>
      </button>

      {isOpen && (
        <ul className="absolute right-0 z-10 mt-1 w-28 overflow-hidden rounded-md bg-slate-800 py-1 shadow-lg ring-1 ring-white/10">
          {options.map((option) => (
            <li key={option.value}>
              <button
                type="button"
                onClick={() => handleSelect(option.value)}
                className={`block w-full px-3 py-1.5 text-left text-sm hover:bg-slate-700 ${
                  option.value === value ? 'text-indigo-300' : 'text-gray-300'
                }`}
              >
                {option.label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default SortDropdown
