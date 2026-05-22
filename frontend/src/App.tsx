import { useEffect, type ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuthStore } from './stores/auth'
import { silentRefresh } from './api/auth'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import BottomNav from './components/BottomNav'

import ExpensesPage from './pages/ExpensesPage'
import ExpenseDetailPage from './pages/ExpenseDetailPage'

import ProfilePage from './pages/ProfilePage'
import CategoriesPage from './pages/CategoriesPage'

// Guarda de rota: redireciona para /login quando não há sessão ativa.
// Enquanto isHydrating for true (tentativa de silentRefresh no boot), segura
// o redirect — caso contrário um F5 com cookie válido flasharia o /login
// antes de voltar pra rota protegida.
function ProtectedRoute({ children }: { children: ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const isHydrating     = useAuthStore((s) => s.isHydrating)
  if (isHydrating) return <SessionSplash />
  if (!isAuthenticated) return <Navigate to="/login" replace />
  return (
    <>
      {children}
      <BottomNav />
    </>
  )
}

// Splash neutro durante o silentRefresh no boot.
function SessionSplash() {
  return <div className="min-h-screen bg-white" aria-busy="true" />
}

export default function App() {
  const setAuth         = useAuthStore((s) => s.setAuth)
  const finishHydration = useAuthStore((s) => s.finishHydration)

  // Tenta reidratar a sessão a partir do cookie httpOnly no primeiro mount.
  // Sucesso → setAuth já marca isHydrating=false; falha → finishHydration
  // libera o ProtectedRoute para redirecionar pra /login.
  useEffect(() => {
    silentRefresh()
      .then(({ accessToken, user }) => setAuth(accessToken, user))
      .catch(() => finishHydration())
  }, [setAuth, finishHydration])

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
