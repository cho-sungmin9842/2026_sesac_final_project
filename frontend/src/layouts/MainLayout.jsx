import { Outlet } from 'react-router-dom'
import Header from '../components/layout/Header'

function MainLayout() {
  return (
    <div className="min-h-svh bg-slate-950 text-gray-100">
      <Header />
      <main>
        <Outlet />
      </main>
    </div>
  )
}

export default MainLayout
