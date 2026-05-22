import {
  Baby,
  Banknote,
  BookOpen,
  Car,
  CircleHelp,
  Coffee,
  Dog,
  Dumbbell,
  Ellipsis,
  Fuel,
  Gamepad2,
  Gift,
  GraduationCap,
  Heart,
  House,
  Music,
  Pill,
  Plane,
  Shirt,
  ShoppingCart,
  Smartphone,
  Sparkles,
  TrendingUp,
  Utensils,
  Wrench,
  type LucideIcon,
} from 'lucide-react'

/** Aliases de ícones legados (V4) → componentes lucide-react atuais. */
const ICON_ALIASES: Record<string, LucideIcon> = {
  'fork-knife': Utensils,
  home: House,
  'academic-cap': GraduationCap,
  banknotes: Banknote,
  'ellipsis-horizontal': Ellipsis,
}

const ICON_MAP: Record<string, LucideIcon> = {
  utensils: Utensils,
  'shopping-cart': ShoppingCart,
  car: Car,
  sparkles: Sparkles,
  heart: Heart,
  house: House,
  'graduation-cap': GraduationCap,
  shirt: Shirt,
  wrench: Wrench,
  'trending-up': TrendingUp,
  banknote: Banknote,
  ellipsis: Ellipsis,
  'circle-help': CircleHelp,
  coffee: Coffee,
  dog: Dog,
  dumbbell: Dumbbell,
  plane: Plane,
  gift: Gift,
  smartphone: Smartphone,
  baby: Baby,
  'book-open': BookOpen,
  music: Music,
  'gamepad-2': Gamepad2,
  pill: Pill,
  fuel: Fuel,
}

export function resolveCategoryIcon(iconName: string): LucideIcon {
  const key = iconName.trim().toLowerCase()
  return ICON_ALIASES[key] ?? ICON_MAP[key] ?? CircleHelp
}

type CategoryIconProps = {
  name: string
  color: string
  size?: 'sm' | 'md'
  className?: string
}

export default function CategoryIcon({ name, color, size = 'md', className = '' }: CategoryIconProps) {
  const Icon = resolveCategoryIcon(name)
  const dim = size === 'sm' ? 'w-4 h-4' : 'w-5 h-5'
  const box = size === 'sm' ? 'w-8 h-8' : 'w-10 h-10'

  return (
    <span
      className={`${box} rounded-full flex items-center justify-center flex-shrink-0 ${className}`}
      style={{ backgroundColor: `${color}22`, color }}
      aria-hidden="true"
    >
      <Icon className={dim} strokeWidth={2} />
    </span>
  )
}
