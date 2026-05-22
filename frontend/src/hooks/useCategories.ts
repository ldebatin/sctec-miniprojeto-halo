import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  copyFromGlobal,
  createCategory,
  deleteCategory,
  getCategories,
  updateCategory,
  type CategoryPayload,
} from '../api/categories'

export const CATEGORIES_QUERY_KEY = ['categories'] as const

export function useCategories() {
  return useQuery({
    queryKey: CATEGORIES_QUERY_KEY,
    queryFn: getCategories,
  })
}

export function useCategoryMutations() {
  const queryClient = useQueryClient()

  const invalidate = () => queryClient.invalidateQueries({ queryKey: CATEGORIES_QUERY_KEY })

  const create = useMutation({
    mutationFn: (body: CategoryPayload) => createCategory(body),
    onSuccess: invalidate,
  })

  const update = useMutation({
    mutationFn: ({ id, body }: { id: string; body: CategoryPayload }) =>
      updateCategory(id, body),
    onSuccess: invalidate,
  })

  const remove = useMutation({
    mutationFn: (id: string) => deleteCategory(id),
    onSuccess: invalidate,
  })

  const personalize = useMutation({
    mutationFn: ({
      globalId,
      body,
    }: {
      globalId: string
      body: CategoryPayload
    }) =>
      copyFromGlobal(globalId, { icon: body.icon, color: body.color }).then((copy) => {
        if (
          copy.name !== body.name ||
          copy.icon !== body.icon ||
          copy.color !== body.color
        ) {
          return updateCategory(copy.id, body)
        }
        return copy
      }),
    onSuccess: invalidate,
  })

  return { create, update, remove, personalize }
}
