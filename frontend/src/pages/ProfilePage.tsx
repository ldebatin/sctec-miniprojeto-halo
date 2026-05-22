import { useState, useEffect } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import axios from 'axios'
import { useProfile, useUpdateProfile } from '../hooks/useProfile'
import { useAuthStore } from '../stores/auth'
import { getCategories, createCategory, deleteCategory } from '../api/categories'
import { logout as logoutApi } from '../api/auth'

function getInitials(name: string): string {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join('')
}

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
      <header className="bg-white px-4 pt-10 pb-4 mb-3">
        <h1 className="text-lg font-semibold text-gray-800">Perfil</h1>
      </header>

      <div className="px-4 space-y-3">
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

        <NameEditor initialName={user.name} />

        <Link
          to="/categorias"
          className="flex items-center justify-between bg-white rounded-xl px-4 py-3 shadow-sm
                     text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
        >
          <span>Gerenciar categorias</span>
          <ChevronIcon />
        </Link>

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

function NameEditor({ initialName }: { initialName: string }) {
  const [name, setName] = useState(initialName)
  const [success, setSuccess] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const mutation = useUpdateProfile()

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

function ChevronIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 24 24"
      strokeWidth={2}
      stroke="currentColor"
      className="w-4 h-4 text-gray-400"
      aria-hidden="true"
    >
      <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
    </svg>
  )
}

function ProfileSkeleton() {
  return (
    <div className="min-h-screen bg-gray-50 pb-20">
      <div className="bg-white px-4 pt-10 pb-4 mb-3">
        <div className="animate-pulse bg-gray-100 rounded h-6 w-24" />
      </div>
      <div className="px-4 space-y-3">
        <div className="animate-pulse bg-gray-100 rounded-xl h-36" />
        <div className="animate-pulse bg-gray-100 rounded-xl h-24" />
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