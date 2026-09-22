import { useState } from 'react'

function StarPicker({ score, onChange }) {
  return (
    <div className="flex gap-1 text-2xl">
      {[1, 2, 3, 4, 5].map((value) => (
        <button
          key={value}
          type="button"
          onClick={() => onChange(value)}
          aria-label={`${value}점`}
          className={value <= score ? 'text-amber-400' : 'text-gray-600 hover:text-amber-400/60'}
        >
          ★
        </button>
      ))}
    </div>
  )
}

function WriteReviewDialog({ movieTitle, initialReview, onSubmit, onClose }) {
  const isEditing = Boolean(initialReview)
  const [score, setScore] = useState(initialReview?.score ?? 5)
  const [content, setContent] = useState(initialReview?.content ?? '')

  const handleSubmit = (event) => {
    event.preventDefault()
    if (!content.trim()) return
    onSubmit({ score, content: content.trim() })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
      <div className="w-full max-w-md rounded-xl bg-slate-900 p-6">
        <h3 className="text-lg font-bold text-white">{isEditing ? '리뷰 수정' : '리뷰 쓰기'}</h3>
        <p className="mt-1 text-sm text-gray-400">{movieTitle}</p>

        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-300">평점</label>
            <StarPicker score={score} onChange={setScore} />
          </div>

          <div>
            <label htmlFor="review-content" className="mb-1 block text-sm font-medium text-gray-300">
              리뷰 내용
            </label>
            <textarea
              id="review-content"
              required
              rows={4}
              value={content}
              onChange={(event) => setContent(event.target.value)}
              placeholder="영화에 대한 감상을 남겨주세요"
              className="w-full rounded-lg bg-slate-800 px-3 py-2 text-sm text-gray-100 placeholder:text-gray-500 focus:outline-none"
            />
          </div>

          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border border-gray-700 px-4 py-2 text-sm font-semibold text-gray-200 hover:bg-slate-800"
            >
              취소
            </button>
            <button
              type="submit"
              className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
            >
              {isEditing ? '수정' : '등록'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default WriteReviewDialog
