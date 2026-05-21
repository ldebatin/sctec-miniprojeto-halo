import { useState, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useForm, Controller } from 'react-hook-form'
import { z } from 'zod'
import dayjs from 'dayjs'
import 'dayjs/locale/pt-br'
import { useExpenses } from '../hooks/useExpenses'
import { getCategories, createCategory } from '../api/categories'
import { createExpense } from '../api/expenses'
import { useQueryClient } from '@tanstack/react-query'
import type { Category } from '../types'

dayjs.locale('pt-br')

const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

// Rótulo de data relativo
function dateLabel(iso: string): string {
  const d = dayjs(iso)
  if (d.isSame(dayjs(), 'day')) return 'Hoje'
  if (d.isSame(dayjs().subtract(1, 'day'), 'day')) return 'Ontem'
  return d.format('DD/MM')
}

// ─── Esquema Zod para novo lançamento ────────────────────────────────────────

const novoSchema = z.object({
  description: z.string().min(1, 'Descrição obrigatória'),
  amount: z
    .string()
    .min(1, 'Valor obrigatório')
    .refine((v) => parseFloat(v.replace(',', '.')) > 0, 'Valor deve ser maior que zero'),
  categoryId: z.string().min(1, 'Categoria obrigatória'),
  occurredAt: z.string().min(1, 'Data obrigatória'),
})

type NovoForm = z.infer<typeof novoSchema>

// ─── Componente principal ─────────────────────────────────────────────────────

export default function ExpensesPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  // Filtros de API
  const [categoryId, setCategoryId] = useState<string | undefined>(undefined)
  const [size, setSize] = useState(10)

  // Filtro local por texto
  const [search, setSearch] = useState('')

  // Controle do modal de novo lançamento
  const [showModal, setShowModal] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const { data, isLoading, isError, refetch } = useExpenses({ categoryId, page: 0, size })

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
  })

  // Filtra localmente por texto de busca
  const filteredExpenses = useMemo(() => {
    const items = data?.content ?? []
    if (!search.trim()) return items
    const lower = search.toLowerCase()
    return items.filter((e) => e.description.toLowerCase().includes(lower))
  }, [data?.content, search])

  const hasMore = (data?.totalElements ?? 0) > (data?.content.length ?? 0)

  function handleCategoryChip(id: string | undefined) {
    setCategoryId(id)
    setSize(10) // reseta paginação ao trocar filtro
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-20">
      {/* Cabeçalho */}
      <header className="bg-white px-4 pt-10 pb-3 flex items-center justify-between mb-4">
        <h1 className="text-lg font-semibold text-gray-800">Lançamentos</h1>
        <button
          onClick={() => setShowModal(true)}
          aria-label="Novo lançamento"
          className="w-9 h-9 rounded-full bg-primary-500 text-white flex items-center justify-center shadow"
        >
          <PlusIcon />
        </button>
      </header>

      <div className="px-4 space-y-3">
        {/* Busca */}
        <input
          type="search"
          placeholder="Buscar por descrição…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm
                     placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-primary-500"
        />

        {/* Chips de categoria */}
        {categories && categories.length > 0 && (
          <div className="flex gap-2 overflow-x-auto pb-1 scrollbar-none">
            <CategoryChip
              label="Todos"
              active={!categoryId}
              onClick={() => handleCategoryChip(undefined)}
            />
            {categories.map((cat) => (
              <CategoryChip
                key={cat.id}
                label={cat.name}
                active={categoryId === cat.id}
                color={cat.color}
                onClick={() => handleCategoryChip(cat.id)}
              />
            ))}
          </div>
        )}
      </div>

      {/* Lista */}
      <div className="px-4 mt-4">
        {isLoading ? (
          <SkeletonList />
        ) : isError ? (
          <ErrorState onRetry={refetch} />
        ) : filteredExpenses.length === 0 ? (
          <p className="text-sm text-gray-400 text-center py-10">
            Nenhum lançamento encontrado.
          </p>
        ) : (
          <>
            <ul className="bg-white rounded-xl shadow-sm overflow-hidden divide-y divide-gray-50">
              {filteredExpenses.map((expense) => (
                <li key={expense.id}>
                  <button
                    onClick={() => navigate(`/lancamentos/${expense.id}`)}
                    className="flex items-center gap-3 p-3 border-b border-gray-50 w-full text-left hover:bg-gray-50 transition-colors"
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

                    <div className="flex flex-col items-end gap-1 flex-shrink-0">
                      <span className="text-sm font-semibold text-gray-800">
                        {brl.format(expense.amount / 100)}
                      </span>
                      <span className="text-xs px-1.5 py-0.5 rounded bg-gray-100 text-gray-500">
                        {expense.source === 'WHATSAPP' ? 'WhatsApp' : 'Web'}
                      </span>
                    </div>
                  </button>
                </li>
              ))}
            </ul>

            {/* Carregar mais */}
            {hasMore && (
              <button
                onClick={() => setSize((s) => s + 10)}
                className="mt-4 w-full py-2 text-sm text-primary-500 font-medium border border-primary-200
                           rounded-xl bg-white hover:bg-primary-50 transition-colors"
              >
                Carregar mais
              </button>
            )}
          </>
        )}
      </div>

      {/* Modal de novo lançamento */}
      {showModal && (
        <NovoLancamentoModal
          categories={categories ?? []}
          onClose={() => {
            setShowModal(false)
            setSubmitError(null)
          }}
          onSuccess={() => {
            setShowModal(false)
            setSubmitError(null)
            queryClient.invalidateQueries({ queryKey: ['expenses'] })
          }}
          submitError={submitError}
          setSubmitError={setSubmitError}
        />
      )}
    </div>
  )
}

// ─── Modal de novo lançamento ─────────────────────────────────────────────────

function NovoLancamentoModal({
  categories,
  onClose,
  onSuccess,
  submitError,
  setSubmitError,
}: {
  categories: Category[]
  onClose: () => void
  onSuccess: () => void
  submitError: string | null
  setSubmitError: (e: string | null) => void
}) {
  const { control, register, handleSubmit, formState: { errors, isSubmitting } } = useForm<NovoForm>({
    defaultValues: {
      description: '',
      amount: '',
      categoryId: '',
      occurredAt: dayjs().format('YYYY-MM-DD'),
    },
  })

  async function onSubmit(values: NovoForm) {
    try {
      setSubmitError(null)
      await createExpense({
        description: values.description,
        amount: Math.round(parseFloat(values.amount.replace(',', '.')) * 100),
        categoryId: values.categoryId,
        occurredAt: values.occurredAt,
      })
      onSuccess()
    } catch (err: unknown) {
      import('axios').then(({ default: axios }) => {
        if (axios.isAxiosError(err)) {
          setSubmitError(
            (err.response?.data as { message?: string })?.message ??
              'Erro ao salvar lançamento.',
          )
        } else {
          setSubmitError('Erro ao salvar lançamento.')
        }
      })
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center">
      {/* Overlay */}
      <div
        className="absolute inset-0 bg-black/40"
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Painel */}
      <div className="relative w-full max-w-lg bg-white rounded-t-2xl p-5 pb-8 shadow-xl">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-base font-semibold text-gray-800">Novo lançamento</h2>
          <button onClick={onClose} aria-label="Fechar" className="text-gray-400 hover:text-gray-600">
            <CloseIcon />
          </button>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-3">
          {/* Descrição */}
          <div>
            <label className="text-xs font-medium text-gray-600">Descrição</label>
            <input
              type="text"
              className="mt-1 w-full rounded-xl border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
              {...register('description', {
                validate: (v) => {
                  const r = z.string().min(1, 'Descrição obrigatória').safeParse(v)
                  return r.success ? true : (r.error.errors[0]?.message ?? 'Inválido')
                },
              })}
            />
            {errors.description && (
              <p className="text-xs text-red-500 mt-0.5">{errors.description.message}</p>
            )}
          </div>

          {/* Valor */}
          <div>
            <label className="text-xs font-medium text-gray-600">Valor (R$)</label>
            <Controller
              name="amount"
              control={control}
              rules={{
                validate: (v) => {
                  const num = parseFloat(String(v).replace(',', '.'))
                  if (!v || isNaN(num)) return 'Valor obrigatório'
                  if (num <= 0) return 'Valor deve ser maior que zero'
                  return true
                },
              }}
              render={({ field }) => (
                <input
                  type="text"
                  inputMode="decimal"
                  placeholder="0,00"
                  className="mt-1 w-full rounded-xl border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
                  value={field.value}
                  onChange={(e) => {
                    // Permite apenas dígitos, vírgula e ponto
                    const filtered = e.target.value.replace(/[^0-9,.]/g, '')
                    field.onChange(filtered)
                  }}
                />
              )}
            />
            {errors.amount && (
              <p className="text-xs text-red-500 mt-0.5">{errors.amount.message}</p>
            )}
          </div>

          {/* Categoria */}
          <div>
            <label className="text-xs font-medium text-gray-600">Categoria</label>
            <select
              className="mt-1 w-full rounded-xl border border-gray-200 px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary-500"
              {...register('categoryId', {
                validate: (v) => (v ? true : 'Categoria obrigatória'),
              })}
            >
              <option value="">Selecione…</option>
              {categories.map((cat) => (
                <option key={cat.id} value={cat.id}>
                  {cat.name}
                </option>
              ))}
            </select>
            {errors.categoryId && (
              <p className="text-xs text-red-500 mt-0.5">{errors.categoryId.message}</p>
            )}
          </div>

          {/* Data */}
          <div>
            <label className="text-xs font-medium text-gray-600">Data</label>
            <input
              type="date"
              className="mt-1 w-full rounded-xl border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
              {...register('occurredAt', {
                validate: (v) => (v ? true : 'Data obrigatória'),
              })}
            />
            {errors.occurredAt && (
              <p className="text-xs text-red-500 mt-0.5">{errors.occurredAt.message}</p>
            )}
          </div>

          {submitError && (
            <p className="text-xs text-red-500">{submitError}</p>
          )}

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full rounded-xl bg-primary-500 py-2.5 text-sm font-semibold text-white
                       disabled:opacity-60 hover:bg-primary-600 transition-colors"
          >
            {isSubmitting ? 'Salvando…' : 'Salvar lançamento'}
          </button>
        </form>
      </div>
    </div>
  )
}

// ─── Componentes auxiliares ────────────────────────────────────────────────────

function CategoryChip({
  label,
  active,
  color,
  onClick,
}: {
  label: string
  active: boolean
  color?: string
  onClick: () => void
}) {
  return (
    <button
      onClick={onClick}
      className={`px-3 py-1 rounded-full text-xs border flex-shrink-0 transition-colors ${
        active
          ? 'bg-primary-500 text-white border-primary-500'
          : 'bg-white text-gray-600 border-gray-200'
      }`}
      style={active && color ? { backgroundColor: color, borderColor: color } : undefined}
    >
      {label}
    </button>
  )
}

function SkeletonList() {
  return (
    <ul className="space-y-2">
      {Array.from({ length: 5 }).map((_, i) => (
        <li key={i} className="animate-pulse bg-gray-100 rounded-xl h-16" />
      ))}
    </ul>
  )
}

function ErrorState({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="text-center py-10">
      <p className="text-sm text-gray-500 mb-3">Erro ao carregar lançamentos.</p>
      <button onClick={onRetry} className="text-sm text-primary-500 underline">
        Tentar novamente
      </button>
    </div>
  )
}

// ─── Ícones inline ─────────────────────────────────────────────────────────────

function PlusIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 24 24"
      strokeWidth={2}
      stroke="currentColor"
      className="w-5 h-5"
      aria-hidden="true"
    >
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
    </svg>
  )
}

function CloseIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 24 24"
      strokeWidth={1.5}
      stroke="currentColor"
      className="w-5 h-5"
      aria-hidden="true"
    >
      <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
    </svg>
  )
}
