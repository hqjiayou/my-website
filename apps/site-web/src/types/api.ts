export interface HttpMessageBody<T> {
  code?: number | null
  message?: string
  data: T
}
