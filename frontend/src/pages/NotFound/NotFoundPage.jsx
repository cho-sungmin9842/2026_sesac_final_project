import { Link } from 'react-router-dom'

function NotFoundPage() {
  return (
    <div className="mx-auto flex min-h-[70vh] max-w-7xl flex-col items-center justify-center gap-4 px-4 py-6 text-center sm:px-6">
      <p className="text-5xl font-bold text-indigo-400">404</p>
      <h1 className="text-xl font-bold text-gray-100">페이지를 찾을 수 없습니다</h1>
      <p className="text-sm text-gray-400">주소를 다시 확인해주시거나, 아래 버튼으로 홈으로 돌아가주세요.</p>
      <Link
        to="/"
        className="mt-2 rounded-lg bg-indigo-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-indigo-500"
      >
        홈으로 돌아가기
      </Link>
    </div>
  )
}

export default NotFoundPage
