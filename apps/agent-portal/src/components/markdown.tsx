import { isValidElement, useState, type ReactElement, type ReactNode } from 'react'
import ReactMarkdown, { type Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { Check, Copy } from 'lucide-react'

/**
 * AI 回答的 Markdown 渲染（GFM：表格/刪除線/任務列表）。
 * react-markdown 默認不渲染原始 HTML，模型輸出里的標籤只會以文本呈現，不走 dangerouslySetInnerHTML。
 * 樣式全部走 gpt-* 主題 token，與聊天頁其餘部分保持同一套語言。
 */

function extractText(node: ReactNode): string {
  if (typeof node === 'string') return node
  if (typeof node === 'number') return String(node)
  if (Array.isArray(node)) return node.map(extractText).join('')
  if (isValidElement(node)) return extractText((node.props as { children?: ReactNode }).children)
  return ''
}

/** GPT 風格代碼塊：深色底，頂欄顯示語言名 + 複製鈕，正文橫向滾動。 */
function CodeBlock({ children }: { children?: ReactNode }) {
  const [copied, setCopied] = useState(false)
  const codeElement = (Array.isArray(children) ? children[0] : children) as
    | ReactElement<{ className?: string; children?: ReactNode }>
    | undefined
  const language = /language-([\w-]+)/.exec(codeElement?.props?.className ?? '')?.[1] ?? ''

  async function copyCode() {
    try {
      await navigator.clipboard.writeText(extractText(codeElement?.props?.children))
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1500)
    } catch {
      setCopied(false)
    }
  }

  return (
    <div className="mb-3 overflow-hidden rounded-xl bg-gpt-code text-gpt-code-fg">
      <div className="flex items-center justify-between px-3.5 pt-2 text-xs text-gpt-code-fg/60">
        <span>{language || 'code'}</span>
        <button
          type="button"
          onClick={() => void copyCode()}
          className="flex items-center gap-1.5 rounded px-1 py-0.5 transition hover:text-gpt-code-fg"
        >
          {copied ? <Check size={13} /> : <Copy size={13} />}
          {copied ? '已複製' : '複製代碼'}
        </button>
      </div>
      <pre className="overflow-x-auto p-3.5 pt-2 font-mono text-[13px] leading-6">{children}</pre>
    </div>
  )
}
const components: Components = {
  p: ({ children }) => <p className="mb-3 leading-7">{children}</p>,
  h1: ({ children }) => <h1 className="mb-2 mt-4 text-xl font-semibold">{children}</h1>,
  h2: ({ children }) => <h2 className="mb-2 mt-4 text-lg font-semibold">{children}</h2>,
  h3: ({ children }) => <h3 className="mb-2 mt-3 text-base font-semibold">{children}</h3>,
  h4: ({ children }) => <h4 className="mb-2 mt-3 text-[15px] font-semibold">{children}</h4>,
  ul: ({ children }) => <ul className="mb-3 list-disc space-y-1 pl-5">{children}</ul>,
  ol: ({ children }) => <ol className="mb-3 list-decimal space-y-1 pl-5">{children}</ol>,
  li: ({ children }) => <li className="leading-7">{children}</li>,
  a: ({ children, href }) => (
    <a href={href} target="_blank" rel="noreferrer" className="text-primary underline">
      {children}
    </a>
  ),
  blockquote: ({ children }) => (
    <blockquote className="mb-3 border-l-2 border-gpt-line pl-3 text-gpt-muted">{children}</blockquote>
  ),
  hr: () => <hr className="my-4 border-gpt-line" />,
  pre: ({ children }) => <CodeBlock>{children}</CodeBlock>,
  code: ({ children, className }) => {
    const isBlock = Boolean(className?.startsWith('language-'))
    if (isBlock) {
      return <code className="font-mono">{children}</code>
    }
    return (
      <code className="rounded bg-gpt-sidebar px-1 py-0.5 font-mono text-[13px]">{children}</code>
    )
  },
  table: ({ children }) => (
    <div className="mb-3 overflow-x-auto">
      <table className="w-full border-collapse text-sm">{children}</table>
    </div>
  ),
  th: ({ children }) => (
    <th className="border border-gpt-line bg-gpt-sidebar px-2 py-1 text-left font-medium">{children}</th>
  ),
  td: ({ children }) => <td className="border border-gpt-line px-2 py-1">{children}</td>,
  img: ({ src, alt }) => <img src={src} alt={alt ?? ''} className="max-w-full rounded-xl" />,
}

export function MarkdownText({ children }: { children: string }) {
  return (
    <div className="text-base text-gpt-ink [&>*:first-child]:mt-0 [&>*:last-child]:mb-0">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
        {children}
      </ReactMarkdown>
    </div>
  )
}
