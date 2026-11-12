import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { rbacApi } from '@/api/rbacApi'
import { AdminPageHeader, DataTable, FormDialog, Spinner, StatusTag } from '@/components/admin'
import {
  adminDangerButtonMdClass,
  adminDangerGhostButtonClass,
  adminFieldClass,
  adminFormActionsClass,
  adminGhostButtonClass,
  adminPrimaryButtonClass,
  adminSecondaryButtonClass,
} from '@/components/admin/controls'
import { useHasPermission } from '@/router/permission'
import { ROUTES } from '@/router/config/routePaths'
import { useAuthStore } from '@/stores/authStore'
import { PERMISSION_CODES, ROLE_STATUS, type MenuCreateInput, type MenuNodeDto, type MenuWriteInput } from '@/types/rbac'

const menuCodePattern = /^admin:menu:[a-z][a-z0-9]*([.:][a-z0-9]+)*$/

type Draft = {
  mode: 'create' | 'edit'
  id: number | null
  menuCode: string
  menuName: string
  parentId: string
  path: string
  icon: string
  sort: string
  status: string
}

export function MenuTreePage() {
  const { t } = useTranslation('menus')
  const { t: tc } = useTranslation('common')
  const queryClient = useQueryClient()
  const loadCurrentUser = useAuthStore((state) => state.loadCurrentUser)
  const canCreate = useHasPermission(PERMISSION_CODES.menuCreate)
  const canUpdate = useHasPermission(PERMISSION_CODES.menuUpdate)
  const canDelete = useHasPermission(PERMISSION_CODES.menuDelete)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [deleting, setDeleting] = useState<{ id: number; name: string } | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['menus'],
    queryFn: () => rbacApi.listMenus(),
  })

  const menus = data ?? []
  const rows = useMemo(() => flattenMenus(menus), [menus])
  const excludedParentIds = useMemo(() => excludedIds(menus, draft), [draft, menus])
  const parentOptions = useMemo(
    () => flattenMenus(menus).filter((item) => !excludedParentIds.has(item.id)),
    [excludedParentIds, menus],
  )

  function refresh() {
    void queryClient.invalidateQueries({ queryKey: ['menus'] })
    void queryClient.invalidateQueries({ queryKey: ['permissions'] })
    void loadCurrentUser()
  }

  const saveMutation = useMutation({
    mutationFn: async (current: Draft) => {
      const body: MenuWriteInput = {
        menuName: current.menuName.trim(),
        parentId: current.parentId ? Number(current.parentId) : null,
        path: current.path.trim() || undefined,
        icon: current.icon.trim() || undefined,
        sort: Number(current.sort),
        status: current.status,
      }
      if (current.mode === 'create') {
        const input: MenuCreateInput = { ...body, menuCode: current.menuCode.trim() }
        return rbacApi.createMenu(input)
      }
      return rbacApi.updateMenu(current.id!, body)
    },
    onSuccess: (_data, current) => {
      toast.success(current.mode === 'create' ? t('messages.created') : t('messages.updated'))
      setDraft(null)
      setFormError(null)
      refresh()
    },
    onError: (err: Error) => toast.error(err.message),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => rbacApi.deleteMenu(id),
    onSuccess: () => {
      toast.success(t('messages.deleted'))
      setDeleting(null)
      setDraft(null)
      refresh()
    },
    onError: (err: Error) => toast.error(err.message),
  })

  function openCreate(parentId: number | null) {
    setFormError(null)
    setDraft({
      mode: 'create',
      id: null,
      menuCode: 'admin:menu:',
      menuName: '',
      parentId: parentId == null ? '' : String(parentId),
      path: '',
      icon: '',
      sort: '10',
      status: ROLE_STATUS.ACTIVE,
    })
  }

  function openEdit(node: MenuNodeDto) {
    if (node.id == null) return
    setFormError(null)
    setDraft({
      mode: 'edit',
      id: node.id,
      menuCode: node.menuCode,
      menuName: node.menuName,
      parentId: parentIdOf(menus, node.id) ?? '',
      path: node.path ?? '',
      icon: node.icon ?? '',
      sort: String(node.sort),
      status: node.status === ROLE_STATUS.DISABLED ? ROLE_STATUS.DISABLED : ROLE_STATUS.ACTIVE,
    })
  }

  function handleDelete(node: MenuNodeDto) {
    if (node.id == null) return
    setDeleting({ id: node.id, name: node.menuName })
  }

  function handleSave() {
    if (!draft) return
    if (draft.mode === 'create') {
      if (!draft.menuCode.trim()) {
        setFormError(t('validation.codeRequired'))
        return
      }
      if (!menuCodePattern.test(draft.menuCode.trim())) {
        setFormError(t('validation.codePattern'))
        return
      }
    }
    if (!draft.menuName.trim()) {
      setFormError(t('validation.nameRequired'))
      return
    }
    const sort = Number(draft.sort)
    if (!Number.isInteger(sort) || sort < 0 || sort > 9999) {
      setFormError(t('validation.sortInvalid'))
      return
    }
    setFormError(null)
    saveMutation.mutate(draft)
  }

  return (
    <section className="admin-content-data">
      <AdminPageHeader
        title={t('title')}
        description={t('description')}
        hint={
          <>
            {t('hint')}{' '}
            <Link to={ROUTES.system.roles} className="font-medium text-admin underline-offset-2 hover:underline">
              {t('assignLink')}
            </Link>
          </>
        }
      />

      <div className="mb-4 flex flex-wrap items-center gap-2">
        {canCreate ? (
          <button type="button" onClick={() => openCreate(null)} className={adminPrimaryButtonClass}>
            <Plus className="h-4 w-4" />
            {t('actions.create')}
          </button>
        ) : null}
      </div>

      {draft ? (
        <FormDialog
          title={draft.mode === 'create' ? t('form.createTitle') : t('form.editTitle')}
          closeLabel={t('actions.cancel')}
          onClose={() => {
            setDraft(null)
            setFormError(null)
          }}
        >
          <div className="grid gap-3 md:grid-cols-2">
            <label className="block text-sm text-admin">
              {t('labels.code')}
              <input
                className={`${adminFieldClass} mt-1 font-mono`}
                value={draft.menuCode}
                disabled={draft.mode === 'edit'}
                onChange={(event) => setDraft({ ...draft, menuCode: event.target.value })}
              />
            </label>
            <label className="block text-sm text-admin">
              {t('labels.name')}
              <input
                className={`${adminFieldClass} mt-1`}
                value={draft.menuName}
                onChange={(event) => setDraft({ ...draft, menuName: event.target.value })}
              />
            </label>
            <label className="block text-sm text-admin">
              {t('labels.parent')}
              <select
                className={`${adminFieldClass} mt-1`}
                value={draft.parentId}
                onChange={(event) => setDraft({ ...draft, parentId: event.target.value })}
              >
                <option value="">{t('rootParent')}</option>
                {parentOptions.map((item) => (
                  <option key={item.id} value={item.id}>
                    {'　'.repeat(item.depth)}
                    {item.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="block text-sm text-admin">
              {t('labels.path')}
              <input
                className={`${adminFieldClass} mt-1`}
                value={draft.path}
                onChange={(event) => setDraft({ ...draft, path: event.target.value })}
              />
            </label>
            <label className="block text-sm text-admin">
              {t('labels.icon')}
              <input
                className={`${adminFieldClass} mt-1`}
                value={draft.icon}
                onChange={(event) => setDraft({ ...draft, icon: event.target.value })}
              />
            </label>
            <label className="block text-sm text-admin">
              {t('labels.sort')}
              <input
                className={`${adminFieldClass} mt-1`}
                inputMode="numeric"
                value={draft.sort}
                onChange={(event) => setDraft({ ...draft, sort: event.target.value })}
              />
            </label>
            <label className="block text-sm text-admin">
              {t('labels.status')}
              <select
                className={`${adminFieldClass} mt-1`}
                value={draft.status}
                onChange={(event) => setDraft({ ...draft, status: event.target.value })}
              >
                <option value={ROLE_STATUS.ACTIVE}>{tc('enum.status.ACTIVE')}</option>
                <option value={ROLE_STATUS.DISABLED}>{tc('enum.status.DISABLED')}</option>
              </select>
            </label>
          </div>
          {formError ? <p className="mt-3 text-sm text-danger-emphasis">{formError}</p> : null}
          <div className={adminFormActionsClass}>
            <button
              type="button"
              onClick={() => {
                setDraft(null)
                setFormError(null)
              }}
              className={adminSecondaryButtonClass}
            >
              {t('actions.cancel')}
            </button>
            <button
              type="button"
              disabled={saveMutation.isPending}
              onClick={handleSave}
              className={adminPrimaryButtonClass}
            >
              {saveMutation.isPending ? <Spinner /> : null}
              {saveMutation.isPending ? t('actions.saving') : t('actions.save')}
            </button>
          </div>
        </FormDialog>
      ) : null}

      {deleting ? (
        <FormDialog
          title={t('messages.deleteConfirmTitle')}
          closeLabel={t('actions.cancel')}
          onClose={() => setDeleting(null)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">
            {t('messages.confirmDelete', { name: deleting.name })}
          </p>
          <div className={adminFormActionsClass}>
            <button
              type="button"
              className={adminSecondaryButtonClass}
              onClick={() => setDeleting(null)}
            >
              {t('actions.cancel')}
            </button>
            <button
              type="button"
              className={adminDangerButtonMdClass}
              disabled={deleteMutation.isPending}
              onClick={() => deleteMutation.mutate(deleting.id)}
            >
              {deleteMutation.isPending ? <Spinner /> : null}
              {t('actions.remove')}
            </button>
          </div>
        </FormDialog>
      ) : null}

      <DataTable
        columns={[
          {
            id: 'name',
            header: t('labels.name'),
            cell: (item) => <span className="font-medium" style={{ paddingLeft: item.depth * 16 }}>{item.name}</span>,
          },
          { id: 'code', header: t('labels.code'), cell: (item) => <span className="font-mono text-xs text-admin-muted">{item.code}</span> },
          { id: 'path', header: t('labels.path'), cell: (item) => item.path || '—' },
          { id: 'sort', header: t('labels.sort'), cell: (item) => item.sort },
          {
            id: 'status',
            header: t('labels.status'),
            cell: (item) => <StatusTag status={item.status ?? ROLE_STATUS.ACTIVE} />,
          },
          {
            id: 'actions',
            header: t('labels.actions'),
            cell: (item) => (
              <div className="flex flex-wrap gap-2">
                {canCreate ? (
                  <button type="button" onClick={() => openCreate(item.id)} className={adminGhostButtonClass}>
                    {t('actions.createChild')}
                  </button>
                ) : null}
                {canUpdate ? (
                  <button type="button" onClick={() => openEdit(item.node)} className={adminGhostButtonClass}>
                    {t('actions.edit')}
                  </button>
                ) : null}
                {canDelete ? (
                  <button
                    type="button"
                    disabled={item.node.children.length > 0 || deleteMutation.isPending}
                    onClick={() => handleDelete(item.node)}
                    className={adminDangerGhostButtonClass}
                  >
                    {t('actions.remove')}
                  </button>
                ) : null}
              </div>
            ),
          },
        ]}
        data={rows}
        rowKey={(item) => item.id}
        isLoading={isPending}
        isError={isError}
        errorMessage={error instanceof Error ? error.message : t('error')}
        emptyMessage={t('empty')}
        onRetry={() => void refetch()}
      />
    </section>
  )
}

function flattenMenus(nodes: MenuNodeDto[], depth = 0, acc: FlatMenu[] = []): FlatMenu[] {
  for (const node of nodes) {
    if (node.id != null) {
      acc.push({
        id: node.id,
        name: node.menuName,
        code: node.menuCode,
        path: node.path,
        sort: node.sort,
        status: node.status,
        depth,
        node,
      })
    }
    if (node.children?.length) flattenMenus(node.children, depth + 1, acc)
  }
  return acc
}

function excludedIds(nodes: MenuNodeDto[], draft: Draft | null) {
  const excluded = new Set<number>()
  if (!draft || draft.mode !== 'edit' || draft.id == null) return excluded
  const self = findNode(nodes, draft.id)
  if (!self) return excluded
  excluded.add(draft.id)
  for (const id of descendantIds(self)) excluded.add(id)
  return excluded
}

function findNode(nodes: MenuNodeDto[], id: number): MenuNodeDto | null {
  for (const node of nodes) {
    if (node.id === id) return node
    const found = findNode(node.children ?? [], id)
    if (found) return found
  }
  return null
}

function parentIdOf(nodes: MenuNodeDto[], id: number): string | null {
  function walk(list: MenuNodeDto[], parent: number | null): string | null {
    for (const node of list) {
      if (node.id === id) return parent == null ? '' : String(parent)
      if (node.children?.length) {
        const found = walk(node.children, node.id ?? null)
        if (found != null) return found
      }
    }
    return null
  }
  return walk(nodes, null)
}

function descendantIds(node: MenuNodeDto): number[] {
  const ids: number[] = []
  for (const child of node.children ?? []) {
    if (child.id != null) ids.push(child.id)
    ids.push(...descendantIds(child))
  }
  return ids
}

type FlatMenu = {
  id: number
  name: string
  code: string
  path: string | null
  sort: number
  status?: string
  depth: number
  node: MenuNodeDto
}
