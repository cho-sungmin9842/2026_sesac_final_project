const WINDOW_SIZE = 5

function getPageNumbers(page, totalPages) {
  const half = Math.floor(WINDOW_SIZE / 2)
  let start = Math.max(1, page - half)
  const end = Math.min(totalPages, start + WINDOW_SIZE - 1)
  start = Math.max(1, end - WINDOW_SIZE + 1)
  return Array.from({ length: end - start + 1 }, (_, i) => start + i)
}

function PageButton({ children, onClick, disabled, active }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`min-w-9 rounded-md px-3 py-1.5 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-40 ${
        active ? 'bg-indigo-600 text-white' : 'bg-slate-800 text-gray-300 hover:bg-slate-700'
      }`}
    >
      {children}
    </button>
  )
}

function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null

  const pageNumbers = getPageNumbers(page, totalPages)

  return (
    <nav className="mt-8 flex items-center justify-center gap-2">
      <PageButton onClick={() => onChange(1)} disabled={page === 1}>
        «
      </PageButton>
      <PageButton onClick={() => onChange(page - 1)} disabled={page === 1}>
        ‹
      </PageButton>

      {pageNumbers.map((pageNumber) => (
        <PageButton key={pageNumber} onClick={() => onChange(pageNumber)} active={pageNumber === page}>
          {pageNumber}
        </PageButton>
      ))}

      <PageButton onClick={() => onChange(page + 1)} disabled={page === totalPages}>
        ›
      </PageButton>
      <PageButton onClick={() => onChange(totalPages)} disabled={page === totalPages}>
        »
      </PageButton>
    </nav>
  )
}

export default Pagination
