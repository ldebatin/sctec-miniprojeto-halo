import { useEffect, useState } from 'react'
import { CATEGORY_COLOR_PALETTE, CATEGORY_ICON_OPTIONS } from '../constants/categories'
import CategoryIcon from './CategoryIcon'

export type CategoryFormValues = {
  name: string
  icon: string
  color: string
}

type CategoryFormProps = {
  initial: CategoryFormValues
  nameReadOnly?: boolean
  submitLabel: string
  isSubmitting: boolean
  error: string | null
  onSubmit: (values: CategoryFormValues) => void | Promise<void>
  onCancel: () => void
}

export default function CategoryForm({
  initial,
  nameReadOnly = false,
  submitLabel,
  isSubmitting,
  error,
  onSubmit,
  onCancel,
}: CategoryFormProps) {
  const [name, setName] = useState(initial.name)
  const [icon, setIcon] = useState(initial.icon)
  const [color, setColor] = useState(initial.color)
  const [nameError, setNameError] = useState<string | null>(null)

  useEffect(() => {
    setName(initial.name)
    setIcon(initial.icon)
    setColor(initial.color)
    setNameError(null)
  }, [initial.name, initial.icon, initial.color])

  async function handleSubmit() {
    if (!nameReadOnly && !name.trim()) {
      setNameError('Nome obrigatório')
      return
    }
    setNameError(null)
    await onSubmit({ name: name.trim(), icon, color })
  }

  return (
    <div className="space-y-4">
      <div>
        <label htmlFor="cat-name" className="text-sm font-medium text-gray-700">
          Nome
        </label>
        <input
          id="cat-name"
          type="text"
          value={name}
          readOnly={nameReadOnly}
          onChange={(e) => {
            setName(e.target.value)
            setNameError(null)
          }}
          maxLength={50}
          placeholder="Ex.: Pet, Academia…"
          className={`mt-1 w-full rounded-xl border border-gray-200 px-3 py-2 text-sm
            focus:outline-none focus:ring-2 focus:ring-primary-500
            ${nameReadOnly ? 'bg-gray-50 text-gray-500' : 'bg-white'}`}
        />
        {nameError && <p className="text-xs text-red-500 mt-1">{nameError}</p>}
      </div>

      <div>
        <p className="text-sm font-medium text-gray-700 mb-2">Ícone</p>
        <div className="grid grid-cols-6 gap-2">
          {CATEGORY_ICON_OPTIONS.map((opt) => (
            <button
              key={opt}
              type="button"
              onClick={() => setIcon(opt)}
              aria-label={`Ícone ${opt}`}
              aria-pressed={icon === opt}
              className={`p-2 rounded-xl border flex items-center justify-center transition-colors
                ${
                  icon === opt
                    ? 'border-primary-500 bg-primary-50'
                    : 'border-gray-100 bg-gray-50 hover:border-gray-200'
                }`}
            >
              <CategoryIcon name={opt} color={color} size="sm" />
            </button>
          ))}
        </div>
      </div>

      <div>
        <p className="text-sm font-medium text-gray-700 mb-2">Cor</p>
        <div className="flex flex-wrap gap-2">
          {CATEGORY_COLOR_PALETTE.map((c) => (
            <button
              key={c}
              type="button"
              onClick={() => setColor(c)}
              aria-label={`Cor ${c}`}
              aria-pressed={color === c}
              className={`w-9 h-9 rounded-full border-2 transition-transform
                ${color === c ? 'border-gray-800 scale-110' : 'border-transparent'}`}
              style={{ backgroundColor: c }}
            />
          ))}
        </div>
      </div>

      <div className="flex items-center gap-3 pt-1 pb-2">
        <CategoryIcon name={icon} color={color} />
        <div>
          <p className="text-sm font-medium text-gray-800">{name || 'Pré-visualização'}</p>
          <p className="text-xs text-gray-400">{icon}</p>
        </div>
      </div>

      {error && <p className="text-sm text-red-500">{error}</p>}

      <div className="flex gap-2">
        <button
          type="button"
          onClick={onCancel}
          className="flex-1 py-2.5 text-sm rounded-xl border border-gray-200 text-gray-600"
        >
          Cancelar
        </button>
        <button
          type="button"
          onClick={handleSubmit}
          disabled={isSubmitting}
          className="flex-1 py-2.5 text-sm rounded-xl bg-primary-500 text-white font-semibold
                     hover:bg-primary-600 disabled:opacity-60"
        >
          {isSubmitting ? 'Salvando…' : submitLabel}
        </button>
      </div>
    </div>
  )
}
