import { useState } from 'react'
import dayjs from 'dayjs'
import 'dayjs/locale/pt-br'
import { useAuthStore } from '../stores/auth'
import { useDashboard } from '../hooks/useDashboard'

dayjs.locale('pt-br')

// Formata valor em BRL
const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

// Rótulo de data relativo: Hoje, Ontem ou DD/MM
function dateLabel(iso: string): string {
  const d = dayjs(iso)
  if (d.isSame(dayjs(), 'day')) return 'Hoje'
  if (d.isSame(dayjs().subtract(1, 'day'), 'day')) return 'Ontem'
  return d.format('DD/MM')
}

// Mês capitalizado para o cabeçalho (ex.: "Maio 2025")
function monthLabel(month: string): string {
  const raw = dayjs(month).format('MMMM YYYY')
  return raw.charAt(0).toUpperCase() + raw.slice(1)
}

export default function DashboardPage() {
  const user = useAuthStore((s) => s.user)
  // mês corrente no formato YYYY-MM
  const [month] = useState(() => dayjs().format('YYYY-MM'))

  const { report, isLoading, isError, refetch } = useDashboard(month)

  return (
    <div className="min-h-screen bg-gray-50 pb-20">
      {/* Cabeçalho */}
      <header className="bg-white px-4 pt-10 pb-4 flex items-center justify-between">
        <div>
          <p className="text-sm text-gray-400">Olá,</p>
          <p className="text-lg font-semibold text-gray-800">
            {user?.name ?? '—'}
          </p>
          <p className="text-xs text-gray-400 mt-0.5">{monthLabel(month)}</p>
        </div>
        <button
          aria-label="Notificações"
          className="p-2 rounded-full text-gray-400 hover:text-gray-600 transition-colors"
        >
          <BellIcon />
        </button>
      </header>

      <main className="px-4 pt-2 space-y-6">
        {/* Card total do mês */}
        {isLoading ? (
          <div className="animate-pulse bg-gray-100 rounded-2xl h-28" />
        ) : isError ? (
          <ErrorCard onRetry={refetch} />
        ) : (
          <div className="bg-primary-500 rounded-2xl p-4 text-white">
            <p className="text-sm opacity-80">Total gasto no mês</p>
            <p className="text-3xl font-bold mt-1">
              {brl.format(report?.total ?? 0)}
            </p>
            <p className="text-xs opacity-70 mt-1">{monthLabel(month)}</p>
          </div>
        )}

        {/* Seção por categoria */}
        <section>
          <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-3">
            Por categoria
          </h2>

          {isLoading ? (
            <SkeletonList rows={4} />
          ) : !report?.byCategory.length ? (
            <EmptyState message="Nenhum gasto registrado ainda." />
          ) : (
            <ul className="space-y-3">
              {report.byCategory.slice(0, 5).map((cat) => (
                <li key={cat.categoryId} className="bg-white rounded-xl p-3 shadow-sm">
                  <div className="flex justify-between items-center mb-1.5">
                    <span className="text-sm font-medium text-gray-700">
                      {cat.categoryName}
                    </span>
                    <span className="text-sm font-semibold text-gray-800">
                      {brl.format(cat.total)}
                    </span>
                  </div>
                  <div className="bg-gray-100 h-1.5 rounded-full overflow-hidden">
                    <div
                      className="h-1.5 rounded-full"
                      style={{
                        width: `${Math.min(cat.percentage, 100)}%`,
                        backgroundColor: cat.color,
                      }}
                    />
                  </div>
                  <p className="text-xs text-gray-400 mt-1 text-right">
                    {cat.percentage.toFixed(1)}%
                  </p>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* Seção últimos lançamentos */}
        <section>
          <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-3">
            Últimos lançamentos
          </h2>

          {isLoading ? (
            <SkeletonList rows={3} />
          ) : !report?.recentExpenses.length ? (
            <EmptyState message="Nenhum lançamento encontrado." />
          ) : (
            <ul className="space-y-2">
              {report.recentExpenses.map((expense) => (
                <li
                  key={expense.id}
                  className="bg-white rounded-xl p-3 shadow-sm flex items-center gap-3"
                >
                  {/* Círculo colorido da categoria */}
                  <div
                    className="w-9 h-9 rounded-full flex-shrink-0"
                    style={{ backgroundColor: expense.category?.color ?? '#e5e7eb' }}
                    aria-hidden="true"
                  />

                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-800 truncate">
                      {expense.description}
                    </p>
                    <p className="text-xs text-gray-400">
                      {expense.category?.name ?? 'Sem categoria'} ·{' '}
                      {dateLabel(expense.occurredAt)}
                    </p>
                  </div>

                  <span className="text-sm font-semibold text-gray-800 flex-shrink-0">
                    {brl.format(expense.amount)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </main>
    </div>
  )
}

// ─── Componentes auxiliares ────────────────────────────────────────────────────

function SkeletonList({ rows }: { rows: number }) {
  return (
    <ul className="space-y-3">
      {Array.from({ length: rows }).map((_, i) => (
        <li key={i} className="animate-pulse bg-gray-100 rounded-xl h-14" />
      ))}
    </ul>
  )
}

function EmptyState({ message }: { message: string }) {
  return (
    <p className="text-sm text-gray-400 text-center py-6">{message}</p>
  )
}

function ErrorCard({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="bg-red-50 border border-red-100 rounded-2xl p-4 text-center">
      <p className="text-sm text-red-600 mb-3">
        Erro ao carregar os dados. Verifique sua conexão.
      </p>
      <button
        onClick={onRetry}
        className="text-sm font-medium text-red-600 underline"
      >
        Tentar novamente
      </button>
    </div>
  )
}

// ─── Ícone inline ──────────────────────────────────────────────────────────────

function BellIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 24 24"
      strokeWidth={1.5}
      stroke="currentColor"
      className="w-6 h-6"
      aria-hidden="true"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M14.857 17.082a23.848 23.848 0 005.454-1.31A8.967 8.967 0 0118 9.75v-.7V9A6 6 0 006 9v.75a8.967 8.967 0 01-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 01-5.714 0m5.714 0a3 3 0 11-5.714 0"
      />
    </svg>
  )
}
