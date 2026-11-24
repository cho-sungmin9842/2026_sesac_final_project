import { useEffect, useRef, useState } from 'react'
import { BASE_URL } from '../../../api/httpClient'

// 사진 첨부 용량 제한 안내 - 백엔드(ReviewService.MAX_PHOTO_SIZE_BYTES)와 같은 값입니다.
const MAX_PHOTO_SIZE_BYTES = 5 * 1024 * 1024

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
  const [error, setError] = useState('')
  // 사진 첨부는 선택 사항입니다 - photoFile이 있으면 리뷰 저장 직후 별도 업로드 요청으로 이어집니다
  // (MovieDetailPage.handleReviewSubmit 참고). photoPreviewUrl은 새로 고른 사진의 미리보기,
  // 없으면 수정 중인 기존 리뷰의 사진을 보여줍니다.
  const [photoFile, setPhotoFile] = useState(null)
  const [photoPreviewUrl, setPhotoPreviewUrl] = useState(null)
  // 파일 탐색기를 다시 열었다가 "취소"를 누른 경우 - 아무 변경도 안 한 것과 달리, 사용자는 이걸
  // "사진을 비웠다"는 뜻으로 받아들입니다(2026-11-20 요청). 그래서 이미 붙어있던 기존 사진까지 같이
  // 가립니다. 네이티브 change 이벤트는 취소 시 발생하지 않아 별도로 cancel 이벤트를 직접 붙입니다
  // (크로미움 계열 브라우저 지원 - React는 이 이벤트를 선언적 prop으로 지원하지 않습니다).
  const [photoRemoved, setPhotoRemoved] = useState(false)
  const fileInputRef = useRef(null)

  // 파일 선택창을 열었다가 "취소"하는 것 외에, "사진 삭제" 버튼으로도 직접 뗄 수 있게 합니다
  // (2026-11-21 요청 - 취소 버튼만으로는 찾기 어려운 조작이라는 지적).
  const clearPhoto = () => {
    setPhotoFile(null)
    setPhotoPreviewUrl(null)
    setPhotoRemoved(true)
    // input 자체의 값(브라우저가 버튼 옆에 직접 그려주는 파일명 표시)은 자동으로 비워지지 않아서,
    // 그대로 두면 미리보기는 사라졌는데 파일명 글자만 남아있게 됩니다.
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  useEffect(() => {
    const input = fileInputRef.current
    if (!input) return undefined
    input.addEventListener('cancel', clearPhoto)
    return () => input.removeEventListener('cancel', clearPhoto)
  }, [])

  const handlePhotoChange = (event) => {
    const file = event.target.files?.[0]
    if (!file) return
    if (!file.type.startsWith('image/')) {
      setError('이미지 파일만 첨부할 수 있습니다.')
      event.target.value = ''
      return
    }
    if (file.size > MAX_PHOTO_SIZE_BYTES) {
      setError('사진 용량은 5MB 이하만 가능합니다.')
      event.target.value = ''
      return
    }
    setError('')
    setPhotoFile(file)
    setPhotoPreviewUrl(URL.createObjectURL(file))
    setPhotoRemoved(false)
  }

  const displayedPhotoUrl = photoRemoved
    ? null
    : (photoPreviewUrl ?? (initialReview?.photoUrl ? `${BASE_URL}${initialReview.photoUrl}` : null))

  // <input type="file">가 버튼 옆에 직접 그려주는 파일명은 "이번에 이 입력창에서 실제로 고른 파일"
  // 에만 반응해서, 기존에 저장된 리뷰 사진(initialReview.photoUrl)은 거기에 반영되지 않습니다
  // (2026-11-20 요청) - 그래서 네이티브 표시 대신 직접 계산한 파일명을 보여줍니다. 디스크에는 충돌
  // 방지를 위해 UUID로 새로 만든 파일명으로 저장되지만, 사용자가 실제로 골랐던 원본 파일명(확장자
  // 포함)은 photoOriginalName 컬럼에 별도로 저장해뒀다가 여기서 그대로 보여줍니다.
  const existingPhotoFileName = initialReview?.photoUrl
    ? (initialReview.photoOriginalName ?? initialReview.photoUrl.split('/').pop())
    : null
  const displayedFileName = photoFile
    ? photoFile.name
    : !photoRemoved && existingPhotoFileName
      ? existingPhotoFileName
      : '선택된 파일 없음'

  const handleSubmit = (event) => {
    event.preventDefault()
    if (!content.trim()) {
      setError('리뷰 내용을 입력해주세요.')
      return
    }
    setError('')
    // photoRemoved: 새 사진을 고른 게 아니라, 기존에 붙어있던 사진을 떼어내 달라는 요청입니다
    // (photoFile이 있으면 그쪽이 우선이라 신경 쓸 필요 없음 - MovieDetailPage.handleReviewSubmit 참고).
    onSubmit({ score, content: content.trim(), photoFile, photoRemoved })
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
              rows={4}
              value={content}
              onChange={(event) => {
                setContent(event.target.value)
                if (error) setError('')
              }}
              placeholder="영화에 대한 감상을 남겨주세요"
              className="w-full rounded-lg bg-slate-800 px-3 py-2 text-sm text-gray-100 placeholder:text-gray-500 focus:outline-none"
            />
            {error && <p className="mt-1 text-xs text-rose-400">{error}</p>}
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-gray-300">사진 첨부 (선택)</label>
            {displayedPhotoUrl && (
              <img
                src={displayedPhotoUrl}
                alt="첨부한 사진 미리보기"
                className="mb-2 h-32 max-w-full rounded-lg object-contain"
              />
            )}
            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="shrink-0 rounded-lg bg-slate-800 px-3 py-1.5 text-xs font-semibold text-gray-200 hover:bg-slate-700"
              >
                파일 선택
              </button>
              <span className="truncate text-xs text-gray-400">{displayedFileName}</span>
              {displayedPhotoUrl && (
                <button
                  type="button"
                  onClick={clearPhoto}
                  className="shrink-0 text-xs text-rose-400 hover:underline"
                >
                  사진 삭제
                </button>
              )}
            </div>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              onChange={handlePhotoChange}
              className="hidden"
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
