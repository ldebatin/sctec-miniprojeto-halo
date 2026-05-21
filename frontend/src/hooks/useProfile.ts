import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getMe, updateMe } from '../api/user'
import { useAuthStore } from '../stores/auth'
import type { User } from '../types'

export function useProfile(): {
  user: User | undefined
  isLoading: boolean
  isError: boolean
} {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['me'],
    queryFn: getMe,
    staleTime: 30 * 60 * 1000, // perfil muda raramente — 30 min antes de revalidar
  })

  return { user: data, isLoading, isError }
}

export function useUpdateProfile() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (body: { name: string }) => updateMe(body),
    onSuccess: (updatedUser) => {
      // Invalida a query de /me para próximas leituras
      queryClient.invalidateQueries({ queryKey: ['me'] })

      // Atualiza o store de auth para refletir o novo nome imediatamente
      const token = useAuthStore.getState().accessToken
      if (token) {
        useAuthStore.getState().setAuth(token, updatedUser)
      }
    },
  })
}
