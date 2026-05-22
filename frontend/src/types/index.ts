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
  /** true = categoria do usuário (inclui cópias de globais personalizadas). */
  isCustom: boolean
  active?: boolean
  globalId?: string | null
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

// Linha do breakdown por categoria no GET /reports/monthly
export interface CategoryBreakdown {
  categoryId: string
  name: string
  color: string
  total: number
  percentage: number
}

// Resposta do GET /reports/monthly (analise-tecnica.md §11)
// Espelha ReportDtos.MonthlyResponse no backend — qualquer rename aqui exige
// rename equivalente no record Java.
export interface MonthlyReport {
  month: string
  from: string
  to: string
  total: number
  breakdown: CategoryBreakdown[]
  expenses: Expense[]
}
