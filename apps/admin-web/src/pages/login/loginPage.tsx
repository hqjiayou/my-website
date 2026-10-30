import { zodResolver } from '@hookform/resolvers/zod'
import { Eye, EyeOff, X } from 'lucide-react'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { z } from 'zod'

import { authApi } from '@/api/authApi'
import { ROUTES } from '@/router/config/routePaths'
import { useAuthStore } from '@/stores/authStore'
import {
  loadRememberUsername,
  loadSavedUsername,
  persistLoginUsername,
} from '@/utils/loginPreferences'

function createLoginSchema(t: (key: string) => string) {
  return z.object({
    username: z.string().min(1, t('errors.usernameRequired')),
    password: z.string().min(1, t('errors.passwordRequired')),
    rememberUsername: z.boolean(),
  })
}

type LoginFormValues = z.infer<ReturnType<typeof createLoginSchema>>

export function LoginPage() {
  const navigate = useNavigate()
  const { t } = useTranslation('login')
  const loginSchema = createLoginSchema(t)
  const setToken = useAuthStore((state) => state.setToken)
  const loadCurrentUser = useAuthStore((state) => state.loadCurrentUser)
  const [submitting, setSubmitting] = useState(false)
  const [showPassword, setShowPassword] = useState(false)

  const {
    register,
    handleSubmit,
    control,
    setValue,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      username: loadSavedUsername(),
      password: '',
      rememberUsername: loadRememberUsername(),
    },
  })

  const usernameValue = useWatch({ control, name: 'username' })
  const passwordValue = useWatch({ control, name: 'password' })

  async function onSubmit(values: LoginFormValues) {
    setSubmitting(true)
    try {
      const { accessToken } = await authApi.login({
        username: values.username,
        password: values.password,
      })
      setToken(accessToken)
      persistLoginUsername(values.username, values.rememberUsername)

      const loaded = await loadCurrentUser()
      if (!loaded) {
        toast.error(t('errors.profileLoadFailed'))
        return
      }

      navigate(ROUTES.dashboard, { replace: true })
    } catch (error) {
      const message = error instanceof Error ? error.message : t('errors.generic')
      toast.error(message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden px-4">
      <LoginOceanBackground />

      <div className="relative z-10 w-full max-w-md rounded-2xl border-2 border-cyan-900/15 bg-white/94 p-8 shadow-xl shadow-cyan-950/25 backdrop-blur-md">
        <div className="mb-8 text-center">
          <p className="text-[10px] font-semibold uppercase tracking-[0.22em] text-cyan-700/70">{t('kicker')}</p>
          <h1 className="mt-2 text-2xl font-semibold tracking-tight text-slate-900">{t('title')}</h1>
          <p className="mt-2 text-sm text-slate-500">{t('description')}</p>
        </div>

        <form className="space-y-5" onSubmit={handleSubmit(onSubmit)} noValidate>
          <div>
            <label htmlFor="username" className="mb-1.5 block text-sm font-medium text-slate-700">
              {t('fields.username')}
            </label>
            <div className="relative">
              <input
                id="username"
                type="text"
                autoComplete="username"
                className="w-full rounded-lg border border-slate-200 py-2.5 pl-3 pr-10 text-sm text-slate-900 outline-none transition focus:border-cyan-500 focus:ring-2 focus:ring-cyan-200"
                {...register('username')}
              />
              {usernameValue ? (
                <button
                  type="button"
                  aria-label={t('actions.clearUsername')}
                  onClick={() => setValue('username', '', { shouldValidate: true })}
                  className="absolute inset-y-0 right-0 inline-flex w-10 items-center justify-center text-slate-400 transition hover:text-slate-600"
                >
                  <X className="h-4 w-4" />
                </button>
              ) : null}
            </div>
            {errors.username ? <p className="mt-1.5 text-xs text-red-600">{errors.username.message}</p> : null}
          </div>

          <div>
            <label htmlFor="password" className="mb-1.5 block text-sm font-medium text-slate-700">
              {t('fields.password')}
            </label>
            <div className="relative">
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                className="w-full rounded-lg border border-slate-200 py-2.5 pl-3 pr-20 text-sm text-slate-900 outline-none transition focus:border-cyan-500 focus:ring-2 focus:ring-cyan-200"
                {...register('password')}
              />
              <div className="absolute inset-y-0 right-0 flex items-center pr-1">
                {passwordValue ? (
                  <button
                    type="button"
                    aria-label={t('actions.clearPassword')}
                    onClick={() => setValue('password', '', { shouldValidate: true })}
                    className="inline-flex h-8 w-8 items-center justify-center text-slate-400 transition hover:text-slate-600"
                  >
                    <X className="h-4 w-4" />
                  </button>
                ) : null}
                <button
                  type="button"
                  aria-label={showPassword ? t('actions.hidePassword') : t('actions.showPassword')}
                  onClick={() => setShowPassword((value) => !value)}
                  className="inline-flex h-8 w-8 items-center justify-center text-slate-400 transition hover:text-slate-600"
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>
            {errors.password ? <p className="mt-1.5 text-xs text-red-600">{errors.password.message}</p> : null}
          </div>

          <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-600">
            <input
              type="checkbox"
              className="admin-check"
              {...register('rememberUsername')}
            />
            <span>{t('fields.rememberUsername')}</span>
          </label>

          <button
            type="submit"
            disabled={submitting}
            className="flex w-full items-center justify-center rounded-lg bg-cyan-900 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-cyan-800 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {submitting ? t('actions.submitting') : t('actions.submit')}
          </button>
        </form>
      </div>
    </div>
  )
}

/* ——————————————————————————————————————————
 * 海洋像素背景（本文件内聚，无外部资源）
 * —————————————————————————————————————————— */

function LoginOceanBackground() {
  return (
    <div className="login-pixel-ocean pointer-events-none absolute inset-0 overflow-hidden" aria-hidden>
      <style>{oceanStyles}</style>

      {/* 天空 */}
      <div
        className="absolute inset-x-0 top-0 h-[24%]"
        style={{
          background: 'linear-gradient(180deg, #dff6ff 0%, #b8ecff 40%, #7fd4f2 75%, #5ec0e8 100%)',
        }}
      />

      {/* 太阳 + 水面反光 */}
      <div
        className="absolute left-[11%] top-[7%] h-3 w-3 bg-[#ffe566] sm:h-4 sm:w-4"
        style={{
          imageRendering: 'pixelated',
          boxShadow: '12px 0 0 #ffe566, 0 12px 0 #ffe566, 12px 12px 0 #ffe566, 6px 6px 0 #fff3a3',
        }}
      />
      <div
        className="ocean-sun-shimmer absolute left-[11%] top-[19%] h-2 w-3 opacity-60 sm:w-4"
        style={{
          imageRendering: 'pixelated',
          background: 'repeating-linear-gradient(180deg, #fff9c4 0 4px, transparent 4px 8px)',
        }}
      />

      <PixelCloud className="left-[26%] top-[5%] opacity-95" large />
      <PixelCloud className="left-[48%] top-[9%] scale-90 opacity-80" />
      <PixelCloud className="right-[8%] top-[6%] scale-75 opacity-85" />
      <PixelCloud className="right-[28%] top-[12%] scale-110 opacity-70" />

      <PixelSeagull className="left-[62%] top-[11%]" />
      <PixelSeagull className="left-[78%] top-[8%]" small delay />

      {/* 远岛剪影 */}
      <div
        className="absolute bottom-[76%] left-[55%] h-8 w-24 opacity-50 sm:w-32"
        style={{
          imageRendering: 'pixelated',
          background: '#4a8fa8',
          clipPath: 'polygon(0 100%, 15% 55%, 35% 70%, 50% 40%, 68% 62%, 85% 35%, 100% 50%, 100% 100%)',
        }}
      />

      {/* 海水主体 */}
      <div
        className="absolute inset-x-0 bottom-0 top-[20%]"
        style={{
          background:
            'linear-gradient(180deg, #5ed4ea 0%, #38b8d8 18%, #2496b8 38%, #157a9c 58%, #0c5f7d 78%, #07384a 100%)',
        }}
      />

      {/* 水下光束 */}
      <div className="ocean-light-ray absolute left-[8%] top-[24%] h-[50%] w-8 opacity-20" />
      <div className="ocean-light-ray absolute left-[28%] top-[26%] h-[45%] w-6 opacity-15" style={{ animationDelay: '2s' }} />
      <div className="ocean-light-ray absolute right-[18%] top-[25%] h-[48%] w-10 opacity-18" style={{ animationDelay: '4s' }} />

      <div
        className="ocean-caustics absolute inset-x-0 bottom-0 top-[22%] opacity-30"
        style={{
          backgroundImage: `
            repeating-linear-gradient(105deg, transparent 0 14px, rgba(255,255,255,0.1) 14px 16px, transparent 16px 32px),
            repeating-linear-gradient(-75deg, transparent 0 20px, rgba(255,255,255,0.06) 20px 22px, transparent 22px 40px)
          `,
          backgroundSize: '56px 56px, 72px 72px',
        }}
      />

      {/* 波浪 */}
      <div className="ocean-wave ocean-wave--back absolute inset-x-0 top-[17%] h-7 opacity-45" />
      <div className="ocean-wave ocean-wave--mid absolute inset-x-0 top-[19%] h-5 opacity-60" />
      <div className="ocean-wave ocean-wave--front absolute inset-x-0 top-[21%] h-4 opacity-85" />
      <div className="ocean-wave ocean-wave--foam absolute inset-x-0 top-[22%] h-2 opacity-70" />

      {/* 远景暗礁 */}
      <div
        className="absolute bottom-[32%] left-[5%] h-6 w-14 rounded-sm bg-[#0a4d62] opacity-70"
        style={{ imageRendering: 'pixelated' }}
      />
      <div
        className="absolute bottom-[28%] right-[12%] h-8 w-20 rounded-sm bg-[#0a4d62] opacity-55"
        style={{ imageRendering: 'pixelated' }}
      />

      {/* 鱼群 */}
      <PixelFish className="left-[8%] top-[34%]" color="#ffd166" flip />
      <PixelFish className="left-[32%] top-[42%]" color="#7bed9f" scale={0.9} />
      <PixelFish className="left-[55%] top-[36%]" color="#ff9ff3" flip />
      <PixelFish className="right-[10%] top-[48%]" color="#54a0ff" />
      <PixelFish className="left-[22%] top-[58%]" color="#feca57" flip scale={0.75} />
      <PixelFish className="right-[28%] top-[62%]" color="#5cd6b3" scale={0.8} />

      <PixelJellyfish className="left-[68%] top-[40%]" />
      <PixelJellyfish className="left-[14%] top-[52%]" small delay />

      {/* 气泡 */}
      <PixelBubbles className="left-[16%] bottom-[30%]" count={5} />
      <PixelBubbles className="left-[42%] bottom-[38%]" count={4} delay />
      <PixelBubbles className="right-[20%] bottom-[32%]" count={6} delay />
      <PixelBubbles className="right-[38%] bottom-[45%]" count={3} />

      {/* 海草丛 */}
      <PixelSeaweed className="left-[4%] bottom-[13%]" tall />
      <PixelSeaweed className="left-[12%] bottom-[12%]" />
      <PixelSeaweed className="left-[22%] bottom-[11%]" tall />
      <PixelSeaweed className="right-[6%] bottom-[12%]" tall />
      <PixelSeaweed className="right-[16%] bottom-[11%]" />
      <PixelSeaweed className="right-[26%] bottom-[10%]" tall />

      {/* 浮标 */}
      <PixelBuoy className="left-[48%] top-[23%]" />

      {/* 海底 */}
      <div className="absolute bottom-0 left-0 right-0 h-[13%] min-h-[68px] bg-[#e8c478]" />
      <div className="absolute bottom-0 left-0 right-0 h-2 bg-[#c9a04a]" />
      <div
        className="absolute bottom-[13%] left-0 right-0 h-4 opacity-45"
        style={{
          backgroundImage:
            'repeating-linear-gradient(90deg, #c9a04a 0 8px, #e8c478 8px 16px, #d4b060 16px 24px, #e0c070 24px 32px)',
          backgroundSize: '32px 8px',
          imageRendering: 'pixelated',
        }}
      />

      {/* 珊瑚、贝壳、海星 */}
      <PixelCoral className="left-[8%] bottom-[14%]" variant="pink" />
      <PixelCoral className="left-[32%] bottom-[13%]" variant="orange" />
      <PixelCoral className="left-[58%] bottom-[14%]" variant="teal" />
      <PixelCoral className="right-[14%] bottom-[13%]" variant="pink" />
      <PixelShell className="left-[20%] bottom-[13%]" />
      <PixelShell className="right-[32%] bottom-[12%]" flip />
      <PixelStarfish className="right-[8%] bottom-[13%]" />
      <PixelStarfish className="left-[44%] bottom-[12%]" small />

      {/* 水面下暗部 + 像素格 + 暗角 */}
      <div className="absolute inset-x-0 top-[20%] h-16 bg-gradient-to-b from-black/10 to-transparent" />
      <div
        className="absolute inset-0 opacity-[0.055]"
        style={{
          backgroundImage: `
            linear-gradient(to right, #042f3d 1px, transparent 1px),
            linear-gradient(to bottom, #042f3d 1px, transparent 1px)
          `,
          backgroundSize: '8px 8px',
          imageRendering: 'pixelated',
        }}
      />
      <div
        className="absolute inset-0"
        style={{
          background:
            'radial-gradient(ellipse 78% 68% at 50% 40%, transparent 0%, rgba(4, 28, 42, 0.5) 100%)',
        }}
      />
    </div>
  )
}

function PixelCloud({ className, large }: { className?: string; large?: boolean }) {
  const p = large ? 'h-2.5 w-2.5 bg-white/95 sm:h-3 sm:w-3' : 'h-2 w-2 bg-white/95 sm:h-2.5 sm:w-2.5'
  return (
    <div className={`absolute ${className ?? ''}`} style={{ imageRendering: 'pixelated' }}>
      <div className="flex">
        <span className={`${p} opacity-0`} />
        <span className={p} />
        <span className={p} />
        <span className={p} />
        <span className={`${p} opacity-0`} />
      </div>
      <div className="flex">
        <span className={p} />
        <span className={p} />
        <span className={p} />
        <span className={p} />
        <span className={p} />
      </div>
      {large ? (
        <div className="flex">
          <span className={`${p} opacity-0`} />
          <span className={p} />
          <span className={p} />
          <span className={`${p} opacity-0`} />
        </div>
      ) : null}
    </div>
  )
}

function PixelSeagull({ className, small, delay }: { className?: string; small?: boolean; delay?: boolean }) {
  const p = small ? 'h-1.5 w-1.5' : 'h-2 w-2'
  return (
    <div
      className={`ocean-seagull absolute ${className ?? ''}`}
      style={{ imageRendering: 'pixelated', animationDelay: delay ? '1.5s' : '0s' }}
    >
      <div className="flex">
        <span className={`${p} bg-white`} />
        <span className={`${p} bg-white opacity-0`} />
        <span className={`${p} bg-white`} />
      </div>
      <div className="flex">
        <span className={`${p} bg-slate-700`} />
        <span className={`${p} bg-white`} />
        <span className={`${p} bg-slate-700`} />
      </div>
    </div>
  )
}

function PixelFish({
  className,
  color,
  flip,
  scale = 1,
}: {
  className?: string
  color: string
  flip?: boolean
  scale?: number
}) {
  const p = 'absolute h-2 w-2 sm:h-2.5 sm:w-2.5'
  return (
    <div
      className={`absolute ${className ?? ''}`}
      style={{
        imageRendering: 'pixelated',
        transform: `${flip ? 'scaleX(-1)' : ''} scale(${scale})`,
      }}
    >
      <div className="ocean-fish-swim relative h-6 w-8">
      <span className={p} style={{ left: 0, top: 8, backgroundColor: color }} />
      <span className={p} style={{ left: 8, top: 8, backgroundColor: color }} />
      <span className={p} style={{ left: 16, top: 8, backgroundColor: color }} />
      <span className={p} style={{ left: 24, top: 0, backgroundColor: color }} />
      <span className={p} style={{ left: 8, top: 0, backgroundColor: color }} />
      <span className="absolute h-1 w-1 bg-slate-900" style={{ left: 20, top: 2 }} />
      </div>
    </div>
  )
}

function PixelJellyfish({ className, small, delay }: { className?: string; small?: boolean; delay?: boolean }) {
  const head = small ? 'h-2 w-3' : 'h-3 w-4'
  return (
    <div
      className={`ocean-jelly-drift absolute ${className ?? ''}`}
      style={{ imageRendering: 'pixelated', animationDelay: delay ? '2s' : '0s' }}
    >
      <span className={`${head} block rounded-sm bg-[#c8a8ff]/80`} />
      <span className="mt-0.5 block h-2 w-0.5 bg-[#e8d4ff]/60" />
      <span className="ml-1 -mt-2 block h-2 w-0.5 bg-[#e8d4ff]/50" />
      <span className="ml-2 -mt-2 block h-2 w-0.5 bg-[#e8d4ff]/50" />
    </div>
  )
}

function PixelBubbles({ className, count = 4, delay }: { className?: string; count?: number; delay?: boolean }) {
  const sizes = ['h-1 w-1', 'h-1.5 w-1.5', 'h-2 w-2', 'h-1 w-1', 'h-2.5 w-2.5', 'h-1.5 w-1.5']
  return (
    <div className={`absolute flex gap-2 sm:gap-3 ${className ?? ''}`}>
      {sizes.slice(0, count).map((size, i) => (
        <span
          key={i}
          className={`ocean-bubble rounded-sm border border-white/40 bg-white/20 ${size}`}
          style={{
            animationDelay: `${(delay ? 1.5 : 0) + i * 0.55}s`,
            imageRendering: 'pixelated',
          }}
        />
      ))}
    </div>
  )
}

function PixelSeaweed({ className, tall }: { className?: string; tall?: boolean }) {
  const h = tall ? 'h-4 w-2 sm:h-5' : 'h-2 w-2 sm:h-3'
  const c = tall ? '#2d8a5e' : '#3aa86e'
  const c2 = tall ? '#236b49' : '#2d8a5e'
  return (
    <div className={`absolute flex items-end gap-px ${className ?? ''}`} style={{ imageRendering: 'pixelated' }}>
      <span className={`${h} ${c} ocean-seaweed-sway`} />
      <span className={`${h} ${c2} ocean-seaweed-sway`} style={{ animationDelay: '0.4s' }} />
      <span className={`${h} ${c} ocean-seaweed-sway`} style={{ animationDelay: '0.8s' }} />
      {tall ? <span className={`${h} ${c2} ocean-seaweed-sway`} style={{ animationDelay: '1.1s' }} /> : null}
    </div>
  )
}

function PixelBuoy({ className }: { className?: string }) {
  const p = 'h-2 w-2 sm:h-2.5 sm:w-2.5'
  return (
    <div className={`ocean-buoy-bob absolute ${className ?? ''}`} style={{ imageRendering: 'pixelated' }}>
      <span className={`${p} block bg-[#e74c3c]`} />
      <span className={`${p} block bg-white`} />
      <span className={`${p} block bg-[#e74c3c]`} />
      <span className="mx-auto mt-0.5 block h-3 w-0.5 bg-[#8b5a2b]" />
    </div>
  )
}

function PixelCoral({ className, variant }: { className?: string; variant: 'pink' | 'orange' | 'teal' }) {
  const palette = {
    pink: { a: '#ff6b9d', b: '#ff8fab' },
    orange: { a: '#ff9f43', b: '#ffb86b' },
    teal: { a: '#1dd1a1', b: '#48dbaf' },
  }[variant]
  const p = 'h-2 w-2 sm:h-2.5 sm:w-2.5'
  return (
    <div className={`absolute ${className ?? ''}`} style={{ imageRendering: 'pixelated' }}>
      <div className="flex items-end">
        <span className={p} style={{ backgroundColor: palette.b }} />
        <span className={p} style={{ backgroundColor: palette.a }} />
        <span className={p} style={{ backgroundColor: palette.b }} />
      </div>
      <div className="flex">
        <span className={`${p} opacity-0`} />
        <span className={p} style={{ backgroundColor: palette.a }} />
        <span className={`${p} opacity-0`} />
      </div>
      <div className="flex">
        <span className={p} style={{ backgroundColor: palette.b }} />
        <span className={`${p} opacity-0`} />
        <span className={p} style={{ backgroundColor: palette.b }} />
      </div>
    </div>
  )
}

function PixelShell({ className, flip }: { className?: string; flip?: boolean }) {
  return (
    <div
      className={`absolute ${className ?? ''}`}
      style={{ imageRendering: 'pixelated', transform: flip ? 'scaleX(-1)' : undefined }}
    >
      <span className="block h-2 w-3 rounded-t-full bg-[#f5e6d3] sm:h-2.5 sm:w-4" />
      <span className="mx-auto block h-1 w-2 bg-[#e8d4c4]" />
    </div>
  )
}

function PixelStarfish({ className, small }: { className?: string; small?: boolean }) {
  const p = small ? 'h-1.5 w-1.5' : 'h-2 w-2'
  return (
    <div className={`absolute ${className ?? ''}`} style={{ imageRendering: 'pixelated' }}>
      <div className="flex justify-center">
        <span className={`${p} bg-[#ff6b6b]`} />
      </div>
      <div className="flex">
        <span className={`${p} bg-[#ff6b6b]`} />
        <span className={`${p} bg-[#ff8787]`} />
        <span className={`${p} bg-[#ff6b6b]`} />
      </div>
      <div className="flex justify-center">
        <span className={`${p} bg-[#ff6b6b]`} />
      </div>
    </div>
  )
}

const oceanStyles = `
.login-pixel-ocean .ocean-wave {
  image-rendering: pixelated;
  background-repeat: repeat-x;
  background-size: 64px 100%;
}
.login-pixel-ocean .ocean-wave--back {
  background-image: repeating-linear-gradient(90deg, #7ad4ee 0 8px, #5ec0dc 8px 16px, transparent 16px 24px, #6ecce6 24px 32px);
  animation: ocean-wave-drift 16s linear infinite;
}
.login-pixel-ocean .ocean-wave--mid {
  background-image: repeating-linear-gradient(90deg, #8ee0f4 0 8px, transparent 8px 16px, #6ecce6 16px 24px, #4eb8d4 24px 32px);
  animation: ocean-wave-drift 11s linear infinite reverse;
}
.login-pixel-ocean .ocean-wave--front {
  background-image: repeating-linear-gradient(90deg, #b8f0ff 0 8px, #e8f8ff 8px 12px, transparent 12px 20px, #a0e4f8 20px 32px);
  animation: ocean-wave-drift 7s linear infinite;
}
.login-pixel-ocean .ocean-wave--foam {
  background-image: repeating-linear-gradient(90deg, #fff 0 4px, transparent 4px 12px, rgba(255,255,255,0.6) 12px 16px);
  animation: ocean-wave-drift 5s linear infinite;
}
.login-pixel-ocean .ocean-caustics { animation: ocean-caustics-shift 20s ease-in-out infinite; }
.login-pixel-ocean .ocean-light-ray {
  background: linear-gradient(180deg, rgba(255,255,255,0.25) 0%, transparent 100%);
  clip-path: polygon(30% 0, 70% 0, 100% 100%, 0 100%);
  animation: ocean-light-ray 12s ease-in-out infinite;
}
.login-pixel-ocean .ocean-sun-shimmer { animation: ocean-shimmer 3s ease-in-out infinite; }
.login-pixel-ocean .ocean-bubble { animation: ocean-bubble-rise 6s ease-in infinite; opacity: 0; }
.login-pixel-ocean .ocean-seaweed-sway { animation: ocean-seaweed-sway 4s ease-in-out infinite; }
.login-pixel-ocean .ocean-fish-swim { animation: ocean-fish-swim 7s ease-in-out infinite; }
.login-pixel-ocean .ocean-jelly-drift { animation: ocean-jelly-drift 9s ease-in-out infinite; }
.login-pixel-ocean .ocean-seagull { animation: ocean-seagull-glide 14s ease-in-out infinite; }
.login-pixel-ocean .ocean-buoy-bob { animation: ocean-buoy-bob 4s ease-in-out infinite; }

@keyframes ocean-wave-drift {
  from { background-position-x: 0; }
  to { background-position-x: 64px; }
}
@keyframes ocean-caustics-shift {
  0%, 100% { transform: translate(0, 0); opacity: 0.22; }
  50% { transform: translate(16px, 8px); opacity: 0.38; }
}
@keyframes ocean-light-ray {
  0%, 100% { opacity: 0.12; transform: skewX(-6deg); }
  50% { opacity: 0.28; transform: skewX(6deg); }
}
@keyframes ocean-shimmer {
  0%, 100% { opacity: 0.4; transform: scaleY(1); }
  50% { opacity: 0.75; transform: scaleY(1.4); }
}
@keyframes ocean-bubble-rise {
  0% { transform: translateY(0) translateX(0); opacity: 0; }
  12% { opacity: 0.75; }
  80% { opacity: 0.3; }
  100% { transform: translateY(-140px) translateX(8px); opacity: 0; }
}
@keyframes ocean-seaweed-sway {
  0%, 100% { transform: skewX(0deg); }
  50% { transform: skewX(8deg); }
}
@keyframes ocean-fish-swim {
  0%, 100% { transform: translateX(0); }
  50% { transform: translateX(28px); }
}
@keyframes ocean-jelly-drift {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(12px, -16px); }
}
@keyframes ocean-seagull-glide {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(40px, -6px); }
}
@keyframes ocean-buoy-bob {
  0%, 100% { transform: translateY(0) rotate(-2deg); }
  50% { transform: translateY(4px) rotate(2deg); }
}
`
