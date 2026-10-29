import { useEffect, useState, type FormEvent } from 'react';
import { useLocation } from 'react-router-dom';
import clsx from 'clsx';
import { api, ApiError } from '../lib/api';
import { useAuth } from '../lib/auth';
import type { Profile } from '../types';

const INTERESTS = ['beaches', 'mountains', 'food', 'history', 'temples', 'trekking', 'wildlife', 'nightlife', 'shopping', 'photography', 'art', 'slow travel'];
const STYLES: { value: NonNullable<Profile['travelStyle']>; label: string; hint: string }[] = [
  { value: 'budget', label: 'Budget', hint: 'Hostels, street food, public transport' },
  { value: 'comfort', label: 'Comfort', hint: 'Good hotels, cabs when needed' },
  { value: 'luxury', label: 'Luxury', hint: 'Boutique stays, fine dining' },
];

export function Settings() {
  const { user, setUser } = useAuth();
  const { hash } = useLocation();

  // The account menu links to #account and #preferences; scroll to the matching section.
  useEffect(() => {
    if (!hash) return;
    document.getElementById(hash.slice(1))?.scrollIntoView({ block: 'start' });
  }, [hash]);
  const [name, setName] = useState(user?.name ?? '');
  const [homeCity, setHomeCity] = useState(user?.homeCity ?? '');
  const [style, setStyle] = useState<Profile['travelStyle']>(user?.travelStyle ?? null);
  const [budget, setBudget] = useState(user?.budgetInr ? String(user.budgetInr) : '');
  const [interests, setInterests] = useState<string[]>(user?.interests ?? []);
  const [status, setStatus] = useState<{ ok: boolean; text: string } | null>(null);
  const [saving, setSaving] = useState(false);

  const toggle = (i: string) =>
    setInterests((cur) => (cur.includes(i) ? cur.filter((x) => x !== i) : [...cur, i]));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setStatus(null);
    try {
      const updated = await api.updateMe({
        name: name.trim(),
        homeCity: homeCity.trim() || null,
        travelStyle: style,
        budgetInr: budget ? Number(budget) : null,
        interests,
      });
      setUser(updated);
      setStatus({ ok: true, text: 'Saved. New plans will use your preferences.' });
    } catch (err) {
      setStatus({ ok: false, text: err instanceof ApiError ? err.message : 'Could not save your changes.' });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="h-full overflow-y-auto">
      <form onSubmit={submit} className="mx-auto max-w-2xl px-5 py-8 sm:px-8">
        <h1 className="text-3xl font-semibold">Settings</h1>

        <section id="account" className="mt-8 scroll-mt-6" aria-labelledby="account-heading">
          <h2 id="account-heading" className="text-xl font-semibold">
            Account
          </h2>
          <div className="mt-4 grid gap-5 sm:grid-cols-2">
            <div>
              <label className="label" htmlFor="name">
                Name
              </label>
              <input id="name" className="field" value={name} onChange={(e) => setName(e.target.value)} required />
            </div>
            <div>
              <label className="label" htmlFor="email">
                Email
              </label>
              <input id="email" className="field cursor-not-allowed text-muted" value={user?.email ?? ''} readOnly />
              <p className="mt-1 text-sm text-muted">Your email is your login and can't be changed here.</p>
            </div>
          </div>
        </section>

        <section id="preferences" className="mt-10 scroll-mt-6 border-t border-line pt-8" aria-labelledby="prefs-heading">
          <h2 id="prefs-heading" className="text-xl font-semibold">
            Travel preferences
          </h2>
          <p className="mt-1 text-muted">Ghoomo reads these before every plan, so you don't have to repeat yourself.</p>

        <div className="mt-6 grid gap-5 sm:grid-cols-2">
          <div>
            <label className="label" htmlFor="home">
              Home city
            </label>
            <input id="home" className="field" value={homeCity} onChange={(e) => setHomeCity(e.target.value)} placeholder="e.g. Kolkata" />
          </div>
        </div>

        <fieldset className="mt-8">
          <legend className="label">How do you like to travel?</legend>
          <div className="grid gap-2 sm:grid-cols-3">
            {STYLES.map((s) => (
              <label
                key={s.value}
                className={clsx(
                  'cursor-pointer rounded-xl border p-3.5 transition-colors',
                  style === s.value ? 'border-ink bg-surface ring-1 ring-ink' : 'border-line hover:border-ink/30',
                )}
              >
                <input
                  type="radio"
                  name="style"
                  value={s.value}
                  checked={style === s.value}
                  onChange={() => setStyle(s.value)}
                  className="sr-only"
                />
                <span className="block font-semibold">{s.label}</span>
                <span className="text-sm text-muted">{s.hint}</span>
              </label>
            ))}
          </div>
        </fieldset>

        <div className="mt-8 max-w-xs">
          <label className="label" htmlFor="budget">
            Usual trip budget (₹)
          </label>
          <input
            id="budget"
            inputMode="numeric"
            className="field"
            value={budget}
            onChange={(e) => setBudget(e.target.value.replace(/\D/g, ''))}
            placeholder="e.g. 25000"
          />
        </div>

        <fieldset className="mt-8">
          <legend className="label">What do you enjoy?</legend>
          <div className="flex flex-wrap gap-2">
            {INTERESTS.map((i) => {
              const on = interests.includes(i);
              return (
                <button
                  key={i}
                  type="button"
                  aria-pressed={on}
                  onClick={() => toggle(i)}
                  className={clsx(
                    'rounded-full border px-3.5 py-1.5 text-sm font-medium capitalize transition-colors',
                    on ? 'border-leaf bg-leaf text-white' : 'border-line hover:border-ink/30',
                  )}
                >
                  {i}
                </button>
              );
            })}
          </div>
        </fieldset>

        </section>

        <div className="mt-10 flex items-center gap-4">
          <button type="submit" className="btn-primary px-6 py-2.5" disabled={saving || !name.trim()}>
            {saving ? 'Saving…' : 'Save changes'}
          </button>
          {status && (
            <p className={clsx('text-sm', status.ok ? 'text-leaf' : 'text-danger')} role="status">
              {status.text}
            </p>
          )}
        </div>
      </form>
    </div>
  );
}
