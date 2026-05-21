import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuthStore } from './stores/auth'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import BottomNav from './components/BottomNav'

import ExpensesPage from './pages/ExpensesPage'
import ExpenseDetailPage from './pages/ExpenseDetailPage'

import ProfilePage from './pages/ProfilePage'

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

export default function App() {
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
