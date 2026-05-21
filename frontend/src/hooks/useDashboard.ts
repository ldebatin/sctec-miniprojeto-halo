import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { getMonthlyReport } from '../api/reports'
import { getExpenses } from '../api/expenses'
import type { MonthlyReport, ExpensePage } from '../types'

// Encapsula as queries do dashboard com TanStack Query (analise-tecnica.md §12.3)
export function useDashboard(month: string): {
  report: MonthlyReport | undefined
  expenses: ExpensePage | undefined
  isLoading: boolean
  isError: boolean
  refetch: () => void
} {
  // Intervalo do mês para filtrar os lançamentos
  const from = dayjs(month).startOf('month').format('YYYY-MM-DD')
  const to   = dayjs(month).endOf('month').format('YYYY-MM-DD')

  const reportQuery = useQuery({
    queryKey: ['report', 'monthly', month],
    queryFn:  () => getMonthlyReport(month),
  })

  // Query secundária: lançamentos do mês ordenados por data desc (size 10)
  // Complementa o report.recentExpenses quando for necessário cache independente
  const expensesQuery = useQuery({
    queryKey: ['expenses', 'list', { from, to, size: 10 }],
    queryFn:  () => getExpenses({ from, to, size: 10, page: 0 }),
  })

  function refetch() {
    reportQuery.refetch()
    expensesQuery.refetch()
  }

  return {
    report:    reportQuery.data,
    expenses:  expensesQuery.data,
    isLoading: reportQuery.isLoading || expensesQuery.isLoading,
    isError:   reportQuery.isError   || expensesQuery.isError,
    refetch,
  }
}
