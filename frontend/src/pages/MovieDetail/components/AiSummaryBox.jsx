function AiSummaryBox({ summary }) {
  return (
    <aside className="w-72 shrink-0 rounded-xl bg-indigo-500/10 p-4">
      <h4 className="mb-2 flex items-center gap-1 text-sm font-bold text-indigo-300">
        ✨ AI 요약
      </h4>
      <p className="text-sm leading-relaxed text-gray-300">{summary}</p>
    </aside>
  )
}

export default AiSummaryBox
