import { useState } from 'react'
import HorizontalScroll from '../../../components/common/HorizontalScroll'
import ConfirmDialog from '../../../components/common/ConfirmDialog'

// 사용자 예매 화면의 좌석 배치도(SeatMap)와 같은 레이아웃(왼쪽/가운데/오른쪽 3블록, 통로 간격)이지만,
// 관리자 화면은 다크 테마이고 선택이 필요 없는 읽기 전용 현황판입니다(예매 완료 좌석만 예외로, 클릭하면
// 예매자 닉네임을 보여주고 그 자리에서 바로 취소할 수 있습니다).
const EDGE_BLOCK_SIZE = 3

function seatClassName(isReserved, isAccessible) {
  const accessibleRing = isAccessible ? 'ring-2 ring-sky-400 ring-offset-1 ring-offset-slate-900' : ''
  if (isReserved) return `bg-rose-500/80 ${accessibleRing}`
  return `bg-slate-600 ${accessibleRing}`
}

// seats: [{ id, rowLabel, colNo, seatType, status, bookerNickname, bookerUsername, bookingId }] -
// 선택한 회차의 실제 좌석 배치도입니다. bookerNickname/bookerUsername/bookingId는 status가 BOOKED인
// 좌석에만 들어있습니다(관리자 전용 조회라 가능). onCancelBooking(bookingId)는 부모가 실제 취소 API를
// 호출하고 좌석 현황을 다시 불러오는 Promise를 돌려줘야 합니다.
function AdminSeatMap({ seats, onCancelBooking }) {
  const [activeSeatId, setActiveSeatId] = useState(null)
  const [pendingCancelSeat, setPendingCancelSeat] = useState(null)
  const rows = [...new Set(seats.map((seat) => seat.rowLabel))].sort()
  const colCount = seats.reduce((max, seat) => Math.max(max, seat.colNo), 0)
  const seatsByRow = rows.reduce((acc, row) => {
    acc[row] = seats.filter((seat) => seat.rowLabel === row).sort((a, b) => a.colNo - b.colNo)
    return acc
  }, {})
  const activeSeat = seats.find((seat) => seat.id === activeSeatId) ?? null

  return (
    <div>
      <h3 className="mb-4 text-sm font-semibold text-gray-200">좌석 배치도</h3>

      <div className="mx-auto mb-6 w-full max-w-lg rounded-md bg-slate-700 py-2 text-center">
        <span className="text-sm font-bold tracking-[0.4em] text-white">SCREEN</span>
      </div>

      {/* 열 수(14)×좌석 크기+통로 간격을 합치면 모바일 화면 폭보다 넓어질 수 있어, 이 영역만 가로
          스크롤되게 감싸둡니다 - 안 그러면 이 좌석판 때문에 페이지 전체가 옆으로 밀립니다. */}
      <HorizontalScroll>
        <div className="flex w-fit flex-col items-center gap-1.5">
          <div className="flex items-center gap-1.5">
            <span className="w-4" />
            {Array.from({ length: colCount }, (_, i) => i + 1).map((colNo) => {
              const isBlockStart = colNo !== 1 && (colNo === EDGE_BLOCK_SIZE + 1 || colNo === colCount - EDGE_BLOCK_SIZE + 1)
              return (
                <span
                  key={colNo}
                  className={`flex h-5 w-5 items-center justify-center text-[10px] text-gray-500 ${isBlockStart ? 'ml-4' : ''}`}
                >
                  {colNo}
                </span>
              )
            })}
          </div>
          {rows.map((row) => (
            <div key={row} className="flex items-center gap-1.5">
              <span className="w-4 text-xs text-gray-500">{row}</span>
              {seatsByRow[row].map((seat) => {
                const isReserved = seat.status === 'BOOKED'
                const isAccessible = seat.seatType === 'WHEELCHAIR'
                const label = `${seat.rowLabel}${seat.colNo}`
                const isBlockStart =
                  seat.colNo !== 1 && (seat.colNo === EDGE_BLOCK_SIZE + 1 || seat.colNo === colCount - EDGE_BLOCK_SIZE + 1)
                const isActive = activeSeatId === seat.id

                return (
                  <button
                    key={seat.id}
                    type="button"
                    aria-label={isAccessible ? `${label} (장애인석)` : label}
                    title={
                      isReserved
                        ? `${label} · ${seat.bookerNickname ?? '알 수 없음'}${seat.bookerUsername ? ` (${seat.bookerUsername})` : ''}`
                        : label
                    }
                    // 예매 완료가 아닌 좌석은 보여주기만 하는 현황판이라 클릭해도 아무 일도 없고,
                    // select-none은 클릭할 때 브라우저가 텍스트 선택 커서를 보여주는 걸 막기 위함입니다.
                    // 가로 스크롤 영역(HorizontalScroll) 안이라 세로로 넘치는 말풍선은 잘려서 안 보이는
                    // 문제가 있어(overflow-x-auto는 overflow-y도 같이 auto가 됨), 클릭한 좌석 정보는
                    // 아래쪽에 별도 텍스트로 보여줍니다(ring으로 지금 클릭한 좌석만 표시).
                    onClick={() => isReserved && setActiveSeatId((prev) => (prev === seat.id ? null : seat.id))}
                    className={`h-5 w-5 select-none rounded-sm ${seatClassName(isReserved, isAccessible)} ${
                      isReserved ? 'cursor-pointer' : 'cursor-default'
                    } ${isActive ? 'ring-2 ring-amber-300' : ''} ${isBlockStart ? 'ml-4' : ''}`}
                  />
                )
              })}
            </div>
          ))}
        </div>
      </HorizontalScroll>

      {activeSeat && (
        <p className="mt-3 flex items-center justify-center gap-2 text-center text-xs text-gray-400">
          <span>
            {activeSeat.rowLabel}
            {activeSeat.colNo}석 예매자: <span className="font-semibold text-gray-200">{activeSeat.bookerNickname ?? '알 수 없음'}</span>
            {activeSeat.bookerUsername && <span className="text-gray-500"> (아이디: {activeSeat.bookerUsername})</span>}
          </span>
          <button
            type="button"
            onClick={() => setPendingCancelSeat(activeSeat)}
            className="rounded-md border border-rose-500/50 px-2 py-0.5 text-[11px] font-semibold text-rose-400 hover:bg-rose-500/10"
          >
            예매 취소
          </button>
        </p>
      )}

      {pendingCancelSeat && (
        <ConfirmDialog
          title="예매 취소"
          message={`${pendingCancelSeat.rowLabel}${pendingCancelSeat.colNo}석(${pendingCancelSeat.bookerNickname ?? '알 수 없음'})의 예매를 취소할까요?\n취소된 좌석은 바로 다른 사용자가 예매할 수 있게 됩니다.`}
          confirmLabel="취소하기"
          danger
          onCancel={() => setPendingCancelSeat(null)}
          onConfirm={() => {
            onCancelBooking(pendingCancelSeat.bookingId)
              .then(() => {
                setActiveSeatId(null)
              })
              .catch(() => {
                // 에러 메시지는 부모(AdminScreeningsPage)가 다이얼로그로 보여줍니다.
              })
              .finally(() => {
                setPendingCancelSeat(null)
              })
          }}
        />
      )}

      <div className="mt-6 flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-xs text-gray-400">
        <span className="flex items-center gap-1.5">
          <span className="h-4 w-4 rounded-sm bg-slate-600" /> 선택 가능
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-4 w-4 rounded-sm bg-rose-500/80" /> 예매 완료 (클릭하면 예매자 표시)
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-4 w-4 rounded-sm bg-slate-600 ring-2 ring-sky-400" /> 장애인석
        </span>
      </div>
    </div>
  )
}

export default AdminSeatMap
