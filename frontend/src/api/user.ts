import apiClient from './client'
import type { User } from '../types'

// GET /me — perfil do usuário autenticado (analise-tecnica.md §11)
export async function getMe(): Promise<User> {
  const { data } = await apiClient.get<User>('/me')
  return data
}

// PATCH /me — atualiza o nome (telefone é imutável no backend)
export async function updateMe(body: { name: string }): Promise<User> {
  const { data } = await apiClient.patch<User>('/me', body)
  return data
}
