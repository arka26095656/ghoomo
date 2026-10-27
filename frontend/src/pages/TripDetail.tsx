import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Check } from 'lucide-react';
import { RouteItinerary } from '../components/RouteItinerary';
import { TripMap } from '../components/TripMap';
import { api, ApiError } from '../lib/api';
import { useDestinationTheme } from '../lib/theme';
import type { ItineraryDetail, ItineraryPlan } from '../types';

export function TripDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [trip, setTrip] = useState<ItineraryDetail | null>(null);
  const [plan, setPlan] = useState<ItineraryPlan | null>(null);
  const [title, setTitle] = useState('');
  const [startDate, setStartDate] = useState('');
  const [budget, setBudget] = useState('');
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [savedAt, setSavedAt] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [focusedDay, setFocusedDay] = useState<number | null>(null);

  useDestinationTheme(trip?.themeKey);

  useEffect(() => {
    api
      .itinerary(Number(id))
      .then((t) => {
        setTrip(t);
        setPlan(t.plan);
        setTitle(t.title);
        setStartDate(t.startDate ?? '');
        setBudget(t.budgetInr ? String(t.budgetInr) : '');
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Could not load this trip.'));
  }, [id]);

  const touch = () => {
    setDirty(true);
    setSavedAt(null);
  };

  const save = async () => {
    if (!trip || !plan) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await api.updateItinerary(trip.id, {
        title: title.trim() || plan.title,
        startDate: startDate || null,
        budgetInr: budget ? Number(budget) : null,
        plan,
      });
      setTrip(updated);
      setPlan(updated.plan);
      setDirty(false);
      setSavedAt(Date.now());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save changes.');
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!trip || !window.confirm(`Delete “${trip.title}”? This can’t be undone.`)) return;
    await api.deleteItinerary(trip.id);
    navigate('/trips', { replace: true });
  };

  if (error && !trip) {
    return (
      <div className="p-8">
        <p className="text-danger">{error}</p>
        <Link to="/trips" className="btn-quiet mt-4">
          Back to My trips
        </Link>
      </div>
    );
  }
  if (!trip || !plan) return <p className="p-8 text-muted">Loading trip…</p>;

  return (
    <div className="grid h-full min-h-0 grid-rows-[260px_1fr] lg:grid-cols-[minmax(0,1fr)_minmax(0,0.85fr)] lg:grid-rows-1">
      <div className="p-3 lg:order-2 lg:p-4 lg:pl-0">
        <TripMap plan={plan} focusedDay={focusedDay} className="h-full" />
      </div>

      <div className="min-h-0 overflow-y-auto lg:order-1">
        <div className="sticky top-0 z-10 flex items-center gap-2 border-b border-line bg-paper/95 px-5 py-2.5 backdrop-blur sm:px-8">
          <Link to="/trips" className="btn-quiet -ml-3 px-3" aria-label="Back to My trips">
            <ArrowLeft size={16} /> <span className="hidden sm:inline">My trips</span>
          </Link>
          <span className="ml-auto text-sm text-muted" aria-live="polite">
            {saving ? 'Saving…' : savedAt ? (
              <span className="inline-flex items-center gap-1 text-leaf">
                <Check size={14} /> Saved
              </span>
            ) : dirty ? 'Unsaved changes' : ''}
          </span>
          <button className="btn-quiet text-danger" onClick={remove}>
            Delete
          </button>
          <button className="btn-primary" onClick={save} disabled={!dirty || saving}>
            Save changes
          </button>
        </div>

        <div className="px-5 pt-6 sm:px-8">
          <div className="grid gap-4 sm:grid-cols-[1fr_10rem_9rem]">
            <div>
              <label className="label" htmlFor="title">
                Trip name
              </label>
              <input
                id="title"
                className="field"
                value={title}
                onChange={(e) => {
                  setTitle(e.target.value);
                  touch();
                }}
                maxLength={200}
              />
            </div>
            <div>
              <label className="label" htmlFor="start">
                Start date
              </label>
              <input
                id="start"
                type="date"
                className="field"
                value={startDate}
                onChange={(e) => {
                  setStartDate(e.target.value);
                  touch();
                }}
              />
            </div>
            <div>
              <label className="label" htmlFor="budget">
                Budget (₹)
              </label>
              <input
                id="budget"
                inputMode="numeric"
                className="field"
                value={budget}
                onChange={(e) => {
                  setBudget(e.target.value.replace(/\D/g, ''));
                  touch();
                }}
                placeholder="e.g. 20000"
              />
            </div>
          </div>
          {error && <p className="mt-3 text-sm text-danger">{error}</p>}
          <p className="mt-4 text-sm text-muted">Drag stops to reorder them within a day, or remove what you don't need.</p>

          <div className="mt-6">
            <RouteItinerary
              plan={{ ...plan, title: title || plan.title }}
              budgetInr={budget ? Number(budget) : null}
              editable
              onChange={(p) => {
                setPlan(p);
                touch();
              }}
              focusedDay={focusedDay}
              onFocusDay={setFocusedDay}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
