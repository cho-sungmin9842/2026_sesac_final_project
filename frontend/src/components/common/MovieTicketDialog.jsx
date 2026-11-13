import { useEffect, useState } from 'react'
import QRCode from 'qrcode'
import { getMovieDetail } from '../../api/movieApi'
import { AGE_CATEGORIES } from '../../pages/Booking/bookingData'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

function formatTicketDate(dateStr) {
  if (!dateStr) return ''
  const date = new Date(`${dateStr}T00:00:00`)
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')} (${WEEKDAYS[date.getDay()]})`
}

// MyPage.jsx의 formatTicketCounts와 같은 로직입니다 - 표시 순서(성인→청소년→어린이→우대)를 맞춰야 하는
// AGE_CATEGORIES 쪽 책임이라 공용 유틸로 빼지 않고 그대로 복사해 씁니다.
function formatTicketCounts(ticketCounts) {
  if (!ticketCounts) return ''
  return AGE_CATEGORIES.map(({ key, label }) => {
    const count = ticketCounts[key]
    return count > 0 ? `${label} ${count}` : null
  })
    .filter(Boolean)
    .join(' · ')
}

// 예매 정보를 인코딩한 실제 QR코드입니다(검증 서버는 없어, 스캔하면 이 예매의 정보가 텍스트로
// 보이는 정도의 장식입니다). 같은 예매는 같은 값이라 다시 열어도 항상 같은 QR코드가 나옵니다.
function QrCodeDecoration({ value }) {
  const [dataUrl, setDataUrl] = useState(null)

  useEffect(() => {
    let cancelled = false
    QRCode.toDataURL(value, { margin: 1, width: 160, color: { dark: '#1e293b', light: '#ffffff' } })
      .then((url) => {
        if (!cancelled) setDataUrl(url)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [value])

  return (
    <div className="flex justify-center" aria-hidden="true">
      {dataUrl ? (
        <img src={dataUrl} alt="" className="h-28 w-28 rounded-lg" />
      ) : (
        <div className="h-28 w-28 animate-pulse rounded-lg bg-slate-800" />
      )}
    </div>
  )
}

// 예매 완료 직후, 그리고 마이페이지 예매 내역에서 "티켓 보기"를 눌렀을 때 똑같이 띄우는 모바일 티켓
// 모양 다이얼로그입니다. 가운데 점선 분리선 + 좌우 반원 노치로 실제 영화관 티켓을 반으로 뜯어낸
// 듯한 모양을 냅니다(노치는 바깥 배경색(검정/70%)과 같은 색 원을 양 끝에 겹쳐서 "구멍"처럼 보이게
// 만든 것이라, 뒤에 있는 배경과 완벽히 안 맞을 수 있지만 장식 목적으로는 충분합니다).
function MovieTicketDialog({ booking, onClose }) {
  const [posterUrl, setPosterUrl] = useState(null)
  const ticketQrValue = [
    '새싹시네마 모바일 티켓',
    `예매번호 ${String(booking.id ?? 0).padStart(8, '0')}`,
    booking.movieTitle,
    `${booking.theaterName} ${booking.showDate} ${booking.showtime?.slice(0, 5)}`,
    `좌석 ${booking.seats.join(', ')}`,
  ].join('\n')

  useEffect(() => {
    if (!booking.movieId) return undefined
    let cancelled = false
    getMovieDetail(booking.movieId)
      .then((detail) => {
        if (!cancelled) setPosterUrl(detail.posterUrl)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [booking.movieId])

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4" onClick={onClose}>
      <div
        className="relative w-full max-w-sm rounded-3xl bg-slate-900 shadow-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="relative h-36 overflow-hidden rounded-t-3xl">
          {posterUrl ? (
            <img src={posterUrl} alt="" className="h-full w-full object-cover" />
          ) : (
            <div className="h-full w-full bg-gradient-to-br from-indigo-600 to-purple-700" />
          )}
          <div className="absolute inset-0 bg-gradient-to-t from-slate-900 via-slate-900/70 to-slate-900/10" />
          <div className="absolute inset-x-5 bottom-3">
            <p className="text-xs font-semibold text-indigo-300">새싹시네마 모바일 티켓</p>
            <h2 className="mt-1 text-lg font-bold leading-tight text-white">{booking.movieTitle}</h2>
          </div>
        </div>

        <div className="space-y-4 px-6 py-5">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-wide text-gray-500">관람일</p>
              <p className="mt-0.5 text-sm font-semibold text-white">{formatTicketDate(booking.showDate)}</p>
            </div>
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-wide text-gray-500">시간</p>
              <p className="mt-0.5 text-sm font-semibold text-white">{booking.showtime?.slice(0, 5)}</p>
            </div>
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-wide text-gray-500">상영관</p>
              <p className="mt-0.5 text-sm font-semibold text-white">{booking.theaterName}</p>
            </div>
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-wide text-gray-500">인원</p>
              <p className="mt-0.5 text-sm font-semibold text-white">{formatTicketCounts(booking.ticketCounts)}</p>
            </div>
          </div>

          <div className="rounded-xl bg-indigo-600/15 px-4 py-3 text-center">
            <p className="text-[11px] font-semibold uppercase tracking-wide text-indigo-300">좌석</p>
            <p className="mt-1 text-2xl font-black tracking-wide text-white">{booking.seats.join(' · ')}</p>
          </div>
        </div>

        <div className="relative">
          <div className="absolute -left-2.5 top-1/2 h-5 w-5 -translate-y-1/2 rounded-full bg-black/70" />
          <div className="absolute -right-2.5 top-1/2 h-5 w-5 -translate-y-1/2 rounded-full bg-black/70" />
          <div className="border-t-2 border-dashed border-slate-700" />
        </div>

        <div className="space-y-3 px-6 py-5">
          <QrCodeDecoration value={ticketQrValue} />
          <p className="text-center text-xs text-gray-500">예매번호 {String(booking.id ?? 0).padStart(8, '0')}</p>
          <button
            type="button"
            onClick={onClose}
            className="w-full rounded-lg bg-indigo-600 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
          >
            닫기
          </button>
        </div>
      </div>
    </div>
  )
}

export default MovieTicketDialog
