import { Link } from 'react-router-dom';
import { inr } from '../lib/format';
import { THEME_LABEL, asThemeKey, type ThemeKey } from '../lib/theme';
import type { ItineraryDetail } from '../types';

/** The coloured header above an itinerary: destination, trip facts and a small line drawing of the place. */
export function DestinationBanner({ itinerary }: { itinerary: ItineraryDetail }) {
  const key = asThemeKey(itinerary.themeKey);
  const destination = itinerary.plan.destination || itinerary.destination;
  const region = THEME_LABEL[key];
  const showRegion = key !== 'default' && !destination.toLowerCase().includes(region.toLowerCase().replace(/^the /, ''));
  const days = itinerary.plan.days.length;

  const facts = [
    `${days} ${days === 1 ? 'day' : 'days'}`,
    showRegion ? region : null,
    itinerary.budgetInr != null ? `Budget ${inr(itinerary.budgetInr)}` : null,
  ].filter(Boolean);

  return (
    <header className="theme-fade relative mb-6 overflow-hidden rounded-card bg-brand px-5 py-5 text-on-brand sm:px-6">
      <Motif themeKey={key} />
      <div className="relative max-w-[75%]">
        <p className="text-sm text-on-brand/80">Your trip to</p>
        <h2 className="mt-0.5 text-[1.9rem] font-semibold leading-tight">{destination}</h2>
        <p className="mt-1.5 text-sm text-on-brand/85">{facts.join(' · ')}</p>
      </div>
      <div className="relative mt-4 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm">
        <span className="text-on-brand/80">Saved to My trips</span>
        <Link
          to={`/trips/${itinerary.id}`}
          className="font-semibold underline decoration-on-brand/40 underline-offset-4 hover:decoration-on-brand"
        >
          Edit this trip
        </Link>
      </div>
    </header>
  );
}

/** One simple outline drawing per destination, in the banner's text colour at low strength. */
function Motif({ themeKey }: { themeKey: ThemeKey }) {
  const common = {
    viewBox: '0 0 200 110',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 2.5,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    className: 'pointer-events-none absolute -right-2 bottom-0 h-[110px] w-[200px] text-on-brand/25',
    'aria-hidden': true,
  };

  switch (themeKey) {
    case 'rajasthan':
      return (
        <svg {...common}>
          <path d="M8 34h184" />
          <path d="M16 110V64a24 24 0 0 1 48 0v46" />
          <path d="M76 110V64a24 24 0 0 1 48 0v46" />
          <path d="M136 110V64a24 24 0 0 1 48 0v46" />
          <path d="M8 34V24h14v10h14V24h14v10h14V24h14v10h14V24h14v10h14V24h14v10h14V24h14v10h14V24h10v10" />
        </svg>
      );
    case 'kerala':
      return (
        <svg {...common}>
          <path d="M0 80q25-14 50 0t50 0 50 0 50 0" />
          <path d="M0 98q25-14 50 0t50 0 50 0 50 0" />
          <path d="M140 66V22" />
          <path d="M140 24q-20-2-34 14M140 24q20-2 34 14M140 24q-7-14-25-14M140 24q7-14 25-14" />
        </svg>
      );
    case 'goa':
      return (
        <svg {...common}>
          <circle cx="132" cy="44" r="20" />
          <path d="M0 78q25-14 50 0t50 0 50 0 50 0" />
          <path d="M0 96q25-14 50 0t50 0 50 0 50 0" />
        </svg>
      );
    case 'himalayas':
      return (
        <svg {...common}>
          <path d="M0 110 50 50l26 30 40-56 50 70 34-30" />
          <path d="m104 40 12-16 12 16" />
          <path d="M70 16q55 22 124 4" />
          <path d="m90 23 5 11 6-9M120 30l4 11 7-9M150 31l3 11 7-10M178 26l2 11 8-10" />
        </svg>
      );
    case 'japan':
      return (
        <svg {...common} strokeWidth={3.5}>
          <path d="M28 34q72-14 144 0" />
          <path d="M44 52h112" />
          <path d="M62 36v74M138 36v74M100 38v14" />
        </svg>
      );
    case 'santorini':
      return (
        <svg {...common}>
          <path d="M70 70a30 30 0 0 1 60 0" />
          <path d="M100 40V26M93 32h14" />
          <path d="M60 70h80v40M60 70v40" />
          <path d="M92 110V90a8 8 0 0 1 16 0v20" />
          <path d="M150 110V84h40v26" />
        </svg>
      );
    default:
      return (
        <svg {...common}>
          <path d="M30 92c20 0 16-46 54-40s34 44 66 30 30-50 40-56" strokeDasharray="2 9" />
          <circle cx="30" cy="92" r="6" />
          <circle cx="190" cy="26" r="6" />
        </svg>
      );
  }
}
