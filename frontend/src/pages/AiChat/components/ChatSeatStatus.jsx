import AdminSeatMap from '../../Admin/components/AdminSeatMap'

// AI 추천 채팅에서 "OO일 OO관 OO시 회차 좌석 현황 알려줘"에 대한 답변 - 마이페이지 좌석 배치도와 같은
// 그리드(AdminSeatMap)를 그대로 재사용하고, 위에 어떤 회차인지/몇 석이 비어있는지만 요약해 붙입니다.
function ChatSeatStatus({ seatStatus }) {
  const bookedSeats = seatStatus.totalSeats - seatStatus.availableSeats

  return (
    <div className="w-full rounded-lg bg-slate-950/40 p-4">
      <p className="mb-1 text-sm font-semibold text-gray-100">
        {seatStatus.movieTitle} · {seatStatus.theaterName} · {seatStatus.dateLabel} {seatStatus.timeLabel}
      </p>
      <p className="mb-4 text-xs text-gray-400">
        전체 {seatStatus.totalSeats}석 중{' '}
        <span className="font-semibold text-emerald-400">{seatStatus.availableSeats}석 예매 가능</span>
        {` (${bookedSeats}석 예매 완료)`}
      </p>
      <AdminSeatMap seats={seatStatus.seats} />
    </div>
  )
}

export default ChatSeatStatus
