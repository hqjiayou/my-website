import { Outlet } from 'react-router-dom'

import { AdminHeader } from './admin-header'
import { AdminMobileBackdrop } from './admin-mobile-backdrop'
import { AdminSidebar } from './admin-sidebar'

/**
 * 后台主布局：组合侧栏、顶栏与主内容区。
 * 引用方式：`import { AdminLayout } from '@/layouts/admin'`
 */
export function AdminLayout() {
  return (
    <div className="min-h-screen bg-slate-100 text-slate-900">
      <AdminMobileBackdrop />
      <AdminSidebar />
      <div className="lg:pl-72">
        <AdminHeader />
        <main className="p-4 sm:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
