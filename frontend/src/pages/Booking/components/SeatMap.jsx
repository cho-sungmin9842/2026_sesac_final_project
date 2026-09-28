import { useState } from 'react'
import { ACCESSIBLE_SEATS, SEAT_ROWS, SEATS_PER_ROW } from '../bookingData'

// 왼쪽/가운데/오른쪽 세 블록으로 나눠서 표시합니다(사진처럼 통로로 구분된 모양).
const BLOCK_RANGES = [
  [1, 3],
  [4, 10],
  [11, SEATS_PER_ROW],
]

function seatClassName(state, isAccessible) {
  const accessibleRing = isAccessible ? 'ring-2 ring-blue-400 ring-offset-1 ring-offset-white' : ''
  if (state === 'reserved') return `bg-gray-200 cursor-not-allowed ${accessibleRing}`
  if (state === 'selected') return `bg-orange-500 ${accessibleRing}`
  return `bg-gray-300 hover:bg-orange-200 ${accessibleRing}`
}

function SeatMap({ reservedSeats, selectedSeats, onToggleSeat }) {
  const [showAccessibleInfo, setShowAccessibleInfo] = useState(false)

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-gray-700">좌석 배치도</h2>
        <button
          type="button"
          onClick={() => setShowAccessibleInfo((prev) => !prev)}
          className="rounded-md bg-gray-100 px-3 py-1.5 text-xs font-semibold text-gray-600 hover:bg-gray-200"
        >
          일반석 / 장애인석 구분
        </button>
      </div>

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
              const isAccessible = ACCESSIBLE_SEATS.has(seatId)
              const state = isReserved ? 'reserved' : isSelected ? 'selected' : 'available'
              // 블록(왼쪽/가운데/오른쪽)이 바뀌는 경계에 통로 간격을 둡니다.
              const isBlockStart = BLOCK_RANGES.some(([start]) => start === col) && col !== 1

              return (
                <button
                  key={seatId}
                  type="button"
                  disabled={isReserved}
                  onClick={() => onToggleSeat(seatId)}
                  aria-label={isAccessible ? `${seatId} (장애인석)` : seatId}
                  className={`h-5 w-5 rounded-sm text-[10px] ${seatClassName(state, isAccessible)} ${isBlockStart ? 'ml-4' : ''}`}
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
        <span className="flex items-center gap-1.5">
          <span className="h-4 w-4 rounded-sm bg-gray-300 ring-2 ring-blue-400" /> 장애인석
        </span>
      </div>

      {showAccessibleInfo && (
        <p className="mx-auto mt-4 max-w-2xl rounded-md bg-blue-50 px-4 py-3 text-center text-xs leading-relaxed text-blue-700">
          배치 안내: 장애인(휠체어)석은 스크린과 가깝고 통로 진입이 쉬운 자리에 우선 배치합니다. 다른 관람객
          좌석을 지나치지 않고 편하게 이용하실 수 있으며, 일반 좌석과 동일하게 예매하실 수 있습니다.
        </p>
      )}
    </div>
  )
}

export default SeatMap
