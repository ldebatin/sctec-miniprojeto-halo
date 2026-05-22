/**
 * Página de gestão de categorias na web (RF-07, RF-08 — analise-tecnica.md §12.1).
 *
 * Esta task (T-036) só faz: rota /categorias com listas globais/customizadas,
 * BottomSheet de criar/editar/personalizar, seletor lucide + paleta de cores,
 * exclusão de customizadas com modal de confirmação.
 */
import { useMemo, useState } from 'react'
import axios from 'axios'
import BottomSheet from '../components/BottomSheet'
import CategoryForm, { type CategoryFormValues } from '../components/CategoryForm'
import CategoryIcon from '../components/CategoryIcon'
import ConfirmModal from '../components/ConfirmModal'
import { CATEGORY_ICON_OPTIONS } from '../constants/categories'
import { useCategories, useCategoryMutations } from '../hooks/useCategories'
import type { Category } from '../types'

function categoryFormError(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const msg = (err.response?.data as { message?: string })?.message
    return typeof msg === 'string' ? msg : 'Erro na requisição.'
  }
  return 'Erro desconhecido.'
}

type SheetState =
  | { mode: 'create' }
  | { mode: 'edit'; category: Category }
  | { mode: 'personalize'; category: Category }

const DEFAULT_FORM: CategoryFormValues = {
  name: '',
  icon: CATEGORY_ICON_OPTIONS[0],
  color: '#22c55e',
}

function toFormValues(cat: Category): CategoryFormValues {
  return { name: cat.name, icon: cat.icon, color: cat.color }
}

export default function CategoriesPage() {
  const { data: categories = [], isLoading, isError, refetch } = useCategories()
  const { create, update, remove, personalize } = useCategoryMutations()

  const [sheet, setSheet] = useState<SheetState | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Category | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

  const globals = useMemo(
    () => categories.filter((c) => !c.isCustom).sort((a, b) => a.name.localeCompare(b.name, 'pt-BR')),
    [categories],
  )
  const customs = useMemo(
    () => categories.filter((c) => c.isCustom).sort((a, b) => a.name.localeCompare(b.name, 'pt-BR')),
    [categories],
  )

  const activeMutation =
    sheet?.mode === 'create'
      ? create
      : sheet?.mode === 'edit'
        ? update
        : sheet?.mode === 'personalize'
          ? personalize
          : null

  function closeSheet() {
    setSheet(null)
    setFormError(null)
  }

  async function handleFormSubmit(values: CategoryFormValues) {
    if (!sheet) return
    setFormError(null)
    try {
      if (sheet.mode === 'create') {
        await create.mutateAsync(values)
      } else if (sheet.mode === 'edit') {
        await update.mutateAsync({ id: sheet.category.id, body: values })
      } else {
        await personalize.mutateAsync({ globalId: sheet.category.id, body: values })
      }
      closeSheet()
    } catch (err) {
      setFormError(categoryFormError(err))
    }
  }

  const sheetTitle =
    sheet?.mode === 'create'
      ? 'Nova categoria'
      : sheet?.mode === 'edit'
        ? 'Editar categoria'
        : sheet?.mode === 'personalize'
          ? 'Personalizar categoria'
          : ''

  const sheetInitial =
    sheet?.mode === 'create'
      ? DEFAULT_FORM
      : sheet
        ? toFormValues(sheet.category)
        : DEFAULT_FORM

  return (
    <div className="min-h-screen bg-gray-50 pb-20">
      <header className="bg-white px-4 pt-10 pb-4 flex items-center justify-between sticky top-0 z-10 border-b border-gray-100">
        <h1 className="text-lg font-semibold text-gray-800">Categorias</h1>
        <button
          type="button"
          onClick={() => setSheet({ mode: 'create' })}
          className="flex items-center gap-1 rounded-full bg-primary-500 text-white text-sm font-medium
                     px-3 py-1.5 hover:bg-primary-600 transition-colors"
        >
          <PlusIcon />
          Nova
        </button>
      </header>

      <main className="px-4 py-4 space-y-6">
        {isLoading && <CategoriesSkeleton />}

        {isError && (
          <div className="bg-white rounded-xl p-4 text-center">
            <p className="text-sm text-gray-500">Não foi possível carregar as categorias.</p>
            <button
              type="button"
              onClick={() => refetch()}
              className="mt-2 text-sm text-primary-600 font-medium"
            >
              Tentar novamente
            </button>
          </div>
        )}

        {!isLoading && !isError && (
          <>
            <CategorySection title="Padrão do Halo" subtitle="Categorias globais — personalize se quiser">
              {globals.length === 0 ? (
                <p className="text-sm text-gray-400">Nenhuma categoria global disponível.</p>
              ) : (
                <ul className="space-y-2">
                  {globals.map((cat) => (
                    <CategoryRow
                      key={cat.id}
                      category={cat}
                      actions={
                        <button
                          type="button"
                          onClick={() => setSheet({ mode: 'personalize', category: cat })}
                          className="text-xs font-medium text-primary-600 hover:text-primary-700"
                        >
                          Personalizar
                        </button>
                      }
                    />
                  ))}
                </ul>
              )}
            </CategorySection>

            <CategorySection
              title="Suas categorias"
              subtitle="Criadas por você ou personalizadas a partir das globais"
            >
              {customs.length === 0 ? (
                <p className="text-sm text-gray-400">
                  Nenhuma categoria personalizada ainda. Use &quot;+ Nova&quot; ou personalize uma global.
                </p>
              ) : (
                <ul className="space-y-2">
                  {customs.map((cat) => (
                    <CategoryRow
                      key={cat.id}
                      category={cat}
                      badge={cat.globalId ? 'Personalizada' : undefined}
                      actions={
                        <div className="flex items-center gap-3">
                          <button
                            type="button"
                            onClick={() => setSheet({ mode: 'edit', category: cat })}
                            className="text-xs font-medium text-gray-600 hover:text-gray-800"
                          >
                            Editar
                          </button>
                          <button
                            type="button"
                            onClick={() => setDeleteTarget(cat)}
                            className="text-xs font-medium text-red-500 hover:text-red-600"
                          >
                            Excluir
                          </button>
                        </div>
                      }
                    />
                  ))}
                </ul>
              )}
            </CategorySection>
          </>
        )}
      </main>

      <BottomSheet open={sheet !== null} title={sheetTitle} onClose={closeSheet}>
        {sheet && (
          <CategoryForm
            key={`${sheet.mode}-${sheet.mode !== 'create' ? sheet.category.id : 'new'}`}
            initial={sheetInitial}
            nameReadOnly={sheet.mode === 'personalize'}
            submitLabel={
              sheet.mode === 'create'
                ? 'Criar'
                : sheet.mode === 'personalize'
                  ? 'Salvar personalização'
                  : 'Salvar'
            }
            isSubmitting={activeMutation?.isPending ?? false}
            error={formError}
            onSubmit={handleFormSubmit}
            onCancel={closeSheet}
          />
        )}
      </BottomSheet>

      <ConfirmModal
        open={deleteTarget !== null}
        title="Excluir categoria?"
        message={
          deleteTarget
            ? `A categoria "${deleteTarget.name}" será desativada. Lançamentos antigos mantêm a referência.`
            : ''
        }
        confirmLabel="Excluir"
        isLoading={remove.isPending}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={async () => {
          if (!deleteTarget) return
          try {
            await remove.mutateAsync(deleteTarget.id)
            setDeleteTarget(null)
          } catch {
            /* lista permanece; usuário pode tentar de novo */
          }
        }}
      />
    </div>
  )
}

function CategorySection({
  title,
  subtitle,
  children,
}: {
  title: string
  subtitle: string
  children: React.ReactNode
}) {
  return (
    <section>
      <h2 className="text-sm font-semibold text-gray-700">{title}</h2>
      <p className="text-xs text-gray-400 mb-3">{subtitle}</p>
      {children}
    </section>
  )
}

function CategoryRow({
  category,
  actions,
  badge,
}: {
  category: Category
  actions: React.ReactNode
  badge?: string
}) {
  return (
    <li className="bg-white rounded-xl px-3 py-3 shadow-sm flex items-center gap-3">
      <CategoryIcon name={category.icon} color={category.color} />
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-gray-800 truncate">{category.name}</p>
        {badge && <p className="text-xs text-gray-400">{badge}</p>}
      </div>
      {actions}
    </li>
  )
}

function CategoriesSkeleton() {
  return (
    <div className="space-y-4">
      {[1, 2, 3, 4].map((i) => (
        <div key={i} className="animate-pulse bg-gray-100 rounded-xl h-14" />
      ))}
    </div>
  )
}

function PlusIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 24 24"
      strokeWidth={2}
      stroke="currentColor"
      className="w-4 h-4"
      aria-hidden="true"
    >
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
    </svg>
  )
}
