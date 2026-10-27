import { useEffect, useRef, useState } from 'react'

/**
 * 語音輸入（Web Speech API，瀏覽器原生語音轉文字，零後端）。
 * 不支援的瀏覽器不渲染按鈕（supported=false），不假裝可用。
 * onSessionText 回傳「本次錄音會話」的累計文字（定稿＋臨時結果），
 * 由調用方拼到既有草稿之後。
 */

interface SpeechResultItem {
  isFinal: boolean
  0: { transcript: string }
}

interface SpeechResultEvent {
  resultIndex: number
  results: ArrayLike<SpeechResultItem>
}

interface SpeechRecognitionInstance {
  lang: string
  interimResults: boolean
  continuous: boolean
  onresult: ((event: SpeechResultEvent) => void) | null
  onend: (() => void) | null
  onerror: ((event: { error?: string }) => void) | null
  start: () => void
  stop: () => void
  abort: () => void
}

type SpeechRecognitionCtor = new () => SpeechRecognitionInstance

function getCtor(): SpeechRecognitionCtor | null {
  const w = window as unknown as {
    SpeechRecognition?: SpeechRecognitionCtor
    webkitSpeechRecognition?: SpeechRecognitionCtor
  }
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null
}

interface UseSpeechInputOptions {
  onSessionText: (text: string) => void
  onError: (message: string) => void
}

export function useSpeechInput({ onSessionText, onError }: UseSpeechInputOptions) {
  const [supported] = useState<boolean>(() => typeof window !== 'undefined' && getCtor() !== null)
  const [listening, setListening] = useState(false)
  const recognitionRef = useRef<SpeechRecognitionInstance | null>(null)
  const finalRef = useRef('')
  const callbacksRef = useRef({ onSessionText, onError })
  useEffect(() => {
    callbacksRef.current = { onSessionText, onError }
  })

  function start() {
    const Ctor = getCtor()
    if (!Ctor || recognitionRef.current) return
    const recognition = new Ctor()
    recognition.lang = 'zh-TW'
    recognition.interimResults = true
    recognition.continuous = true
    finalRef.current = ''
    recognition.onresult = (event) => {
      let interim = ''
      for (let i = event.resultIndex; i < event.results.length; i += 1) {
        const result = event.results[i]
        if (result.isFinal) {
          finalRef.current += result[0].transcript
        } else {
          interim += result[0].transcript
        }
      }
      callbacksRef.current.onSessionText(finalRef.current + interim)
    }
    recognition.onend = () => {
      recognitionRef.current = null
      setListening(false)
    }
    recognition.onerror = (event) => {
      if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
        callbacksRef.current.onError('無法使用麥克風，請確認瀏覽器已允許麥克風權限')
      }
      recognitionRef.current = null
      setListening(false)
    }
    recognitionRef.current = recognition
    try {
      recognition.start()
      setListening(true)
    } catch {
      recognitionRef.current = null
      setListening(false)
    }
  }

  function stop() {
    recognitionRef.current?.stop()
  }

  useEffect(() => {
    return () => {
      recognitionRef.current?.abort()
      recognitionRef.current = null
    }
  }, [])

  return { supported, listening, start, stop }
}
