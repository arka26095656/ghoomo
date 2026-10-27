import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../lib/api';
import { inr, relativeTime, shortDate } from '../lib/format';
import type { ItinerarySummary } from '../types';

export function Trips() {
  const [trips, setTrips] = useState<ItinerarySummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .itineraries()
      .then(setTrips)
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Could not load your trips.'));
  }, []);

  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto max-w-4xl px-5 py-8 sm:px-8">
        <div className="flex items-end justify-between gap-4">
          <h1 className="text-3xl font-semibold">My trips</h1>
          <Link to="/plan" className="btn-primary">
            Plan a new trip
          </Link>
        </div>

        {error && <p className="mt-6 text-danger">{error}</p>}
        {!trips && !error && <p className="mt-6 text-muted">Loading your trips…</p>}

        {trips && trips.length === 0 && (
          <div className="mt-10 rounded-card border border-dashed border-line p-10 text-center">
            <p className="font-display text-xl font-semibold">No trips yet</p>
            <p className="mt-1 text-muted">Every itinerary Ghoomo makes for you is saved here.</p>
            <Link to="/plan" className="btn-primary mt-5">
              Start planning
            </Link>
          </div>
        )}

        {trips && trips.length > 0 && (
          <ul className="mt-8 divide-y divide-line border-y border-line">
            {trips.map((t) => (
              <li key={t.id}>
                <Link
                  to={`/trips/${t.id}`}
                  className="grid grid-cols-[1fr_auto] items-center gap-x-6 gap-y-1 px-1 py-5 hover:bg-surface sm:grid-cols-[minmax(0,1fr)_7rem_7rem_6rem] sm:px-3"
                >
                  <span className="min-w-0">
                    <span className="block truncate font-display text-2xl font-semibold leading-tight">{t.destination}</span>
                    <span className="block truncate text-sm text-muted">{t.title}</span>
                  </span>
                  <span className="text-right text-sm sm:text-left">
                    <span className="block font-semibold">
                      {t.days} {t.days === 1 ? 'day' : 'days'}
                    </span>
                    <span className="text-muted">{shortDate(t.startDate) ?? 'Dates open'}</span>
                  </span>
                  <span className="hidden text-sm sm:block">
                    <span className="block font-semibold">{inr(t.estimatedInr)}</span>
                    <span className="text-muted">{t.budgetInr ? `of ${inr(t.budgetInr)}` : 'no budget set'}</span>
                  </span>
                  <span className="hidden text-right text-sm text-muted sm:block">{relativeTime(t.updatedAt)}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
