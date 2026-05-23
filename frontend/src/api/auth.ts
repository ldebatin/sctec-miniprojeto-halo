import axios from 'axios'
import apiClient from './client'
import type { User } from '../types'

// POST /auth/otp/request — envia o código de 6 dígitos para o WhatsApp do usuário
// O telefone deve estar normalizado (ex: "5548999999999") antes de chamar esta função
export async function requestOtp(phone: string): Promise<void> {
  await apiClient.post('/auth/otp/request', { phone })
}

// POST /auth/otp/verify — valida o código e abre a sessão (RF-09).
// O backend retorna apenas { accessToken, expiresIn } — fazemos um GET /me em
// seguida pra obter o User completo, já com o token recém-emitido (analise-tecnica.md §7.3).
export async function verifyOtp(
  phone: string,
  code: string,
): Promise<{ accessToken: string; user: User }> {
  const { data } = await apiClient.post<{ accessToken: string; expiresIn: number }>(
    '/auth/otp/verify',
    { phone, code },
  )
  const user = await fetchMe(data.accessToken)
  return { accessToken: data.accessToken, user }
}

// POST /auth/refresh — usa o cookie httpOnly para tentar restaurar a sessão.
// Usa axios "cru" (sem o interceptor de refresh do apiClient) para evitar loop
// quando o refresh em si falha com 401.
export async function refreshSession(): Promise<{ accessToken: string }> {
  const { data } = await axios.post<{ accessToken: string; expiresIn: number }>(
    '/auth/refresh',
    {},
    {
      baseURL: import.meta.env.VITE_API_BASE_URL,
      withCredentials: true,
    },
  )
  return { accessToken: data.accessToken }
}

// GET /me — carrega o perfil do usuário autenticado. Aceita token explícito
// para uso em fluxos onde o store ainda não foi atualizado (login + bootstrap).
export async function fetchMe(token?: string): Promise<User> {
  const config = token ? { headers: { Authorization: `Bearer ${token}` } } : undefined
  const { data } = await apiClient.get<User>('/me', config)
  return data
}

// DELETE /auth/sessions/current — revoga o refresh token no backend e limpa o cookie (RF-11)
export async function logout(): Promise<void> {
  await apiClient.delete('/auth/sessions/current')
}
