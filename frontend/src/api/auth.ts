import apiClient from './client'
import type { User } from '../types'

// POST /auth/otp/request — envia o código de 6 dígitos para o WhatsApp do usuário
// O telefone deve estar normalizado (ex: "5548999999999") antes de chamar esta função
export async function requestOtp(phone: string): Promise<void> {
  await apiClient.post('/auth/otp/request', { phone })
}

// POST /auth/otp/verify — valida o código e obtém tokens de sessão
// Backend emite o access token no body e o refresh token em cookie httpOnly (analise-tecnica.md §7.3)
export async function verifyOtp(
  phone: string,
  code: string,
): Promise<{ accessToken: string; user: User }> {
  const { data } = await apiClient.post<{ accessToken: string; user: User }>(
    '/auth/otp/verify',
    { phone, code },
  )
  return data
}
