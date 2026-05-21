import apiClient from './client'
import type { Expense, ExpensePage } from '../types'

// GET /expenses — lista paginada com filtros opcionais (analise-tecnica.md §11)
export async function getExpenses(params: {
  from?: string
  to?: string
  categoryId?: string
  page?: number
  size?: number
}): Promise<ExpensePage> {
  const { data } = await apiClient.get<ExpensePage>('/expenses', { params })
  return data
}

// GET /expenses/:id — detalhe de um lançamento
export async function getExpense(id: string): Promise<Expense> {
  const { data } = await apiClient.get<Expense>(`/expenses/${id}`)
  return data
}

// POST /expenses — criação manual via web (source = WEB no backend)
export async function createExpense(body: {
  description: string
  amount: number
  categoryId: string
  occurredAt: string
}): Promise<Expense> {
  const { data } = await apiClient.post<Expense>('/expenses', body)
  return data
}

// PATCH /expenses/:id — edição parcial de um lançamento
export async function updateExpense(
  id: string,
  body: Partial<{
    description: string
    amount: number
    categoryId: string
    occurredAt: string
  }>,
): Promise<Expense> {
  const { data } = await apiClient.patch<Expense>(`/expenses/${id}`, body)
  return data
}

// DELETE /expenses/:id — soft delete (backend seta deleted_at, não remove do banco)
export async function deleteExpense(id: string): Promise<void> {
  await apiClient.delete(`/expenses/${id}`)
}
