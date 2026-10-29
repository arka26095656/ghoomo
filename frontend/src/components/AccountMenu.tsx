import { useCallback, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { History, LogOut, Menu, Monitor, Moon, SlidersHorizontal, Sun, UserRound, X } from 'lucide-react';
import clsx from 'clsx';
import { useAuth } from '../lib/auth';
import { useColorMode, type ColorMode } from '../lib/colorMode';
import { useDismiss } from '../lib/useDismiss';

const MODES: { value: ColorMode; label: string; icon: typeof Sun }[] = [
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'dark', label: 'Dark', icon: Moon },
  { value: 'system', label: 'System', icon: Monitor },
];

/** The menu at the top right: account, preferences, chat history, appearance and log out. */
export function AccountMenu() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useColorMode();
  const buttonRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  const close = useCallback(() => setOpen(false), []);
  useDismiss(open, close, [buttonRef, panelRef]);

  const initial = (user?.name?.trim()[0] ?? '?').toUpperCase();

  const items = [
    { to: '/settings#account', label: 'Account settings', icon: UserRound },
    { to: '/settings#preferences', label: 'Travel preferences', icon: SlidersHorizontal },
    { to: '/plan?history=1', label: 'Chat history', icon: History },
  ];

  return (
    <div className="relative">
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-haspopup="true"
        aria-label="Account menu"
        className={clsx(
          'flex items-center gap-2 rounded-full border py-1 pl-1 pr-2.5 transition-colors',
          open ? 'border-ink/30 bg-surface' : 'border-line hover:border-ink/30 hover:bg-surface',
        )}
      >
        <span
          className="grid h-7 w-7 place-items-center rounded-full bg-brand font-display text-sm font-semibold text-on-brand"
          aria-hidden="true"
        >
          {initial}
        </span>
        <span className="hidden max-w-[10rem] truncate text-sm font-medium md:inline">{user?.name}</span>
        {open ? <X size={16} aria-hidden="true" /> : <Menu size={16} aria-hidden="true" />}
      </button>

      {open && (
        <div
          ref={panelRef}
          className="absolute right-0 top-[calc(100%+0.5rem)] z-50 w-72 max-w-[calc(100vw-2rem)] overflow-hidden rounded-card border border-line bg-surface shadow-xl"
        >
          <div className="border-b border-line px-4 py-3">
            <p className="truncate font-semibold">{user?.name}</p>
            <p className="truncate text-sm text-muted">{user?.email}</p>
          </div>

          <nav className="py-1.5" aria-label="Account">
            {items.map(({ to, label, icon: Icon }) => (
              <Link
                key={to}
                to={to}
                onClick={close}
                className="flex items-center gap-3 px-4 py-2 text-sm hover:bg-sunk focus-visible:bg-sunk"
              >
                <Icon size={16} className="text-muted" aria-hidden="true" />
                {label}
              </Link>
            ))}
          </nav>

          <div className="border-t border-line px-4 py-3">
            <p id="appearance-label" className="mb-2 text-sm text-muted">
              Appearance
            </p>
            <div role="radiogroup" aria-labelledby="appearance-label" className="grid grid-cols-3 gap-1 rounded-xl bg-sunk p-1">
              {MODES.map(({ value, label, icon: Icon }) => (
                <button
                  key={value}
                  type="button"
                  role="radio"
                  aria-checked={mode === value}
                  onClick={() => setMode(value)}
                  className={clsx(
                    'flex flex-col items-center gap-1 rounded-lg py-1.5 text-xs font-medium transition-colors',
                    mode === value ? 'bg-surface text-ink shadow-sm' : 'text-muted hover:text-ink',
                  )}
                >
                  <Icon size={16} aria-hidden="true" />
                  {label}
                </button>
              ))}
            </div>
          </div>

          <div className="border-t border-line py-1.5">
            <button
              type="button"
              onClick={() => {
                close();
                logout();
                navigate('/');
              }}
              className="flex w-full items-center gap-3 px-4 py-2 text-sm text-danger hover:bg-sunk"
            >
              <LogOut size={16} aria-hidden="true" />
              Log out
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
