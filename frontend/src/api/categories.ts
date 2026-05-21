import apiClient from './client'
import type { Category } from '../types'

// GET /categories — globais + customizadas do usuário autenticado
export async function getCategories(): Promise<Category[]> {
  const { data } = await apiClient.get<Category[]>('/categories')
  return data
}

// POST /categories — cria categoria customizada (RF-07)
export async function createCategory(body: {
  name: string
  icon: string
  color: string
}): Promise<Category> {
  const { data } = await apiClient.post<Category>('/categories', body)
  return data
}

// PATCH /categories/:id — edita nome, ícone ou cor da categoria
export async function updateCategory(
  id: string,
  body: Partial<{ name: string; icon: string; color: string }>,
): Promise<Category> {
  const { data } = await apiClient.patch<Category>(`/categories/${id}`, body)
  return data
}

// DELETE /categories/:id — desativa categoria customizada (RF-07)
export async function deleteCategory(id: string): Promise<void> {
  await apiClient.delete(`/categories/${id}`)
}
