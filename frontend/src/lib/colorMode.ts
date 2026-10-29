import { useCallback, useSyncExternalStore } from 'react';

/**
 * Light / dark / system appearance. The choice lives in localStorage and is applied as
 * <html data-theme="light|dark">; "system" removes the attribute so the OS setting decides
 * (see the prefers-color-scheme blocks in index.css). index.html applies the saved choice
 * before the first paint so the page never flashes the wrong colours.
 */
export type ColorMode = 'light' | 'dark' | 'system';

const KEY = 'ghoomo.colorMode';
const listeners = new Set<() => void>();

function read(): ColorMode {
  try {
    const v = localStorage.getItem(KEY);
    return v === 'light' || v === 'dark' ? v : 'system';
  } catch {
    return 'system';
  }
}

function apply(mode: ColorMode) {
  const root = document.documentElement;
  if (mode === 'system') delete root.dataset.theme;
  else root.dataset.theme = mode;
}

export function setColorMode(mode: ColorMode) {
  try {
    if (mode === 'system') localStorage.removeItem(KEY);
    else localStorage.setItem(KEY, mode);
  } catch {
    /* storage unavailable: the choice lasts for this visit only */
  }
  current = mode;
  apply(mode);
  listeners.forEach((l) => l());
}

let current: ColorMode = typeof window === 'undefined' ? 'system' : read();

export function useColorMode(): [ColorMode, (m: ColorMode) => void] {
  const mode = useSyncExternalStore(
    useCallback((cb: () => void) => {
      listeners.add(cb);
      return () => listeners.delete(cb);
    }, []),
    () => current,
  );
  return [mode, setColorMode];
}
