export interface User {
  id: string
  name: string
  phone: string
  createdAt: string
}

export interface Category {
  id: string
  name: string
  icon: string
  color: string
  global: boolean
}

export interface Expense {
  id: string
  description: string
  amount: number
  categoryId: string
  category?: Category
  occurredAt: string
  source: 'WHATSAPP' | 'WEB'
  rawMessage?: string
  createdAt: string
}

// Paginação padrão do Spring Data (analise-tecnica.md §11 — GET /expenses)
export interface ExpensePage {
  content: Expense[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// Resposta do GET /reports/monthly — espelha ReportDtos.MonthlyResponse do
// backend (T-039, analise-tecnica.md §11). Nomes alinhados ao record Java.
export interface MonthlyReport {
  month: string
  from: string
  to: string
  total: number
  breakdown: Array<{
    categoryId: string
    name: string
    color: string
    total: number
    percentage: number
  }>
  expenses: Expense[]
}
