import { useEffect } from 'react';

/**
 * Destination themes. The keys match the backend's DestinationTheme and the
 * :root[data-dest='...'] blocks in index.css, which hold the actual colours.
 */
export const THEME_KEYS = ['default', 'rajasthan', 'kerala', 'goa', 'himalayas', 'japan', 'santorini'] as const;
export type ThemeKey = (typeof THEME_KEYS)[number];

export const THEME_LABEL: Record<ThemeKey, string> = {
  default: 'Ghoomo',
  rajasthan: 'Rajasthan',
  kerala: 'Kerala',
  goa: 'Goa',
  himalayas: 'The Himalayas',
  japan: 'Japan',
  santorini: 'The Greek islands',
};

export const asThemeKey = (value: string | null | undefined): ThemeKey =>
  (THEME_KEYS as readonly string[]).includes(value ?? '') ? (value as ThemeKey) : 'default';

/**
 * Paints the whole app in a destination's palette while the calling page is mounted.
 * Pass null (or 'default') for the standard Ghoomo look. Leaving the page restores it.
 */
export function useDestinationTheme(themeKey: string | null | undefined) {
  const key = asThemeKey(themeKey);
  useEffect(() => {
    const root = document.documentElement;
    if (key === 'default') delete root.dataset.dest;
    else root.dataset.dest = key;
  }, [key]);

  useEffect(
    () => () => {
      delete document.documentElement.dataset.dest;
    },
    [],
  );
}
