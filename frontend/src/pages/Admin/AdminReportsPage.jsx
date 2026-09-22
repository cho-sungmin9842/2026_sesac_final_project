import { reportStats, reports } from '../../data/reports'
import ReportTable from './components/ReportTable'
import StatCard from './components/StatCard'

function AdminReportsPage() {
  return (
    <div className="px-8 py-6">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-bold text-white">리뷰 신고 관리</h1>
        <span className="rounded-md bg-slate-900 px-3 py-1.5 text-sm text-gray-300">
          오늘 신규 {reportStats.newToday}건
        </span>
      </div>

      <div className="flex gap-4">
        <StatCard label="대기중인 신고" value={`${reportStats.pending}건`} delta={`${reportStats.pendingDelta}건 (전일 대비)`} />
        <StatCard label="오늘 처리 완료" value={`${reportStats.resolvedToday}건`} />
        <StatCard label="이번주 계정정지" value={`${reportStats.suspendedThisWeek}명`} />
      </div>

      <div className="mt-6 rounded-xl bg-slate-900/60 p-4">
        <ReportTable reports={reports} />
      </div>
    </div>
  )
}

export default AdminReportsPage
