import {
  Banknote,
  Car,
  CircleHelp,
  Ellipsis,
  GraduationCap,
  Heart,
  House,
  Shirt,
  ShoppingCart,
  Sparkles,
  TrendingUp,
  Utensils,
  Wrench,
  type LucideIcon,
} from 'lucide-react'

// Mapeia o nome em kebab-case salvo em categories.icon (migrations V4/V5)
// para o componente Lucide correspondente. Inclui apelidos para ícones de
// registros antigos copiados antes da V5 — 'question-circle' (V3 →
// 'circle-help'), 'ellipsis-horizontal' (Heroicons → 'ellipsis'),
// 'fork-knife' (V4 → 'utensils' na V5).
const ICONS: Record<string, LucideIcon> = {
  banknote: Banknote,
  car: Car,
  'circle-help': CircleHelp,
  ellipsis: Ellipsis,
  'ellipsis-horizontal': Ellipsis,
  'fork-knife': Utensils,
  'graduation-cap': GraduationCap,
  heart: Heart,
  house: House,
  'question-circle': CircleHelp,
  shirt: Shirt,
  'shopping-cart': ShoppingCart,
  sparkles: Sparkles,
  'trending-up': TrendingUp,
  utensils: Utensils,
  wrench: Wrench,
}

interface CategoryIconProps {
  name: string | undefined
  className?: string
}

/**
 * Renderiza o ícone de uma categoria. Para nomes desconhecidos pelo set
 * Lucide, devolve o próprio texto — assim categorias customizadas que
 * gravaram um emoji em {@code icon} (ex.: "📦") seguem aparecendo.
 */
export default function CategoryIcon({ name, className }: CategoryIconProps) {
  if (!name) return <CircleHelp className={className} aria-hidden="true" />
  const Icon = ICONS[name]
  if (Icon) return <Icon className={className} aria-hidden="true" />
  return (
    <span className={className} aria-hidden="true">
      {name}
    </span>
  )
}
