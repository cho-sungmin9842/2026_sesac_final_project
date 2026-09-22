import { SEAT_ROWS, SEATS_PER_ROW } from '../bookingData'

function seatClassName(state) {
  if (state === 'reserved') return 'bg-gray-400 cursor-not-allowed'
  if (state === 'selected') return 'bg-indigo-600'
  return 'bg-gray-100 hover:bg-indigo-100 border border-gray-300'
}

function SeatMap({ reservedSeats, selectedSeats, onToggleSeat }) {
  return (
    <div>
      <div className="mx-auto mb-8 h-1 w-full max-w-xl rounded-full bg-gradient-to-r from-transparent via-gray-300 to-transparent" />
      <p className="mb-6 text-center text-xs tracking-[0.3em] text-gray-400">SCREEN</p>

      <div className="flex flex-col items-center gap-2">
        {SEAT_ROWS.map((row) => (
          <div key={row} className="flex items-center gap-2">
            <span className="w-4 text-xs text-gray-400">{row}</span>
            {Array.from({ length: SEATS_PER_ROW }, (_, i) => i + 1).map((col) => {
              const seatId = `${row}${col}`
              const isReserved = reservedSeats.has(seatId)
              const isSelected = selectedSeats.includes(seatId)
              const state = isReserved ? 'reserved' : isSelected ? 'selected' : 'available'

              return (
                <button
                  key={seatId}
                  type="button"
                  disabled={isReserved}
                  onClick={() => onToggleSeat(seatId)}
                  aria-label={seatId}
                  className={`h-6 w-6 rounded-md text-[10px] ${seatClassName(state)}`}
                />
              )
            })}
          </div>
        ))}
      </div>

      <div className="mt-6 flex items-center justify-center gap-6 text-xs text-gray-500">
        <span className="flex items-center gap-1.5">
          <span className="h-4 w-4 rounded-md border border-gray-300 bg-gray-100" /> 선택 가능
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-4 w-4 rounded-md bg-indigo-600" /> 선택됨
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-4 w-4 rounded-md bg-gray-400" /> 예약 완료
        </span>
      </div>
    </div>
  )
}

export default SeatMap
