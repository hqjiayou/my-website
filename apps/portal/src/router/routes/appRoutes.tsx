import { Navigate, Route, Routes } from 'react-router-dom'

import { ChatPage } from '@/pages/chat/chatPage'
import { HomePage } from '@/pages/home/homePage'
import { KnowledgePage } from '@/pages/knowledge/knowledgePage'
import { LoginPage } from '@/pages/login/loginPage'

import { ROUTES } from '../config/routePaths'
import { AuthGuard } from '../guards/authGuard'
import { GuestGuard } from '../guards/guestGuard'

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<GuestGuard />}>
        <Route path={ROUTES.login} element={<LoginPage />} />
      </Route>
      <Route element={<AuthGuard />}>
        <Route path={ROUTES.home} element={<HomePage />} />
        <Route path={ROUTES.chat} element={<ChatPage />} />
        <Route path={ROUTES.knowledge} element={<KnowledgePage />} />
      </Route>
      <Route path="*" element={<Navigate to={ROUTES.home} replace />} />
    </Routes>
  )
}
