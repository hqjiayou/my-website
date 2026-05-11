import type { Role, User } from '@/mocks/types/user'

export const MOCK_ROLES: Role[] = [
  { id: 'role-super-admin', key: 'super-admin', name: '超级管理员' },
  { id: 'role-developer', key: 'developer', name: '开发者' },
  { id: 'role-operator', key: 'operator', name: '运营' },
  { id: 'role-editor', key: 'editor', name: '内容编辑' },
  { id: 'role-reservation', key: 'reservation-agent', name: '预约处理' },
]

export const MOCK_USERS: User[] = [
  {
    id: 'user-001',
    siteId: 'tokyo-car-repair',
    name: 'Admin Li',
    email: 'admin.li@example.com',
    roleKey: 'super-admin',
    status: 'active',
  },
  {
    id: 'user-002',
    siteId: 'tokyo-car-repair',
    name: 'Dev Sato',
    email: 'dev.sato@example.com',
    roleKey: 'developer',
    status: 'active',
  },
  {
    id: 'user-003',
    siteId: 'sweet-heart-cake',
    name: 'Operator Mei',
    email: 'operator.mei@example.com',
    roleKey: 'operator',
    status: 'inactive',
  },
]
