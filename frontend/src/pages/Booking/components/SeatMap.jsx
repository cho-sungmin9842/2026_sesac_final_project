import { SEAT_ROWS, SEATS_PER_ROW } from '../bookingData'

// 왼쪽/가운데/오른쪽 세 블록으로 나눠서 표시합니다(사진처럼 통로로 구분된 모양).
const BLOCK_RANGES = [
  [1, 3],
  [4, 10],
  [11, SEATS_PER_ROW],
]

function seatClassName(state) {
  if (state === 'reserved') return 'bg-gray-200 cursor-not-allowed'
  if (state === 'selected') return 'bg-orange-500'
  return 'bg-gray-300 hover:bg-orange-200'
}

function SeatMap({ reservedSeats, selectedSeats, onToggleSeat }) {
  return (
    <div>
      <div className="mx-auto mb-8 w-full max-w-lg rounded-md bg-gray-700 py-2 text-center">
        <span className="text-sm font-bold tracking-[0.4em] text-white">SCREEN</span>
      </div>

      <div className="flex flex-col items-center gap-1.5">
        {SEAT_ROWS.map((row) => (
          <div key={row} className="flex items-center gap-1.5">
            <span className="w-4 text-xs text-gray-400">{row}</span>
            {Array.from({ length: SEATS_PER_ROW }, (_, i) => i + 1).map((col) => {
              const seatId = `${row}${col}`
              const isReserved = reservedSeats.has(seatId)
              const isSelected = selectedSeats.includes(seatId)
              const state = isReserved ? 'reserved' : isSelected ? 'selected' : 'available'
              // 블록(왼쪽/가운데/오른쪽)이 바뀌는 경계에 통로 간격을 둡니다.
              const isBlockStart = BLOCK_RANGES.some(([start]) => start === col) && col !== 1

              return (
                <button
                  key={seatId}
                  type="button"
                  disabled={isReserved}
                  onClick={() => onToggleSeat(seatId)}
                  aria-label={seatId}
                  className={`h-5 w-5 rounded-sm text-[10px] ${seatClassName(state)} ${isBlockStart ? 'ml-4' : ''}`}
                />
              )
            })}
          </div>
        ))}
      </div>

      <div className="mt-6 flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-xs text-gray-500">
        <span className="flex items-center gap-1.5">
          <span className="h-4 w-4 rounded-sm bg-gray-300" /> 선택 가능
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-4 w-4 rounded-sm bg-orange-500" /> 선택됨
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-4 w-4 rounded-sm bg-gray-200" /> 예약 완료
        </span>
      </div>
    </div>
  )
}

export default SeatMap
