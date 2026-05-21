import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getExpense, updateExpense } from '../api/expenses'
import type { Expense } from '../types'

export function useExpenseDetail(id: string): {
  expense: Expense | undefined
  isLoading: boolean
  isError: boolean
} {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['expense', id],
    queryFn: () => getExpense(id),
    enabled: !!id,
  })

  return { expense: data, isLoading, isError }
}

export function useUpdateExpense() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({
      id,
      body,
    }: {
      id: string
      body: Parameters<typeof updateExpense>[1]
    }) => updateExpense(id, body),
    onSuccess: (_, variables) => {
      // Invalida tanto o detalhe quanto a lista para manter cache consistente
      queryClient.invalidateQueries({ queryKey: ['expense', variables.id] })
      queryClient.invalidateQueries({ queryKey: ['expenses'] })
    },
  })
}
