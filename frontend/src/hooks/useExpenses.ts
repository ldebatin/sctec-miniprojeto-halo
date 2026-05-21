import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { getExpenses, deleteExpense } from '../api/expenses'
import type { ExpensePage } from '../types'

interface ExpenseFilters {
  from?: string
  to?: string
  categoryId?: string
  page?: number
  size?: number
}

export function useExpenses(filters: ExpenseFilters): {
  data: ExpensePage | undefined
  isLoading: boolean
  isError: boolean
  refetch: () => void
} {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['expenses', 'list', filters],
    queryFn: () => getExpenses(filters),
  })

  return { data, isLoading, isError, refetch }
}

export function useDeleteExpense() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  return useMutation({
    mutationFn: (id: string) => deleteExpense(id),
    onSuccess: () => {
      // Invalida todas as queries de expenses para refletir a exclusão
      queryClient.invalidateQueries({ queryKey: ['expenses'] })
      navigate('/lancamentos')
    },
  })
}
