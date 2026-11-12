import { Children, cloneElement, isValidElement, useId, type ReactElement, type ReactNode } from 'react'

type FormFieldProps = {
  /** label 文案（上置，12px/500，DS §8）。 */
  label: ReactNode
  /** 必填：label 後加 danger 色 `*`（純呈現，校验仍由各頁既有邏輯負責）。 */
  required?: boolean
  /** 幫助文字（12 muted）；與 error 共用字段下槽位，error 出現時被替換。 */
  help?: ReactNode
  /** 校验錯誤文字（12 danger）；槽位恒在（min-h-4），出現/消失不跳動布局。 */
  error?: ReactNode
  /** 顯式指定 label 的 htmlFor（優先於自動 id 關聯）。 */
  htmlFor?: string
  /** 控件 id：未傳且子控件為單一原生元素時，以 useId 生成並注入子控件。 */
  id?: string
  className?: string
  children: ReactNode
}

/**
 * 表單字段基線（DS §8）：label 上置＋字段＋help/error 共用槽位。
 *
 * id 關聯：子節點為單一原生元素（input/select/textarea 等）且自身無 id 時，
 * 把解析出的 id（props.id 或 useId 生成）以 cloneElement 注入，label 的 htmlFor
 * 指向同一 id；子結構複雜（多子節點/自帶 id 的組件）時不注入，label 僅在
 * caller 傳了 id/htmlFor 時才帶 htmlFor，避免指向不存在的 id。
 */
export function FormField({
  label,
  required = false,
  help,
  error,
  htmlFor,
  id,
  className,
  children,
}: FormFieldProps) {
  const generatedId = useId()

  const singleChild =
    Children.count(children) === 1 && isValidElement(children)
      ? (children as ReactElement<{ id?: string }>)
      : null
  const childId = singleChild?.props.id
  const controlId = id ?? childId ?? generatedId
  const canInject = singleChild != null && typeof singleChild.type === 'string' && childId == null
  const control = canInject ? cloneElement(singleChild, { id: controlId }) : children
  const labelFor = htmlFor ?? (canInject || id != null || childId != null ? controlId : undefined)

  return (
    <div className={className}>
      <label htmlFor={labelFor} className="mb-1.5 block text-xs font-medium text-admin-muted">
        {label}
        {required ? (
          <>
            {' '}
            <span className="text-danger-emphasis">*</span>
          </>
        ) : null}
      </label>
      {control}
      <div className="mt-1 min-h-4">
        {error ? (
          <p className="text-xs text-danger-emphasis">{error}</p>
        ) : help ? (
          <p className="text-xs text-admin-muted">{help}</p>
        ) : null}
      </div>
    </div>
  )
}
