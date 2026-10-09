import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'

import { PageHeader } from '@/components/page'
import { cn } from '@/lib/cn'
import { useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

import { SiteAiConversationsPanel } from './siteAiConversationsPanel'
import { SiteAiKnowledgePanel } from './siteAiKnowledgePanel'
import { SiteAiPreviewChat } from './siteAiPreviewChat'
import { SiteAiPromptPanel } from './siteAiPromptPanel'
import { SiteAiRuntimePanel } from './siteAiRuntimePanel'
import type { SiteWorkspaceOutletContext } from './siteWorkspaceLayout'

type AiTab = 'prompt' | 'knowledge' | 'conversations' | 'preview' | 'runtime'

export function SiteAiPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<SiteWorkspaceOutletContext>()
  const canPrompt = useHasPermission(PERMISSION_CODES.aiPromptRead)
  const canKnowledge = useHasPermission(PERMISSION_CODES.aiKbList)
  const canConversations = useHasPermission(PERMISSION_CODES.aiConversationList)

  const tabs = useMemo(() => {
    const items: Array<{ id: AiTab; label: string }> = []
    if (canPrompt) items.push({ id: 'prompt', label: t('aiWorkspace.tabs.prompt') })
    if (canKnowledge) items.push({ id: 'knowledge', label: t('aiWorkspace.tabs.knowledge') })
    if (canConversations) items.push({ id: 'conversations', label: t('aiWorkspace.tabs.conversations') })
    if (canPrompt) items.push({ id: 'preview', label: t('aiWorkspace.tabs.preview') })
    if (canPrompt) items.push({ id: 'runtime', label: t('aiWorkspace.tabs.runtime') })
    return items
  }, [canConversations, canKnowledge, canPrompt, t])

  const [tab, setTab] = useState<AiTab | null>(null)
  const activeTab = tab && tabs.some((item) => item.id === tab) ? tab : (tabs[0]?.id ?? null)

  return (
    <section>
      <PageHeader title={t('workspace.nav.ai')} description={t('aiWorkspace.description')} />
      {tabs.length === 0 || activeTab == null ? (
        <p className="rounded-xl border border-slate-200 bg-white px-4 py-6 text-sm text-slate-500 shadow-sm">
          {t('aiWorkspace.noPermission')}
        </p>
      ) : (
        <>
          <nav className="mb-4 flex flex-wrap gap-2">
            {tabs.map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => setTab(item.id)}
                className={cn(
                  'rounded-lg border px-3 py-1.5 text-sm font-medium transition',
                  activeTab === item.id
                    ? 'border-slate-900 bg-slate-900 text-white'
                    : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50',
                )}
              >
                {item.label}
              </button>
            ))}
          </nav>
          <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            {activeTab === 'prompt' ? <SiteAiPromptPanel siteId={site.id} /> : null}
            {activeTab === 'knowledge' ? <SiteAiKnowledgePanel siteId={site.id} /> : null}
            {activeTab === 'conversations' ? <SiteAiConversationsPanel siteId={site.id} /> : null}
            {activeTab === 'preview' ? <SiteAiPreviewChat siteId={site.id} /> : null}
            {activeTab === 'runtime' ? <SiteAiRuntimePanel siteId={site.id} /> : null}
          </article>
        </>
      )}
    </section>
  )
}
