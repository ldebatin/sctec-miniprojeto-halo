import axios from 'axios'
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

// DELETE /auth/sessions/current — revoga o refresh token no backend e limpa o cookie (RF-11)
export async function logout(): Promise<void> {
  await apiClient.delete('/auth/sessions/current')
}

// POST /auth/refresh — usado no boot do app para reidratar a sessão a partir
// do cookie httpOnly de refresh, evitando que F5 caia em /login mesmo com
// cookie válido. Usa axios bruto (sem apiClient) para não disparar o
// interceptor de 401, que também faz refresh e provocaria recursão.
export async function silentRefresh(): Promise<{ accessToken: string; user: User }> {
  const { data } = await axios.post<{ accessToken: string; user: User }>(
    '/auth/refresh',
    {},
    {
      baseURL: import.meta.env.VITE_API_BASE_URL,
      withCredentials: true,
    },
  )
  return data
}
