import { useState, useEffect, useRef } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { z } from 'zod'
import { Navigate, useNavigate } from 'react-router-dom'
import axios from 'axios'
import { requestOtp, verifyOtp } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const OTP_MAX_ATTEMPTS = 5
const RESEND_COOLDOWN_SECONDS = 60

// ─── Esquemas Zod ────────────────────────────────────────────────────────────
// Definidos como schemas de campo para reutilizar no validate do RHF sem o
// @hookform/resolvers (não incluído no package.json)

const phoneFieldSchema = z
  .string()
  .min(1, 'Telefone obrigatório')
  .refine(
    (val) => val.replace(/\D/g, '').length >= 10,
    'Telefone inválido (mínimo 10 dígitos)',
  )

const codeFieldSchema = z
  .string()
  .length(6, 'O código deve ter exatamente 6 dígitos')
  .regex(/^\d+$/, 'Apenas dígitos são permitidos')

const phoneSchema = z.object({ phone: phoneFieldSchema })
const otpSchema   = z.object({ code: codeFieldSchema })

type PhoneForm = z.infer<typeof phoneSchema>
type OtpForm   = z.infer<typeof otpSchema>

// ─── Utilitários ─────────────────────────────────────────────────────────────

// Aplica máscara (XX) XXXXX-XXXX enquanto o usuário digita
function applyPhoneMask(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 11)
  if (digits.length <= 2) return digits
  if (digits.length <= 7) return `(${digits.slice(0, 2)}) ${digits.slice(2)}`
  return `(${digits.slice(0, 2)}) ${digits.slice(2, 7)}-${digits.slice(7)}`
}

// Remove formatação e adiciona o DDI brasileiro se ausente (ex: 5548999999999)
function normalizePhone(raw: string): string {
  const digits = raw.replace(/\D/g, '')
  return digits.startsWith('55') ? digits : `55${digits}`
}

// Extrai a mensagem de erro da resposta da API ou retorna um fallback genérico
function extractApiError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const msg = error.response?.data?.message
    return typeof msg === 'string' ? msg : 'Erro ao conectar com o servidor'
  }
  return 'Erro inesperado. Tente novamente.'
}

// ─── Componente principal ─────────────────────────────────────────────────────

export default function LoginPage() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const setAuth         = useAuthStore((s) => s.setAuth)
  const navigate        = useNavigate()

  const [step, setStep]                     = useState<'phone' | 'otp'>('phone')
  const [normalizedPhone, setNormalizedPhone] = useState('')
  const [apiError, setApiError]             = useState<string | null>(null)
  const [cooldown, setCooldown]             = useState(0)
  const [otpAttempts, setOtpAttempts]       = useState(0)
  const intervalRef                         = useRef<ReturnType<typeof setInterval> | null>(null)

  // Limpa o intervalo ao desmontar para evitar setState em componente desmontado
  useEffect(() => () => clearInterval(intervalRef.current ?? undefined), [])

  // Inicia o countdown de N segundos — chame em vez de setCooldown diretamente
  function startCooldown(seconds = RESEND_COOLDOWN_SECONDS) {
    clearInterval(intervalRef.current ?? undefined)
    setCooldown(seconds)
    intervalRef.current = setInterval(() => {
      setCooldown((s) => {
        if (s <= 1) {
          clearInterval(intervalRef.current!)
          return 0
        }
        return s - 1
      })
    }, 1000)
  }

  // Dois formulários independentes — cada step tem seu próprio estado de validação
  const phoneForm = useForm<PhoneForm>({ defaultValues: { phone: '' } })
  const otpForm   = useForm<OtpForm>({ defaultValues: { code: '' } })

  // Se já estiver autenticado, não exibe o login
  if (isAuthenticated) return <Navigate to="/" replace />

  // ── Submit Step 1: solicita o OTP ──────────────────────────────────────────
  const onPhoneSubmit = phoneForm.handleSubmit(async ({ phone }) => {
    setApiError(null)
    const normalized = normalizePhone(phone)
    try {
      await requestOtp(normalized)
      setNormalizedPhone(normalized)
      setOtpAttempts(0)
      startCooldown()
      setStep('otp')
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 429) {
        setApiError('Aguarde 60 segundos antes de solicitar um novo código.')
      } else {
        setApiError(extractApiError(err))
      }
    }
  })

  // ── Submit Step 2: verifica o OTP e abre a sessão ─────────────────────────
  const onOtpSubmit = otpForm.handleSubmit(async ({ code }) => {
    setApiError(null)
    try {
      const { accessToken, user } = await verifyOtp(normalizedPhone, code)
      setAuth(accessToken, user)
      navigate('/', { replace: true })
    } catch (err) {
      setOtpAttempts((prev) => {
        const newAttempts = prev + 1
        const remaining = OTP_MAX_ATTEMPTS - newAttempts
        if (remaining <= 0) {
          setApiError('Código inválido. Número máximo de tentativas atingido. Solicite um novo código.')
        } else {
          setApiError(`Código inválido. ${remaining} tentativa${remaining === 1 ? '' : 's'} restante${remaining === 1 ? '' : 's'}.`)
        }
        return newAttempts
      })
    }
  })

  // ── Volta para o Step 1 ───────────────────────────────────────────────────
  async function handleResend() {
    setApiError(null)
    otpForm.reset()
    try {
      await requestOtp(normalizedPhone)
      setOtpAttempts(0)
      startCooldown()
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 429) {
        setApiError('Aguarde antes de solicitar um novo código.')
      } else {
        setApiError(extractApiError(err))
      }
    }
  }

  function handleBack() {
    clearInterval(intervalRef.current ?? undefined)
    setStep('phone')
    setApiError(null)
    setCooldown(0)
    setOtpAttempts(0)
    otpForm.reset()
  }

  return (
    <main className="min-h-screen flex flex-col justify-start pt-24 bg-white">

      {/* ── Logo — fora do container estreito para ocupar mais largura ── */}
      <div className="flex justify-center mb-6 px-4">
        <img
          src="/logo.png"
          alt="Halo — Controle de gastos pessoais via WhatsApp"
          className="w-full max-w-[560px] object-contain"
        />
      </div>

      <div className="max-w-sm mx-auto px-6 w-full">

        {step === 'phone' ? (

          /* ── Step 1: Telefone ─────────────────────────────────────────────── */
          <form onSubmit={onPhoneSubmit} noValidate>
            <p className="text-base font-medium text-gray-700 mb-4 text-center">
              Digite seu número de WhatsApp
            </p>

            <label htmlFor="phone" className="sr-only">
              Número de telefone
            </label>
            <Controller
              name="phone"
              control={phoneForm.control}
              rules={{
                validate: (value) => {
                  const result = phoneFieldSchema.safeParse(value)
                  if (result.success) return true
                  return result.error.errors[0]?.message ?? 'Telefone inválido'
                },
              }}
              render={({ field }) => (
                <input
                  {...field}
                  id="phone"
                  onChange={(e) => field.onChange(applyPhoneMask(e.target.value))}
                  type="tel"
                  inputMode="tel"
                  placeholder="(99) 99999-9999"
                  maxLength={15}
                  autoComplete="tel"
                  aria-invalid={!!phoneForm.formState.errors.phone}
                  className="border border-gray-200 rounded-lg p-3 w-full text-gray-900
                             placeholder-gray-400 focus:outline-none focus:ring-2
                             focus:ring-primary-500 focus:border-transparent"
                />
              )}
            />

            {phoneForm.formState.errors.phone && (
              <p role="alert" className="text-red-500 text-sm mt-1">
                {phoneForm.formState.errors.phone.message}
              </p>
            )}

            {apiError && (
              <p role="alert" className="text-red-500 text-sm mt-3">
                {apiError}
              </p>
            )}

            <button
              type="submit"
              disabled={phoneForm.formState.isSubmitting}
              className="mt-6 bg-primary-500 text-white rounded-lg py-3 w-full font-medium
                         hover:bg-primary-600 active:bg-primary-700 disabled:opacity-60
                         transition-colors"
            >
              {phoneForm.formState.isSubmitting ? 'Enviando...' : 'Enviar código'}
            </button>
          </form>

        ) : (

          /* ── Step 2: OTP ──────────────────────────────────────────────────── */
          <form onSubmit={onOtpSubmit} noValidate>
            <p className="text-base font-medium text-gray-700 text-center">
              Código enviado para o WhatsApp
            </p>
            <p className="text-sm text-gray-400 text-center mt-1 mb-6">
              Válido por 5 minutos
            </p>

            <label htmlFor="code" className="sr-only">
              Código de verificação
            </label>
            <Controller
              name="code"
              control={otpForm.control}
              rules={{
                validate: (value) => {
                  const result = codeFieldSchema.safeParse(value)
                  if (result.success) return true
                  return result.error.errors[0]?.message ?? 'Código inválido'
                },
              }}
              render={({ field }) => (
                <input
                  {...field}
                  id="code"
                  onChange={(e) => {
                    // Aceita apenas dígitos e limita a 6 caracteres
                    const digits = e.target.value.replace(/\D/g, '').slice(0, 6)
                    field.onChange(digits)
                  }}
                  type="text"
                  inputMode="numeric"
                  placeholder="000000"
                  maxLength={6}
                  autoComplete="one-time-code"
                  aria-invalid={!!otpForm.formState.errors.code}
                  className="border border-gray-200 rounded-lg p-3 w-full text-center
                             text-3xl tracking-[0.5em] font-mono text-gray-900
                             placeholder-gray-300 focus:outline-none focus:ring-2
                             focus:ring-primary-500 focus:border-transparent"
                />
              )}
            />

            {otpForm.formState.errors.code && (
              <p role="alert" className="text-red-500 text-sm mt-1 text-center">
                {otpForm.formState.errors.code.message}
              </p>
            )}

            {apiError && (
              <p role="alert" className="text-red-500 text-sm mt-3 text-center">
                {apiError}
              </p>
            )}

            <button
              type="submit"
              disabled={otpForm.formState.isSubmitting || otpAttempts >= OTP_MAX_ATTEMPTS}
              className="mt-6 bg-primary-500 text-white rounded-lg py-3 w-full font-medium
                         hover:bg-primary-600 active:bg-primary-700 disabled:opacity-60
                         transition-colors"
            >
              {otpForm.formState.isSubmitting ? 'Verificando...' : 'Entrar'}
            </button>

            <div className="flex flex-col items-center gap-2 mt-4">
              <button
                type="button"
                onClick={handleResend}
                disabled={cooldown > 0}
                className="text-sm font-medium disabled:text-gray-400 disabled:cursor-not-allowed
                           text-primary-500 hover:text-primary-600 transition-colors"
              >
                {cooldown > 0
                  ? `Reenviar código (${cooldown}s)`
                  : 'Reenviar código'}
              </button>
              <button
                type="button"
                onClick={handleBack}
                className="text-gray-400 text-xs hover:text-gray-600 transition-colors"
              >
                ← Trocar número
              </button>
            </div>
          </form>

        )}
      </div>
    </main>
  )
}
