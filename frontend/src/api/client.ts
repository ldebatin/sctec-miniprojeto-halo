import axios, { type InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '../stores/auth'

// Extensão local para marcar requests já reprocessados e evitar loop infinito
interface RetryableConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  withCredentials: true, // necessário para enviar e receber o cookie de refresh token (httpOnly)
})

// Injeta Authorization: Bearer <token> em todas as requisições quando há sessão ativa
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

// Renova o access token automaticamente ao receber 401 (analise-tecnica.md §12.3 + T-026)
apiClient.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    const axiosError = axios.isAxiosError(error) ? error : null
    if (!axiosError) return Promise.reject(error)

    const original = axiosError.config as RetryableConfig | undefined

    // Só tenta refresh uma vez; ignora 401 sem config e no próprio logout
    const isLogout = original?.url?.includes('/auth/sessions/current')
    if (axiosError.response?.status === 401 && original && !original._retry && !isLogout) {
      original._retry = true

      try {
        // Usa instância separada para não re-disparar este interceptor
        const { data } = await axios.post<{ accessToken: string }>(
          '/auth/refresh',
          {},
          {
            baseURL: import.meta.env.VITE_API_BASE_URL,
            withCredentials: true,
          },
        )

        const { user, setAuth } = useAuthStore.getState()
        // Mantém os dados do usuário já carregados; só atualiza o token
        if (user) {
          setAuth(data.accessToken, user)
        }

        original.headers['Authorization'] = `Bearer ${data.accessToken}`
        return apiClient(original)
      } catch {
        // Refresh falhou (token expirado ou revogado) → desloga e volta ao login
        useAuthStore.getState().clearAuth()
        window.location.href = '/login'
      }
    }

    return Promise.reject(error)
  },
)

export default apiClient
