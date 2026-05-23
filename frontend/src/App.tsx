import { useEffect, useState, type ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuthStore } from './stores/auth'
import { fetchMe, refreshSession } from './api/auth'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import BottomNav from './components/BottomNav'

import ExpensesPage from './pages/ExpensesPage'
import ExpenseDetailPage from './pages/ExpenseDetailPage'

import ProfilePage from './pages/ProfilePage'
import CategoriesPage from './pages/CategoriesPage'

// Guarda de rota: redireciona para /login quando não há sessão ativa
// BottomNav incluído aqui para aparecer em todas as rotas protegidas
function ProtectedRoute({ children }: { children: ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  if (!isAuthenticated) return <Navigate to="/login" replace />
  return (
    <>
      {children}
      <BottomNav />
    </>
  )
}

// Splash mínimo enquanto o bootstrap silent-refresh resolve. Mantemos curto
// porque o caminho ruim (sem cookie) cai em <100ms.
function BootstrapSplash() {
  return (
    <main className="min-h-screen flex items-center justify-center bg-white">
      <div className="animate-pulse h-10 w-10 rounded-full bg-gray-200" aria-hidden="true" />
    </main>
  )
}

export default function App() {
  const setAuth = useAuthStore((s) => s.setAuth)
  const [bootstrapped, setBootstrapped] = useState(false)

  // Token vive só em memória (analise-tecnica §10.2). Ao subir a app — incluindo
  // após F5 — tentamos restaurar a sessão via cookie httpOnly de refresh.
  // Falha silenciosa: cai no fluxo normal de /login.
  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const { accessToken } = await refreshSession()
        const user = await fetchMe(accessToken)
        if (!cancelled) setAuth(accessToken, user)
      } catch {
        // Sem sessão válida — segue para /login.
      } finally {
        if (!cancelled) setBootstrapped(true)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [setAuth])

  if (!bootstrapped) return <BootstrapSplash />

  return (
    <Routes>
      {/* Rota pública */}
      <Route path="/login" element={<LoginPage />} />

      {/* Rotas protegidas (analise-tecnica.md §12.1) */}
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <DashboardPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/lancamentos"
        element={
          <ProtectedRoute>
            <ExpensesPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/lancamentos/:id"
        element={
          <ProtectedRoute>
            <ExpenseDetailPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/categorias"
        element={
          <ProtectedRoute>
            <CategoriesPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/perfil"
        element={
          <ProtectedRoute>
            <ProfilePage />
          </ProtectedRoute>
        }
      />

      {/* Qualquer rota desconhecida cai na raiz (que redireciona para /login se não autenticado) */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
