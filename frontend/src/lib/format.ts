export const inr = (n: number | null | undefined) =>
  n == null ? '—' : '₹' + Math.round(n).toLocaleString('en-IN');

export const shortDate = (iso: string | null | undefined) => {
  if (!iso) return null;
  const d = new Date(iso.length === 10 ? iso + 'T00:00:00' : iso);
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
};

export const relativeTime = (iso: string) => {
  const diff = (Date.now() - new Date(iso).getTime()) / 1000;
  if (diff < 60) return 'just now';
  if (diff < 3600) return `${Math.floor(diff / 60)} min ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)} h ago`;
  if (diff < 86400 * 7) return `${Math.floor(diff / 86400)} d ago`;
  return shortDate(iso) ?? '';
};

/** One colour per day, used on the route line and the matching map pins. */
export const DAY_COLORS = ['#DE9810', '#2E7D68', '#3B6CB0', '#C2536B', '#7A5BC2', '#2A9DB0', '#8C6A2E'];
export const dayColor = (day: number) => DAY_COLORS[(day - 1) % DAY_COLORS.length];

/** WMO weather codes from Open-Meteo, grouped coarsely. */
export const weatherLabel = (code: number | null) => {
  if (code == null) return 'Forecast';
  if (code === 0) return 'Clear';
  if (code <= 3) return 'Partly cloudy';
  if (code <= 48) return 'Foggy';
  if (code <= 67) return 'Rain';
  if (code <= 77) return 'Snow';
  if (code <= 82) return 'Showers';
  return 'Thunderstorms';
};
