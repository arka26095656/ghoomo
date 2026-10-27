import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { Compass, LogOut, Map, SlidersHorizontal } from 'lucide-react';
import clsx from 'clsx';
import { Logo } from './Logo';
import { useAuth } from '../lib/auth';

const links = [
  { to: '/plan', label: 'Plan', icon: Compass },
  { to: '/trips', label: 'My trips', icon: Map },
  { to: '/settings', label: 'Preferences', icon: SlidersHorizontal },
];

export function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="flex h-full flex-col">
      <header className="flex h-14 shrink-0 items-center gap-2 border-b border-line bg-paper/90 px-4 backdrop-blur sm:px-6">
        <NavLink to="/plan" aria-label="Ghoomo home">
          <Logo />
        </NavLink>
        <nav className="ml-4 flex items-center gap-1 sm:ml-8" aria-label="Main">
          {links.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                clsx(
                  'inline-flex items-center gap-2 rounded-full px-3 py-1.5 text-sm font-medium transition-colors',
                  isActive ? 'bg-ink text-paper' : 'text-muted hover:bg-sunk hover:text-ink',
                )
              }
            >
              <Icon size={16} aria-hidden="true" />
              <span className="hidden sm:inline">{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="ml-auto flex items-center gap-3">
          <span className="hidden text-sm text-muted md:inline">{user?.name}</span>
          <button
            className="btn-quiet px-2.5"
            onClick={() => {
              logout();
              navigate('/');
            }}
            aria-label="Log out"
            title="Log out"
          >
            <LogOut size={16} />
          </button>
        </div>
      </header>
      <main className="min-h-0 flex-1">
        <Outlet />
      </main>
    </div>
  );
}
