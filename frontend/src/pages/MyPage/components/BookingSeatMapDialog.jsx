import { useEffect, useState } from 'react'
import { getScreeningSeats } from '../../../api/screeningApi'
import { changeBookingSeat } from '../../../api/bookingApi'

const EDGE_BLOCK_SIZE = 3

// Admin/예매 화면의 좌석 배치도(AdminSeatMap.jsx)와 같은 레이아웃이지만, "예약 완료" 한 가지 대신 내
// 좌석/다른 예매자 좌석/좌석변경 가능을 색으로 구분합니다. selectedSeatId가 내 좌석 중 하나면, 좌석변경
// 가능한 빈 좌석을 눌러 바로 그 좌석으로 바꿀 수 있습니다.
function seatClassName(seat, isSelected) {
  const accessibleRing = seat.seatType === 'WHEELCHAIR' ? 'ring-2 ring-sky-400 ring-offset-1 ring-offset-slate-900' : ''
  const selectedRing = isSelected ? 'ring-2 ring-white ring-offset-1 ring-offset-slate-900' : ''

  if (seat.status !== 'BOOKED') {
    return `bg-slate-600 hover:bg-slate-500 cursor-pointer ${accessibleRing}`
  }
  if (seat.bookedByMe) {
    return `bg-indigo-500 cursor-pointer ${selectedRing || accessibleRing}`
  }
  return `bg-rose-500/80 cursor-not-allowed ${accessibleRing}`
}

// 마이페이지 "예매 내역"에서 좌석 번호를 클릭하면 뜨는 다이얼로그 - 그 상영의 전체 좌석 배치도를 보여주고,
// 내 좌석을 눌러 선택한 뒤 비어있는 좌석을 누르면 그 자리로 좌석을 바꿀 수 있습니다.
function BookingSeatMapDialog({ booking, userId, onClose, onSeatChanged }) {
  const [seats, setSeats] = useState([])
  const [status, setStatus] = useState('loading') // loading | ready | error
  const [selectedSeatId, setSelectedSeatId] = useState(null)
  const [isChanging, setIsChanging] = useState(false)

  // 다이얼로그가 열려있는 동안 좌석변경에 성공한 뒤 최신 상태로 다시 불러올 때도 재사용합니다.
  const loadSeats = () => {
    setStatus('loading')
    return getScreeningSeats(booking.screeningId, userId)
      .then((result) => {
        setSeats(result)
        setStatus('ready')
      })
      .catch(() => {
        setStatus('error')
      })
  }

  useEffect(() => {
    loadSeats()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [booking.screeningId, userId])

  const handleSeatClick = async (seat) => {
    if (isChanging) return

    // 다른 예매자의 좌석은 선택할 수 없습니다.
    if (seat.status === 'BOOKED' && !seat.bookedByMe) return

    // 내 좌석을 눌렀을 때 - 이미 선택돼 있으면 선택을 취소하고, 아니면 "바꿀 좌석"으로 선택합니다.
    if (seat.bookedByMe) {
      setSelectedSeatId((prev) => (prev === seat.id ? null : seat.id))
      return
    }

    // 빈 좌석을 눌렀을 때 - 먼저 내 좌석을 선택해둔 상태여야 바로 그 자리로 바꿉니다.
    if (selectedSeatId == null) return

    // 예매 화면의 좌석 선택(BookingSeatPage.jsx)과 동일하게, 장애인석(A열)으로 옮기는 경우에는
    // 실수로 누른 게 아닌지 한 번 더 확인합니다.
    if (seat.seatType === 'WHEELCHAIR') {
      const confirmed = window.confirm('장애인석을 선택했습니다.\n장애인석을 선택하시겠습니까?')
      if (!confirmed) return
    }

    setIsChanging(true)
    try {
      await changeBookingSeat(userId, booking.id, { fromSeatId: selectedSeatId, toSeatId: seat.id })
      setSelectedSeatId(null)
      await loadSeats()
      onSeatChanged?.()
    } catch (error) {
      window.alert(error.message)
    } finally {
      setIsChanging(false)
    }
  }

  const rows = [...new Set(seats.map((seat) => seat.rowLabel))].sort()
  const colCount = seats.reduce((max, seat) => Math.max(max, seat.colNo), 0)
  const seatsByRow = rows.reduce((acc, row) => {
    acc[row] = seats.filter((seat) => seat.rowLabel === row).sort((a, b) => a.colNo - b.colNo)
    return acc
  }, {})

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
      <div className="w-full max-w-lg rounded-xl bg-slate-900 p-6">
        <div className="mb-4 flex items-start justify-between gap-4">
          <div className="min-w-0">
            <h3 className="truncate text-sm font-bold text-white">{booking.movieTitle}</h3>
            <p className="mt-1 text-xs text-gray-400">
              {booking.theaterName} · {booking.showDate} {booking.showtime}
            </p>
          </div>
          <button type="button" onClick={onClose} className="shrink-0 text-gray-400 hover:text-white">
            ✕
          </button>
        </div>

        {status === 'loading' && <p className="py-10 text-center text-sm text-gray-500">좌석 현황을 불러오는 중...</p>}
        {status === 'error' && <p className="py-10 text-center text-sm text-red-400">좌석 현황을 불러오지 못했습니다.</p>}

        {status === 'ready' && (
          <>
            <p className="mb-4 text-center text-xs text-gray-400">
              {selectedSeatId == null
                ? '바꾸고 싶은 내 좌석을 눌러주세요.'
                : '옮겨갈 빈 좌석을 눌러주세요. (같은 좌석을 다시 누르면 선택이 취소됩니다)'}
            </p>
            <div className="mx-auto mb-6 w-full max-w-md rounded-md bg-slate-700 py-2 text-center">
              <span className="text-sm font-bold tracking-[0.4em] text-white">SCREEN</span>
            </div>
            <div className={`flex flex-col items-center gap-1.5 ${isChanging ? 'pointer-events-none opacity-60' : ''}`}>
              {rows.map((row) => (
                <div key={row} className="flex items-center gap-1.5">
                  <span className="w-4 text-xs text-gray-500">{row}</span>
                  {seatsByRow[row].map((seat) => {
                    const label = `${seat.rowLabel}${seat.colNo}`
                    const isBlockStart =
                      seat.colNo !== 1 && (seat.colNo === EDGE_BLOCK_SIZE + 1 || seat.colNo === colCount - EDGE_BLOCK_SIZE + 1)
                    return (
                      <button
                        key={seat.id}
                        type="button"
                        aria-label={seat.seatType === 'WHEELCHAIR' ? `${label} (장애인석)` : label}
                        title={label}
                        onClick={() => handleSeatClick(seat)}
                        // select-none/focus:outline-none이 없으면, 좌석을 누른 직후 그 버튼이 브라우저
                        // 기본 포커스 표시(또는 텍스트 커서)를 그대로 보여줘 좌석 가운데에 "|"처럼 보이는
                        // 줄이 생겼습니다 - 버튼 안에 실제 글자가 없는데도 발생하는 브라우저 기본 스타일이라,
                        // 명시적으로 꺼둡니다.
                        className={`h-5 w-5 select-none rounded-sm focus:outline-none ${seatClassName(seat, seat.id === selectedSeatId)} ${isBlockStart ? 'ml-4' : ''}`}
                      />
                    )
                  })}
                </div>
              ))}
            </div>
            <div className="mt-6 flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-xs text-gray-400">
              <span className="flex items-center gap-1.5">
                <span className="h-4 w-4 rounded-sm bg-indigo-500" /> 내 좌석
              </span>
              <span className="flex items-center gap-1.5">
                <span className="h-4 w-4 rounded-sm bg-rose-500/80" /> 다른 예매자 좌석
              </span>
              <span className="flex items-center gap-1.5">
                <span className="h-4 w-4 rounded-sm bg-slate-600" /> 좌석변경 가능
              </span>
              <span className="flex items-center gap-1.5">
                <span className="h-4 w-4 rounded-sm bg-slate-600 ring-2 ring-sky-400" /> 장애인석
              </span>
            </div>
          </>
        )}
      </div>
    </div>
  )
}

export default BookingSeatMapDialog
