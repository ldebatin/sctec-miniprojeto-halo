import apiClient from './client'
import type { Category } from '../types'

export type CategoryPayload = {
  name: string
  icon: string
  color: string
}

// GET /categories — globais + customizadas do usuário autenticado
export async function getCategories(): Promise<Category[]> {
  const { data } = await apiClient.get<Category[]>('/categories')
  return data
}

// POST /categories — cria categoria customizada (RF-07)
export async function createCategory(body: CategoryPayload): Promise<Category> {
  const { data } = await apiClient.post<Category>('/categories', body)
  return data
}

// POST /categories/from-global/:id — cópia/override de global (RF-08)
export async function copyFromGlobal(
  globalId: string,
  body?: Partial<Pick<CategoryPayload, 'icon' | 'color'>>,
): Promise<Category> {
  const { data } = await apiClient.post<Category>(`/categories/from-global/${globalId}`, body ?? {})
  return data
}

// PATCH /categories/:id — edita nome, ícone ou cor
export async function updateCategory(
  id: string,
  body: CategoryPayload,
): Promise<Category> {
  const { data } = await apiClient.patch<Category>(`/categories/${id}`, body)
  return data
}

// DELETE /categories/:id — desativa categoria customizada (RF-07)
export async function deleteCategory(id: string): Promise<void> {
  await apiClient.delete(`/categories/${id}`)
}
