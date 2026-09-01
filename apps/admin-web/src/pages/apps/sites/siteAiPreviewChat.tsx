import {
  AssistantRuntimeProvider,
  ComposerPrimitive,
  MessagePrimitive,
  ThreadPrimitive,
  useLocalRuntime,
} from '@assistant-ui/react'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { createJavaPreviewAdapter } from './siteAiPreviewAdapter'

export function SiteAiPreviewChat({ siteId }: { siteId: number }) {
  const { t } = useTranslation('sites')
  const adapter = useMemo(() => createJavaPreviewAdapter(siteId), [siteId])
  const runtime = useLocalRuntime(adapter)

  return (
    <AssistantRuntimeProvider runtime={runtime}>
      <p className="mb-3 text-sm text-slate-600">{t('aiWorkspace.preview.hint')}</p>
      <ThreadPrimitive.Root className="flex h-[28rem] flex-col overflow-hidden rounded-xl border border-slate-200 bg-slate-50">
        <ThreadPrimitive.Viewport className="flex-1 overflow-y-auto px-3 py-3">
          <ThreadPrimitive.Empty>
            <p className="px-2 text-sm text-slate-500">{t('aiWorkspace.preview.empty')}</p>
          </ThreadPrimitive.Empty>
          <ThreadPrimitive.Messages>
            {() => (
              <MessagePrimitive.Root className="mb-3 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800">
                <MessagePrimitive.Content />
              </MessagePrimitive.Root>
            )}
          </ThreadPrimitive.Messages>
        </ThreadPrimitive.Viewport>
        <ComposerPrimitive.Root className="flex gap-2 border-t border-slate-200 bg-white p-3">
          <ComposerPrimitive.Input
            className="min-h-10 flex-1 resize-none rounded-lg border border-slate-200 px-3 py-2 text-sm"
            placeholder={t('aiWorkspace.preview.placeholder')}
          />
          <ComposerPrimitive.Send className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white">
            {t('aiWorkspace.preview.send')}
          </ComposerPrimitive.Send>
        </ComposerPrimitive.Root>
      </ThreadPrimitive.Root>
    </AssistantRuntimeProvider>
  )
}
