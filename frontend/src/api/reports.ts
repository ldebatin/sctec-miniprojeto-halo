import apiClient from './client'
import type { MonthlyReport } from '../types'

// GET /reports/monthly?month=YYYY-MM — resumo do mês para o dashboard (RF-12)
export async function getMonthlyReport(month: string): Promise<MonthlyReport> {
  const { data } = await apiClient.get<MonthlyReport>('/reports/monthly', {
    params: { month },
  })
  return data
}
