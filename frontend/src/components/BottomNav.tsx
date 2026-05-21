import { NavLink } from 'react-router-dom'

// Estilo compartilhado por cada tab — ativo usa primary-500, inativo usa gray-400
function tabClass({ isActive }: { isActive: boolean }) {
  return `flex flex-col items-center gap-1 flex-1 py-2 text-xs font-medium transition-colors ${
    isActive ? 'text-primary-500' : 'text-gray-400'
  }`
}

export default function BottomNav() {
  return (
    <nav
      aria-label="Navegação principal"
      className="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-100
                 flex items-stretch safe-area-bottom"
    >
      {/* Início — `end` garante ativo apenas na rota exata "/" */}
      <NavLink to="/" end className={tabClass}>
        <HomeIcon />
        <span>Início</span>
      </NavLink>

      {/* Gastos — ativo em /lancamentos e /lancamentos/:id */}
      <NavLink to="/lancamentos" className={tabClass}>
        <ListIcon />
        <span>Gastos</span>
      </NavLink>

      {/* Perfil */}
      <NavLink to="/perfil" className={tabClass}>
        <UserIcon />
        <span>Perfil</span>
      </NavLink>
    </nav>
  )
}

// ─── Ícones inline (Heroicons 2 outline — sem dependência externa) ────────────

function HomeIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 24 24"
      strokeWidth={1.5}
      stroke="currentColor"
      className="w-6 h-6"
      aria-hidden="true"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M2.25 12l8.954-8.955c.44-.439 1.152-.439 1.591 0L21.75 12M4.5 9.75v10.125c0 .621.504 1.125 1.125 1.125H9.75v-4.875c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21h4.125c.621 0 1.125-.504 1.125-1.125V9.75M8.25 21h8.25"
      />
    </svg>
  )
}

function ListIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 24 24"
      strokeWidth={1.5}
      stroke="currentColor"
      className="w-6 h-6"
      aria-hidden="true"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M8.25 6.75h12M8.25 12h12m-12 5.25h12M3.75 6.75h.007v.008H3.75V6.75zm.375 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zM3.75 12h.007v.008H3.75V12zm.375 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm-.375 5.25h.007v.008H3.75v-.008zm.375 0a.375.375 0 11-.75 0 .375.375 0 01.75 0z"
      />
    </svg>
  )
}

function UserIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 24 24"
      strokeWidth={1.5}
      stroke="currentColor"
      className="w-6 h-6"
      aria-hidden="true"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z"
      />
    </svg>
  )
}
