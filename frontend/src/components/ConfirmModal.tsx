type ConfirmModalProps = {
  open: boolean
  title: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
  isLoading?: boolean
  onConfirm: () => void
  onCancel: () => void
}

export default function ConfirmModal({
  open,
  title,
  message,
  confirmLabel = 'Confirmar',
  cancelLabel = 'Cancelar',
  isLoading = false,
  onConfirm,
  onCancel,
}: ConfirmModalProps) {
  if (!open) return null

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4" role="presentation">
      <button
        type="button"
        className="absolute inset-0 bg-black/40"
        aria-label="Fechar"
        onClick={onCancel}
      />
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-modal-title"
        className="relative bg-white rounded-2xl p-5 w-full max-w-sm shadow-xl"
      >
        <h2 id="confirm-modal-title" className="text-base font-semibold text-gray-800">
          {title}
        </h2>
        <p className="text-sm text-gray-500 mt-2">{message}</p>
        <div className="flex gap-2 mt-5">
          <button
            type="button"
            onClick={onCancel}
            disabled={isLoading}
            className="flex-1 py-2.5 text-sm rounded-xl border border-gray-200 text-gray-600
                       disabled:opacity-60"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={isLoading}
            className="flex-1 py-2.5 text-sm rounded-xl bg-red-500 text-white font-medium
                       hover:bg-red-600 disabled:opacity-60"
          >
            {isLoading ? 'Excluindo…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
