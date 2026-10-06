import { Navigate, Route, Routes } from 'react-router-dom'
import MainLayout from './layouts/MainLayout'
import AdminLayout from './layouts/AdminLayout'
import HomePage from './pages/Home/HomePage'
import MovieListPage from './pages/MovieList/MovieListPage'
import MovieDetailPage from './pages/MovieDetail/MovieDetailPage'
import AiChatPage from './pages/AiChat/AiChatPage'
import BookingListPage from './pages/Booking/BookingListPage'
import BookingSeatPage from './pages/Booking/BookingSeatPage'
import PaymentSuccessPage from './pages/Payment/PaymentSuccessPage'
import PaymentFailPage from './pages/Payment/PaymentFailPage'
import MyPage from './pages/MyPage/MyPage'
import AdminReportsPage from './pages/Admin/AdminReportsPage'
import AdminMembersPage from './pages/Admin/AdminMembersPage'
import AdminScreeningsPage from './pages/Admin/AdminScreeningsPage'
import LoginPage from './pages/Auth/LoginPage'
import SignupPage from './pages/Auth/SignupPage'
import RequireAuth from './auth/RequireAuth'

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />

      <Route
        element={
          <RequireAuth>
            <MainLayout />
          </RequireAuth>
        }
      >
        <Route path="/" element={<HomePage />} />
        <Route path="/movies" element={<MovieListPage />} />
        <Route path="/movies/:id" element={<MovieDetailPage />} />
        <Route path="/ai-chat" element={<AiChatPage />} />
        <Route path="/booking" element={<BookingListPage />} />
        <Route path="/booking/:id" element={<BookingSeatPage />} />
        <Route path="/payment/success" element={<PaymentSuccessPage />} />
        <Route path="/payment/fail" element={<PaymentFailPage />} />
        <Route path="/mypage" element={<MyPage />} />
      </Route>

      <Route
        path="/admin"
        element={
          <RequireAuth adminOnly>
            <AdminLayout />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="reports" replace />} />
        <Route path="reports" element={<AdminReportsPage />} />
        <Route path="members" element={<AdminMembersPage />} />
        <Route path="screenings" element={<AdminScreeningsPage />} />
      </Route>
    </Routes>
  )
}

export default App
