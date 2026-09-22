import { Outlet } from 'react-router-dom'
import AdminSidebar from '../components/layout/AdminSidebar'

function AdminLayout() {
  return (
    <div className="flex min-h-svh bg-slate-950 text-gray-100">
      <AdminSidebar />
      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  )
}

export default AdminLayout
