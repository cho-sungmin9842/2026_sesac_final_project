// 날짜별 예매 건수를 간단한 막대그래프로 보여줍니다. 차트 라이브러리 없이 SVG만으로 그려서(이
// 프로젝트엔 별도 차트 패키지가 없습니다), 특정 날짜/회차로 좁혀보는 아래쪽 표들과 달리 "최근 흐름"을
// 한눈에 보여주는 용도입니다.
const CHART_WIDTH = 600
const CHART_HEIGHT = 160
const BAR_GAP = 6

function BookingTrendChart({ data }) {
  if (!data || data.length === 0) {
    return <p className="text-sm text-gray-500">예매 추이 데이터가 없습니다.</p>
  }

  // 전부 0건이어도 막대가 전부 찌그러지지 않도록 최소 기준값을 1로 둡니다.
  const maxCount = Math.max(1, ...data.map((point) => point.count))
  const barWidth = (CHART_WIDTH - BAR_GAP * (data.length - 1)) / data.length

  return (
    <div className="overflow-x-auto">
      <svg viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT + 24}`} className="h-44 w-full min-w-[480px]">
        {data.map((point, index) => {
          const barHeight = (point.count / maxCount) * (CHART_HEIGHT - 16)
          const x = index * (barWidth + BAR_GAP)
          const y = CHART_HEIGHT - barHeight
          const label = point.date.slice(5).replace('-', '/')
          return (
            <g key={point.date}>
              {point.count > 0 && (
                <text x={x + barWidth / 2} y={y - 4} textAnchor="middle" className="fill-gray-300 text-[9px]">
                  {point.count}
                </text>
              )}
              <rect x={x} y={y} width={barWidth} height={Math.max(barHeight, 1)} rx={2} className="fill-indigo-500" />
              <text x={x + barWidth / 2} y={CHART_HEIGHT + 16} textAnchor="middle" className="fill-gray-500 text-[9px]">
                {label}
              </text>
            </g>
          )
        })}
      </svg>
    </div>
  )
}

export default BookingTrendChart
