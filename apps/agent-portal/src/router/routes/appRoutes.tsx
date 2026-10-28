import { Navigate, Route, Routes } from 'react-router-dom'

import { AppShell } from '@/components/appShell'
import { ChatPage } from '@/pages/chat/chatPage'
import { HomePage } from '@/pages/home/homePage'
import { KnowledgePage } from '@/pages/knowledge/knowledgePage'
import { LoginPage } from '@/pages/login/loginPage'
import { MemoriesPage } from '@/pages/memories/memoriesPage'
import { SharePage } from '@/pages/share/sharePage'

import { ROUTES } from '../config/routePaths'
import { AuthGuard } from '../guards/authGuard'
import { GuestGuard } from '../guards/guestGuard'

export function AppRoutes() {
  return (
    <Routes>
      <Route path={`${ROUTES.share}/:token`} element={<SharePage />} />
      <Route element={<GuestGuard />}>
        <Route path={ROUTES.login} element={<LoginPage />} />
      </Route>
      <Route element={<AuthGuard />}>
        <Route element={<AppShell />}>
          <Route path={ROUTES.home} element={<HomePage />} />
          <Route path={`${ROUTES.chat}/:conversationId?`} element={<ChatPage />} />
          <Route path={ROUTES.knowledge} element={<KnowledgePage />} />
          <Route path={ROUTES.memories} element={<MemoriesPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to={ROUTES.home} replace />} />
    </Routes>
  )
}
