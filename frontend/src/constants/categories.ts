/**
 * Paleta canônica de 20 cores para categorias (RF-07).
 * Mesma lista usada pela migration V6 do backend para padronizar as cores
 * das categorias globais — garante que o gráfico de pizza do resumo mensal
 * (RF-15, analise-tecnica §9.3) renderize com cores consistentes entre
 * usuários.
 */
export const CATEGORY_COLOR_PALETTE = [
  '#EF4444', // Vermelho
  '#22C55E', // Verde
  '#EAB308', // Amarelo
  '#3B82F6', // Azul
  '#F97316', // Laranja
  '#A855F7', // Roxo
  '#06B6D4', // Ciano
  '#D946EF', // Magenta
  '#84CC16', // Limão
  '#EC4899', // Rosa
  '#14B8A6', // Verde-azulado
  '#B5A1E5', // Lavanda
  '#8B5C3B', // Marrom
  '#E6D5B8', // Bege
  '#800020', // Bordô
  '#98D8C8', // Menta
  '#708238', // Oliva
  '#FBCEB1', // Damasco
  '#1E3A8A', // Azul-marinho
  '#6B7280', // Cinza
] as const

/** Ícones lucide-react disponíveis no seletor (nomes kebab-case = valor no backend). */
export const CATEGORY_ICON_OPTIONS = [
  'utensils',
  'shopping-cart',
  'car',
  'sparkles',
  'heart',
  'house',
  'graduation-cap',
  'shirt',
  'wrench',
  'trending-up',
  'banknote',
  'ellipsis',
  'circle-help',
  'coffee',
  'dog',
  'dumbbell',
  'plane',
  'gift',
  'smartphone',
  'baby',
  'book-open',
  'music',
  'gamepad-2',
  'pill',
  'fuel',
] as const

export type CategoryIconName = (typeof CATEGORY_ICON_OPTIONS)[number]
