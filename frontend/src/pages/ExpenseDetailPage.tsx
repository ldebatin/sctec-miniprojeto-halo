import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import dayjs from 'dayjs'
import 'dayjs/locale/pt-br'
import { useExpenseDetail, useUpdateExpense } from '../hooks/useExpenseDetail'
import { useDeleteExpense } from '../hooks/useExpenses'
import { getCategories } from '../api/categories'
import axios from 'axios'

dayjs.locale('pt-br')

// ─── Esquema de validação ─────────────────────────────────────────────────────

const schema = z.object({
  description: z.string().min(1, 'Descrição obrigatória'),
  amountReais: z
    .number({ invalid_type_error: 'Valor obrigatório' })
    .positive('Valor deve ser maior que zero'),
  categoryId: z.string().min(1, 'Categoria obrigatória'),
  occurredAt: z.string().min(1, 'Data obrigatória'),
})

type DetailForm = z.infer<typeof schema>

// ─── Componente principal ─────────────────────────────────────────────────────

export default function ExpenseDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const [confirmDelete, setConfirmDelete] = useState(false)
  const [apiError, setApiError] = useState<string | null>(null)

  const { expense, isLoading, isError } = useExpenseDetail(id ?? '')
  const updateMutation = useUpdateExpense()
  const deleteMutation = useDeleteExpense()

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
  })

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<DetailForm>({
    defaultValues: {
      description: '',
      amountReais: 0,
      categoryId: '',
      occurredAt: '',
    },
  })

  // Popula o formulário quando o lançamento é carregado
  useEffect(() => {
    if (expense) {
      reset({
        description: expense.description,
        amountReais: expense.amount / 100, // centavos → reais para exibição
        categoryId: expense.categoryId,
        occurredAt: dayjs(expense.occurredAt).format('YYYY-MM-DD'),
      })
    }
  }, [expense, reset])

  async function onSubmit(values: DetailForm) {
    if (!id) return
    try {
      setApiError(null)
      await updateMutation.mutateAsync({
        id,
        body: {
          description: values.description,
          amount: Math.round(values.amountReais * 100), // reais → centavos para a API
          categoryId: values.categoryId,
          occurredAt: values.occurredAt,
        },
      })
      navigate('/lancamentos')
    } catch (err: unknown) {
      if (axios.isAxiosError(err)) {
        setApiError(
          (err.response?.data as { message?: string })?.message ??
            'Erro ao salvar alterações.',
        )
      } else {
        setApiError('Erro ao salvar alterações.')
      }
    }
  }

  async function handleDelete() {
    if (!id) return
    try {
      await deleteMutation.mutateAsync(id)
      // useDeleteExpense já navega para /lancamentos no onSuccess
    } catch {
      setApiError('Erro ao excluir lançamento.')
    }
  }

  // ── Skeleton de carregamento ──
  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 pb-20">
        <PageHeader onBack={() => navigate('/lancamentos')} />
        <div className="px-4 mt-6 space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="animate-pulse bg-gray-100 rounded-xl h-12" />
          ))}
        </div>
      </div>
    )
  }

  if (isError || !expense) {
    return (
      <div className="min-h-screen bg-gray-50 flex flex-col items-center justify-center pb-20">
        <p className="text-sm text-gray-500 mb-4">Lançamento não encontrado.</p>
        <button
          onClick={() => navigate('/lancamentos')}
          className="text-sm text-primary-500 underline"
        >
          Voltar
        </button>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-20">
      {/* Cabeçalho com voltar + título + lixeira */}
      <header className="bg-white px-4 pt-10 pb-3 flex items-center justify-between mb-4">
        <button
          onClick={() => navigate('/lancamentos')}
          aria-label="Voltar"
          className="p-1 -ml-1 text-gray-500 hover:text-gray-700 transition-colors"
        >
          <ArrowLeftIcon />
        </button>
        <h1 className="text-base font-semibold text-gray-800">Lançamento</h1>
        <button
          onClick={() => setConfirmDelete(true)}
          aria-label="Excluir lançamento"
          className="p-1 text-red-400 hover:text-red-600 transition-colors"
        >
          <TrashIcon />
        </button>
      </header>

      <div className="px-4 space-y-4">
        {/* Confirmação de exclusão inline */}
        {confirmDelete && (
          <div className="bg-red-50 border border-red-100 rounded-lg p-3">
            <p className="text-sm font-medium text-red-700 mb-2">Confirmar exclusão?</p>
            <p className="text-xs text-red-500 mb-3">
              O lançamento será removido e não aparecerá mais nos relatórios.
            </p>
            <div className="flex gap-2">
              <button
                onClick={() => setConfirmDelete(false)}
                className="flex-1 py-1.5 text-sm rounded-lg border border-gray-200 bg-white text-gray-600"
              >
                Cancelar
              </button>
              <button
                onClick={handleDelete}
                disabled={deleteMutation.isPending}
                className="flex-1 py-1.5 text-sm rounded-lg bg-red-500 text-white font-medium disabled:opacity-60"
              >
                {deleteMutation.isPending ? 'Excluindo…' : 'Excluir'}
              </button>
            </div>
          </div>
        )}

        {/* Badge de origem */}
        <div className="flex items-center gap-2">
          <span
            className="text-xs px-1.5 py-0.5 rounded bg-gray-100 text-gray-500"
          >
            {expense.source === 'WHATSAPP' ? 'WhatsApp' : 'Web'}
          </span>
          <span className="text-xs text-gray-400">
            Registrado em {dayjs(expense.createdAt).format('DD/MM/YYYY [às] HH:mm')}
          </span>
        </div>

        {/* Mensagem original do WhatsApp (RF-10) */}
        {expense.source === 'WHATSAPP' && expense.rawMessage && (
          <div className="bg-gray-50 rounded-xl px-3 py-2">
            <p className="text-xs font-medium text-gray-500 mb-0.5">Mensagem original</p>
            <p className="text-sm text-gray-700 italic">"{expense.rawMessage}"</p>
          </div>
        )}

        {/* Formulário de edição */}
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
          {/* Descrição */}
          <div>
            <label className="text-xs font-medium text-gray-600">Descrição</label>
            <input
              type="text"
              className="mt-1 w-full rounded-xl border border-gray-200 bg-white px-3 py-2.5 text-sm
                         focus:outline-none focus:ring-2 focus:ring-primary-500"
              {...register('description', {
                validate: (v) => {
                  const r = schema.shape.description.safeParse(v)
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
            <input
              type="number"
              step="0.01"
              min="0.01"
              className="mt-1 w-full rounded-xl border border-gray-200 bg-white px-3 py-2.5 text-sm
                         focus:outline-none focus:ring-2 focus:ring-primary-500"
              {...register('amountReais', {
                valueAsNumber: true,
                validate: (v) => {
                  const r = schema.shape.amountReais.safeParse(v)
                  return r.success ? true : (r.error.errors[0]?.message ?? 'Inválido')
                },
              })}
            />
            {errors.amountReais && (
              <p className="text-xs text-red-500 mt-0.5">{errors.amountReais.message}</p>
            )}
          </div>

          {/* Categoria */}
          <div>
            <label className="text-xs font-medium text-gray-600">Categoria</label>
            <select
              className="mt-1 w-full rounded-xl border border-gray-200 bg-white px-3 py-2.5 text-sm
                         focus:outline-none focus:ring-2 focus:ring-primary-500"
              {...register('categoryId', {
                validate: (v) => (v ? true : 'Categoria obrigatória'),
              })}
            >
              <option value="">Selecione…</option>
              {(categories ?? []).map((cat) => (
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
              className="mt-1 w-full rounded-xl border border-gray-200 bg-white px-3 py-2.5 text-sm
                         focus:outline-none focus:ring-2 focus:ring-primary-500"
              {...register('occurredAt', {
                validate: (v) => (v ? true : 'Data obrigatória'),
              })}
            />
            {errors.occurredAt && (
              <p className="text-xs text-red-500 mt-0.5">{errors.occurredAt.message}</p>
            )}
          </div>

          {apiError && <p className="text-xs text-red-500">{apiError}</p>}

          <button
            type="submit"
            disabled={isSubmitting || !isDirty}
            className="w-full rounded-xl bg-primary-500 py-3 text-sm font-semibold text-white
                       disabled:opacity-60 hover:bg-primary-600 transition-colors"
          >
            {isSubmitting ? 'Salvando…' : 'Salvar alterações'}
          </button>
        </form>
      </div>
    </div>
  )
}

// ─── Componentes auxiliares ────────────────────────────────────────────────────

function PageHeader({ onBack }: { onBack: () => void }) {
  return (
    <header className="bg-white px-4 pt-10 pb-3 flex items-center justify-between mb-4">
      <button onClick={onBack} aria-label="Voltar" className="p-1 -ml-1 text-gray-500">
        <ArrowLeftIcon />
      </button>
      <h1 className="text-base font-semibold text-gray-800">Lançamento</h1>
      <div className="w-8" aria-hidden="true" />
    </header>
  )
}

// ─── Ícones inline ─────────────────────────────────────────────────────────────

function ArrowLeftIcon() {
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
      <path strokeLinecap="round" strokeLinejoin="round" d="M10.5 19.5L3 12m0 0l7.5-7.5M3 12h18" />
    </svg>
  )
}

function TrashIcon() {
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
        d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"
      />
    </svg>
  )
}
