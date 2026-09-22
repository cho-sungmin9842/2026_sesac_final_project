function StatCard({ label, value, delta }) {
  return (
    <div className="flex-1 rounded-xl bg-slate-900 p-5">
      <p className="text-sm text-gray-400">{label}</p>
      <p className="mt-2 text-2xl font-bold text-white">{value}</p>
      {delta && <p className="mt-1 text-xs text-emerald-400">▲ {delta}</p>}
    </div>
  )
}

export default StatCard
