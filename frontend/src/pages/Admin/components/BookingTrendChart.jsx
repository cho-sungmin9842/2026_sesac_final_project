import { useState } from 'react'
import HorizontalScroll from '../../../components/common/HorizontalScroll'

// 날짜별 예매 건수/매출액을 간단한 막대그래프로 보여줍니다. 차트 라이브러리 없이 SVG만으로 그려서(이
// 프로젝트엔 별도 차트 패키지가 없습니다), 특정 날짜/회차로 좁혀보는 아래쪽 표들과 달리 "최근 흐름"을
// 한눈에 보여주는 용도입니다.
//
// 막대 폭을 "전체 너비 ÷ 날짜 수"로 나누던 예전 방식은 기간이 길어질수록(4주=29일) 막대가 좁아지고
// 날짜 라벨이 서로 겹쳐 읽기 힘들어지는 문제가 있었습니다. 그래서 막대 폭/간격은 고정하고, 차트
// 전체 너비가 날짜 수만큼 늘어나게 한 뒤 HorizontalScroll로 가로 스크롤하는 방식으로 바꿨습니다 -
// 날짜가 며칠이든 막대 하나하나가 항상 같은(읽기 편한) 크기를 유지합니다.
const BAR_WIDTH = 22
const BAR_GAP = 12
const BAR_PITCH = BAR_WIDTH + BAR_GAP
const CHART_HEIGHT = 160
const LABEL_AREA_HEIGHT = 24
// 날짜가 많아지면(예: 29일) 라벨을 전부 보여주면 그래도 빽빽하니, 라벨 사이 실제 간격이 최소
// 이만큼(px)은 되도록 몇 개씩 건너뛰며 보여줍니다.
const MIN_LABEL_GAP_PX = 56

function formatWon(value) {
  return `${value.toLocaleString()}원`
}

// metric: 'count'(건수) | 'revenue'(매출액) - 막대 높이/상단 숫자만 이 기준을 따르고, 호버 툴팁에는
// 항상 건수와 매출액을 함께 보여줍니다.
function BookingTrendChart({ data, metric = 'count' }) {
  const [hoveredIndex, setHoveredIndex] = useState(null)

  if (!data || data.length === 0) {
    return <p className="text-sm text-gray-500">예매 추이 데이터가 없습니다.</p>
  }

  const valueOf = (point) => (metric === 'revenue' ? point.revenue : point.count)
  const formatValue = (value) => (metric === 'revenue' ? formatWon(value) : `${value}`)

  // 전부 0이어도 막대가 전부 찌그러지지 않도록 최소 기준값을 1로 둡니다.
  const maxValue = Math.max(1, ...data.map(valueOf))
  const chartWidth = data.length * BAR_PITCH - BAR_GAP
  const labelStep = Math.max(1, Math.ceil(MIN_LABEL_GAP_PX / BAR_PITCH))

  const hovered = hoveredIndex != null ? data[hoveredIndex] : null
  const hoveredCenterX = hoveredIndex != null ? hoveredIndex * BAR_PITCH + BAR_WIDTH / 2 : 0
  // 가운데 정렬로 띄우면 맨 왼쪽/오른쪽 막대에서는 툴팁이 차트 밖으로 잘려나가니, 가장자리 근처에서는
  // 중앙 대신 그쪽 가장자리에 붙여 안쪽으로만 펼쳐지게 합니다.
  const tooltipAlign = hoveredCenterX < 120 ? 'left' : hoveredCenterX > chartWidth - 120 ? 'right' : 'center'

  return (
    <HorizontalScroll>
      <div className="relative" style={{ width: chartWidth }}>
        <svg
          viewBox={`0 0 ${chartWidth} ${CHART_HEIGHT + LABEL_AREA_HEIGHT}`}
          width={chartWidth}
          height={CHART_HEIGHT + LABEL_AREA_HEIGHT}
        >
          {data.map((point, index) => {
            const value = valueOf(point)
            const barHeight = (value / maxValue) * (CHART_HEIGHT - 16)
            const x = index * BAR_PITCH
            const y = CHART_HEIGHT - barHeight
            const label = point.date.slice(5).replace('-', '/')
            const showLabel = index % labelStep === 0 || index === data.length - 1

            return (
              <g key={point.date}>
                {value > 0 && (
                  <text x={x + BAR_WIDTH / 2} y={y - 4} textAnchor="middle" className="fill-gray-300 text-[10px]">
                    {formatValue(value)}
                  </text>
                )}
                <rect x={x} y={y} width={BAR_WIDTH} height={Math.max(barHeight, 1)} rx={2} className="fill-indigo-500" />
                {showLabel && (
                  <text x={x + BAR_WIDTH / 2} y={CHART_HEIGHT + 16} textAnchor="middle" className="fill-gray-500 text-[10px]">
                    {label}
                  </text>
                )}
                {/* 실제 막대는 값이 적을 때 몇 px밖에 안 돼서 마우스를 올리기 어려우니, 호버 영역은
                    차트 세로 전체 높이로 넉넉하게 잡습니다(투명 사각형). */}
                <rect
                  x={x}
                  y={0}
                  width={BAR_WIDTH}
                  height={CHART_HEIGHT}
                  fill="transparent"
                  onMouseEnter={() => setHoveredIndex(index)}
                  onMouseLeave={() => setHoveredIndex((prev) => (prev === index ? null : prev))}
                />
              </g>
            )
          })}
        </svg>

        {hovered && (
          // HorizontalScroll의 스크롤 영역(overflow-x-auto)은 overflow-y도 함께 auto로 계산되어
          // 세로로도 잘려나가므로, 차트 바깥(위)으로 띄우면 안 보입니다. 차트 박스 안쪽 상단에
          // 겹쳐서 띄웁니다.
          <div
            className="pointer-events-none absolute top-1 z-10 w-max max-w-[260px] rounded-lg bg-slate-900 px-3 py-2 text-xs text-gray-100 shadow-lg ring-1 ring-slate-700"
            style={
              tooltipAlign === 'left'
                ? { left: 0 }
                : tooltipAlign === 'right'
                  ? { left: chartWidth, transform: 'translateX(-100%)' }
                  : { left: hoveredCenterX, transform: 'translateX(-50%)' }
            }
          >
            <p className="mb-1 font-semibold text-gray-200">
              {hovered.date} · 총 {hovered.count}건 · {formatWon(hovered.revenue)}
            </p>
            {hovered.byMovie.length === 0 ? (
              <p className="text-gray-400">예매 없음</p>
            ) : (
              <ul className="space-y-0.5">
                {hovered.byMovie.map((movie) => (
                  <li key={movie.movieTitle} className="flex justify-between gap-3 text-gray-300">
                    <span className="truncate">{movie.movieTitle}</span>
                    <span className="shrink-0">
                      {movie.count}건 · {formatWon(movie.revenue)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>
    </HorizontalScroll>
  )
}

export default BookingTrendChart
