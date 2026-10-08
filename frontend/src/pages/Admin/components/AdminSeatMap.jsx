// 사용자 예매 화면의 좌석 배치도(SeatMap)와 같은 레이아웃(왼쪽/가운데/오른쪽 3블록, 통로 간격)이지만,
// 관리자 화면은 다크 테마이고 선택/클릭이 필요 없는 읽기 전용 현황판이라 별도 컴포넌트로 둡니다.
const EDGE_BLOCK_SIZE = 3

function seatClassName(isReserved, isAccessible) {
  const accessibleRing = isAccessible ? 'ring-2 ring-sky-400 ring-offset-1 ring-offset-slate-900' : ''
  if (isReserved) return `bg-rose-500/80 ${accessibleRing}`
  return `bg-slate-600 ${accessibleRing}`
}

// seats: [{ id, rowLabel, colNo, seatType, status }] - 선택한 회차의 실제 좌석 배치도입니다.
function AdminSeatMap({ seats }) {
  const rows = [...new Set(seats.map((seat) => seat.rowLabel))].sort()
  const colCount = seats.reduce((max, seat) => Math.max(max, seat.colNo), 0)
  const seatsByRow = rows.reduce((acc, row) => {
    acc[row] = seats.filter((seat) => seat.rowLabel === row).sort((a, b) => a.colNo - b.colNo)
    return acc
  }, {})

  return (
    <div>
      <h3 className="mb-4 text-sm font-semibold text-gray-200">좌석 배치도</h3>

      <div className="mx-auto mb-6 w-full max-w-lg rounded-md bg-slate-700 py-2 text-center">
        <span className="text-sm font-bold tracking-[0.4em] text-white">SCREEN</span>
      </div>

      <div className="flex flex-col items-center gap-1.5">
        {rows.map((row) => (
          <div key={row} className="flex items-center gap-1.5">
            <span className="w-4 text-xs text-gray-500">{row}</span>
            {seatsByRow[row].map((seat) => {
              const isReserved = seat.status === 'BOOKED'
              const isAccessible = seat.seatType === 'WHEELCHAIR'
              const label = `${seat.rowLabel}${seat.colNo}`
              const isBlockStart =
                seat.colNo !== 1 && (seat.colNo === EDGE_BLOCK_SIZE + 1 || seat.colNo === colCount - EDGE_BLOCK_SIZE + 1)

              return (
                <span
                  key={seat.id}
                  aria-label={isAccessible ? `${label} (장애인석)` : label}
                  title={label}
                  // select-none: 읽기 전용 현황판이라 클릭해도 아무 동작이 없는데, 빈 inline 요소라
                  // 클릭할 때 브라우저가 텍스트 선택 커서(흰색 "|")를 보여주는 문제가 있었습니다.
                  className={`h-5 w-5 select-none rounded-sm ${seatClassName(isReserved, isAccessible)} ${isBlockStart ? 'ml-4' : ''}`}
                />
              )
            })}
          </div>
        ))}
      </div>

      <div className="mt-6 flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-xs text-gray-400">
        <span className="flex items-center gap-1.5">
          <span className="h-4 w-4 rounded-sm bg-slate-600" /> 선택 가능
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-4 w-4 rounded-sm bg-rose-500/80" /> 예매 완료
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-4 w-4 rounded-sm bg-slate-600 ring-2 ring-sky-400" /> 장애인석
        </span>
      </div>
    </div>
  )
}

export default AdminSeatMap
