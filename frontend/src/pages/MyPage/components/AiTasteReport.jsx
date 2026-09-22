function AiTasteReport({ text }) {
  return (
    <section className="mt-6 flex items-start gap-3 rounded-xl bg-indigo-500/10 p-4">
      <span className="text-xl">🧠</span>
      <div>
        <h3 className="text-sm font-bold text-indigo-300">AI 취향 리포트</h3>
        <p className="mt-1 text-sm text-gray-300">{text}</p>
      </div>
    </section>
  )
}

export default AiTasteReport
