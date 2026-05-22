import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useProfile, useUpdateProfile } from '../hooks/useProfile'
import { useAuthStore } from '../stores/auth'
import { getCategories, createCategory, deleteCategory } from '../api/categories'
import { logout as logoutApi } from '../api/auth'
import axios from 'axios'

// ─── Utilitários ──────────────────────────────────────────────────────────────

// Pega as duas primeiras iniciais do nome em maiúsculo
function getInitials(name: string): string {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join('')
}

// Formata número de telefone armazenado (E.164 com 55) para exibição
function formatPhone(phone: string): string {
  const digits = phone.replace(/\D/g, '')
  const local = digits.startsWith('55') ? digits.slice(2) : digits
  if (local.length === 11) {
    return `(${local.slice(0, 2)}) ${local.slice(2, 7)}-${local.slice(7)}`
  }
  if (local.length === 10) {
    return `(${local.slice(0, 2)}) ${local.slice(2, 6)}-${local.slice(6)}`
  }
  return phone
}

function extractApiError(err: unknown): string {
  if (axios.isAxiosError(err)) {
    return (
      (err.response?.data as { message?: string })?.message ?? 'Erro na requisição.'
    )
  }
  return 'Erro desconhecido.'
}

// ─── Componente principal ─────────────────────────────────────────────────────

export default function ProfilePage() {
  const navigate = useNavigate()
  const clearAuth = useAuthStore((s) => s.clearAuth)
  const [isLoggingOut, setIsLoggingOut] = useState(false)

  const { user, isLoading, isError } = useProfile()

  if (isLoading) return <ProfileSkeleton />
  if (isError || !user) return <ProfileError />

  async function handleLogout() {
    if (isLoggingOut) return
    setIsLoggingOut(true)
    try {
      await logoutApi()
    } catch {
      // ignora erros de rede — o objetivo é sempre deslogar o cliente
    }
    clearAuth()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-20">
      {/* Cabeçalho */}
      <header className="bg-white px-4 pt-10 pb-4 mb-3">
        <h1 className="text-lg font-semibold text-gray-800">Perfil</h1>
      </header>

      <div className="px-4 space-y-3">
        {/* Avatar + nome + telefone */}
        <div className="bg-gray-50 rounded-xl p-4 mb-3 flex flex-col items-center gap-2">
          <div
            className="w-16 h-16 rounded-full bg-primary-100 flex items-center justify-center"
            aria-hidden="true"
          >
            <span className="text-xl font-bold text-primary-700">
              {getInitials(user.name)}
            </span>
          </div>
          <p className="text-base font-semibold text-gray-800">{user.name}</p>
          <p className="text-sm text-gray-400">{formatPhone(user.phone)}</p>
        </div>

        {/* Edição de nome */}
        <NameEditor initialName={user.name} />

        {/* Categorias customizadas */}
        <CustomCategories />

        {/* Botão de logout */}
        <button
          onClick={handleLogout}
          disabled={isLoggingOut}
          className="border border-red-200 text-red-500 rounded-lg py-3 w-full text-sm font-medium
                     hover:bg-red-50 disabled:opacity-60 transition-colors"
        >
          {isLoggingOut ? 'Saindo…' : 'Sair da conta'}
        </button>
      </div>
    </div>
  )
}

// ─── Seção de edição de nome ──────────────────────────────────────────────────

function NameEditor({ initialName }: { initialName: string }) {
  const [name, setName] = useState(initialName)
  const [success, setSuccess] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const mutation = useUpdateProfile()

  // Sincroniza quando o dado muda (ex: refetch em background)
  useEffect(() => {
    setName(initialName)
  }, [initialName])

  async function handleSave() {
    const trimmed = name.trim()
    if (!trimmed) return
    setSuccess(false)
    setError(null)
    try {
      await mutation.mutateAsync({ name: trimmed })
      setSuccess(true)
    } catch (err) {
      setError(extractApiError(err))
    }
  }

  return (
    <div className="bg-gray-50 rounded-xl p-4 mb-3">
      <h2 className="text-sm font-semibold text-gray-700 mb-2">Editar nome</h2>
      <input
        type="text"
        value={name}
        onChange={(e) => {
          setName(e.target.value)
          setSuccess(false)
        }}
        className="w-full rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm
                   focus:outline-none focus:ring-2 focus:ring-primary-500"
        placeholder="Seu nome"
      />
      {success && (
        <p className="text-primary-600 text-sm mt-1">Nome atualizado!</p>
      )}
      {error && <p className="text-red-500 text-sm mt-1">{error}</p>}
      <button
        onClick={handleSave}
        disabled={mutation.isPending || !name.trim() || name.trim() === initialName}
        className="mt-2 w-full rounded-xl bg-primary-500 py-2 text-sm font-semibold text-white
                   disabled:opacity-60 hover:bg-primary-600 transition-colors"
      >
        {mutation.isPending ? 'Salvando…' : 'Salvar'}
      </button>
    </div>
  )
}

// ─── Seção de categorias customizadas ────────────────────────────────────────

function CustomCategories() {
  const queryClient = useQueryClient()

  const { data: categories, isLoading } = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
  })

  // Filtra apenas as categorias do próprio usuário
  const custom = (categories ?? []).filter((c) => !c.global)

  // ID da categoria aguardando confirmação de exclusão
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)
  const [showNewForm, setShowNewForm] = useState(false)

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteCategory(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] })
      setPendingDeleteId(null)
    },
  })

  const createMutation = useMutation({
    mutationFn: createCategory,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] })
      setShowNewForm(false)
    },
  })

  return (
    <div className="bg-gray-50 rounded-xl p-4 mb-3">
      <div className="flex items-center justify-between mb-2">
        <h2 className="text-sm font-semibold text-gray-700">Minhas categorias</h2>
        <button
          onClick={() => setShowNewForm((v) => !v)}
          aria-label="Nova categoria"
          className="w-7 h-7 rounded-full bg-primary-500 text-white flex items-center justify-center text-lg leading-none"
        >
          {showNewForm ? '−' : '+'}
        </button>
      </div>

      {/* Formulário inline de nova categoria */}
      {showNewForm && (
        <NewCategoryForm
          onSubmit={async (values) => {
            await createMutation.mutateAsync(values)
          }}
          onCancel={() => setShowNewForm(false)}
          isSubmitting={createMutation.isPending}
          error={createMutation.isError ? extractApiError(createMutation.error) : null}
        />
      )}

      {isLoading && (
        <div className="space-y-2 mt-2">
          {[1, 2].map((i) => (
            <div key={i} className="animate-pulse bg-gray-100 rounded h-8" />
          ))}
        </div>
      )}

      {!isLoading && custom.length === 0 && !showNewForm && (
        <p className="text-xs text-gray-400 mt-1">
          Nenhuma categoria personalizada ainda.
        </p>
      )}

      {custom.length > 0 && (
        <ul className="mt-1">
          {custom.map((cat) => (
            <li
              key={cat.id}
              className="flex items-center justify-between py-2 border-b border-gray-100 last:border-0"
            >
              <div className="flex items-center gap-2">
                <div
                  className="w-3 h-3 rounded-full flex-shrink-0"
                  style={{ backgroundColor: cat.color }}
                  aria-hidden="true"
                />
                <span className="text-sm text-gray-700">{cat.name}</span>
              </div>

              {pendingDeleteId === cat.id ? (
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setPendingDeleteId(null)}
                    className="text-xs text-gray-500"
                  >
                    Cancelar
                  </button>
                  <button
                    onClick={() => deleteMutation.mutate(cat.id)}
                    disabled={deleteMutation.isPending}
                    className="text-xs text-red-500 font-medium disabled:opacity-60"
                  >
                    {deleteMutation.isPending ? 'Excluindo…' : 'Confirmar'}
                  </button>
                </div>
              ) : (
                <button
                  onClick={() => setPendingDeleteId(cat.id)}
                  className="text-xs text-red-400 hover:text-red-600 transition-colors"
                >
                  Excluir
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

// ─── Formulário inline de nova categoria ─────────────────────────────────────

function NewCategoryForm({
  onSubmit,
  onCancel,
  isSubmitting,
  error,
}: {
  onSubmit: (values: { name: string; icon: string; color: string }) => Promise<void>
  onCancel: () => void
  isSubmitting: boolean
  error: string | null
}) {
  const [name, setName] = useState('')
  const [color, setColor] = useState('#22c55e')
  const [icon, setIcon] = useState('📦')
  const [nameError, setNameError] = useState<string | null>(null)

  async function handleSave() {
    if (!name.trim()) {
      setNameError('Nome obrigatório')
      return
    }
    setNameError(null)
    await onSubmit({ name: name.trim(), icon: icon || '📦', color })
  }

  return (
    <div className="bg-white border border-gray-200 rounded-lg p-3 mt-2 mb-3 space-y-2">
      {/* Nome */}
      <div>
        <label className="text-xs font-medium text-gray-600">Nome</label>
        <input
          type="text"
          value={name}
          onChange={(e) => {
            setName(e.target.value)
            setNameError(null)
          }}
          placeholder="Ex.: Pet, Academia…"
          className="mt-0.5 w-full rounded-lg border border-gray-200 px-2.5 py-1.5 text-sm
                     focus:outline-none focus:ring-2 focus:ring-primary-500"
        />
        {nameError && <p className="text-xs text-red-500 mt-0.5">{nameError}</p>}
      </div>

      {/* Cor */}
      <div className="flex items-center gap-3">
        <label className="text-xs font-medium text-gray-600">Cor</label>
        <input
          type="color"
          value={color}
          onChange={(e) => setColor(e.target.value)}
          className="w-8 h-8 rounded-lg border border-gray-200 cursor-pointer p-0.5"
        />
        <span className="text-xs text-gray-400">{color}</span>
      </div>

      {/* Ícone */}
      <div>
        <label className="text-xs font-medium text-gray-600">Ícone (emoji)</label>
        <input
          type="text"
          value={icon}
          onChange={(e) => setIcon(e.target.value)}
          placeholder="📦"
          maxLength={2}
          className="mt-0.5 w-16 rounded-lg border border-gray-200 px-2.5 py-1.5 text-center text-base
                     focus:outline-none focus:ring-2 focus:ring-primary-500"
        />
      </div>

      {error && <p className="text-xs text-red-500">{error}</p>}

      <div className="flex gap-2 pt-1">
        <button
          onClick={onCancel}
          className="flex-1 py-1.5 text-sm rounded-lg border border-gray-200 text-gray-600"
        >
          Cancelar
        </button>
        <button
          onClick={handleSave}
          disabled={isSubmitting}
          className="flex-1 py-1.5 text-sm rounded-lg bg-primary-500 text-white font-medium
                     disabled:opacity-60"
        >
          {isSubmitting ? 'Salvando…' : 'Salvar'}
        </button>
      </div>
    </div>
  )
}

// ─── Estados de carregamento / erro ──────────────────────────────────────────

function ProfileSkeleton() {
  return (
    <div className="min-h-screen bg-gray-50 pb-20">
      <div className="bg-white px-4 pt-10 pb-4 mb-3">
        <div className="animate-pulse bg-gray-100 rounded h-6 w-24" />
      </div>
      <div className="px-4 space-y-3">
        <div className="animate-pulse bg-gray-100 rounded-xl h-36" />
        <div className="animate-pulse bg-gray-100 rounded-xl h-24" />
        <div className="animate-pulse bg-gray-100 rounded-xl h-32" />
        <div className="animate-pulse bg-gray-100 rounded-lg h-12" />
      </div>
    </div>
  )
}

function ProfileError() {
  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center pb-20">
      <p className="text-sm text-gray-500">Erro ao carregar perfil. Tente novamente.</p>
    </div>
  )
}
