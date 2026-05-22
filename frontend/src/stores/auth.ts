import { create } from 'zustand'
import type { User } from '../types'

interface AuthState {
  accessToken: string | null
  user: User | null
  isAuthenticated: boolean
  // Verdadeiro até o boot tentar reidratar a sessão via POST /auth/refresh
  // usando o cookie httpOnly. Enquanto for verdadeiro, rotas protegidas devem
  // exibir splash em vez de redirecionar para /login — caso contrário um F5
  // com cookie válido flasharia a tela de login antes de voltar ao dashboard.
  isHydrating: boolean
  setAuth: (token: string, user: User) => void
  clearAuth: () => void
  finishHydration: () => void
}

// Token mantido apenas em memória — sem localStorage para reduzir superfície XSS
// (analise-tecnica.md §10.2 e §12.3). A persistência entre F5 vem do cookie
// httpOnly de refresh + chamada silenciosa a /auth/refresh no boot.
export const useAuthStore = create<AuthState>()((set) => ({
  accessToken: null,
  user: null,
  isAuthenticated: false,
  isHydrating: true,

  setAuth: (token, user) =>
    set({ accessToken: token, user, isAuthenticated: true, isHydrating: false }),

  clearAuth: () =>
    set({ accessToken: null, user: null, isAuthenticated: false, isHydrating: false }),

  finishHydration: () => set({ isHydrating: false }),
}))
