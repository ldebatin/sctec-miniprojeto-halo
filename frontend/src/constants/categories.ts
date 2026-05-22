/** Paleta predefinida para categorias (RF-07 — alinhada ao seed Tailwind 500). */
export const CATEGORY_COLOR_PALETTE = [
  '#F59E0B',
  '#10B981',
  '#3B82F6',
  '#EC4899',
  '#EF4444',
  '#8B5CF6',
  '#0EA5E9',
  '#F472B6',
  '#6B7280',
  '#14B8A6',
  '#22C55E',
  '#94A3B8',
  '#22c55e',
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
