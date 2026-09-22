const STATUS_STYLES = {
  대기: 'bg-red-500/10 text-red-400',
  검토중: 'bg-amber-500/10 text-amber-400',
  처리완료: 'bg-emerald-500/10 text-emerald-400',
}

function ReportTable({ reports }) {
  return (
    <table className="w-full text-left text-sm">
      <thead>
        <tr className="border-b border-white/5 text-gray-400">
          <th className="py-3 font-medium">영화</th>
          <th className="py-3 font-medium">리뷰 내용</th>
          <th className="py-3 font-medium">신고 사유</th>
          <th className="py-3 font-medium">상태</th>
          <th className="py-3 font-medium">처리</th>
        </tr>
      </thead>
      <tbody>
        {reports.map((report) => (
          <tr key={report.id} className="border-b border-white/5 text-gray-200">
            <td className="py-4 font-semibold">{report.movie}</td>
            <td className="py-4 text-gray-400">{report.content}</td>
            <td className="py-4 text-gray-400">{report.reason}</td>
            <td className="py-4">
              <span className={`rounded-md px-2 py-1 text-xs font-medium ${STATUS_STYLES[report.status]}`}>
                {report.status}
              </span>
            </td>
            <td className="py-4">
              {report.status === '처리완료' ? (
                <span className="rounded-md bg-slate-800 px-3 py-1.5 text-xs text-gray-400">완료됨</span>
              ) : (
                <div className="flex gap-2">
                  <button
                    type="button"
                    className="rounded-md bg-indigo-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-indigo-500"
                  >
                    태그 부착
                  </button>
                  <button
                    type="button"
                    className="rounded-md bg-slate-800 px-3 py-1.5 text-xs font-semibold text-gray-200 hover:bg-slate-700"
                  >
                    삭제
                  </button>
                </div>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

export default ReportTable
