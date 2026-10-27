import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowUp } from 'lucide-react';
import { Logo } from '../components/Logo';
import { useAuth } from '../lib/auth';
import { EXAMPLE_PROMPTS } from '../components/ChatPanel';

export function Landing() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [prompt, setPrompt] = useState('');

  const go = (text: string) => {
    const q = encodeURIComponent(text.trim());
    if (!q) return;
    navigate(user ? `/plan?q=${q}` : `/register?q=${q}`);
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    go(prompt);
  };

  return (
    <div className="min-h-full">
      <header className="mx-auto flex max-w-6xl items-center justify-between px-5 py-5 sm:px-8">
        <Logo />
        <nav className="flex items-center gap-2">
          {user ? (
            <Link to="/plan" className="btn-primary">
              Open planner
            </Link>
          ) : (
            <>
              <Link to="/login" className="btn-quiet">
                Log in
              </Link>
              <Link to="/register" className="btn-primary">
                Sign up free
              </Link>
            </>
          )}
        </nav>
      </header>

      <main className="mx-auto max-w-6xl px-5 pb-20 sm:px-8">
        <section className="relative grid grid-cols-[minmax(0,1fr)] items-center gap-10 pt-10 md:grid-cols-[minmax(0,1.15fr)_minmax(0,0.85fr)] md:pt-20">
          <div className="min-w-0">
            <h1 className="text-[2.6rem] font-bold leading-[1.02] sm:text-6xl">
              Say where.
              <br />
              Get every day
              <br />
              mapped out.
            </h1>
            <p className="mt-5 max-w-[46ch] text-lg text-muted">
              Ghoomo turns one sentence into a day-by-day trip: real places from OpenStreetMap, the live forecast for
              your dates, and costs in rupees. Then you chat to change it.
            </p>

            <form onSubmit={submit} className="mt-8 max-w-xl">
              <label htmlFor="hero-prompt" className="sr-only">
                Describe your trip
              </label>
              <div className="flex items-center gap-2 rounded-full border border-line bg-surface p-1.5 pl-5 shadow-[0_1px_0_rgb(var(--line))] focus-within:border-ink/40 focus-within:ring-2 focus-within:ring-marigold/40">
                <input
                  id="hero-prompt"
                  value={prompt}
                  onChange={(e) => setPrompt(e.target.value)}
                  placeholder="4 days in Goa under ₹25,000"
                  className="min-w-0 flex-1 bg-transparent py-2 text-base placeholder:text-muted/70 focus:outline-none"
                />
                <button type="submit" className="btn-primary px-5 py-2.5" disabled={!prompt.trim()}>
                  Plan it <ArrowUp size={16} className="rotate-90" aria-hidden="true" />
                </button>
              </div>
            </form>
            <div className="mt-4 flex max-w-xl flex-wrap gap-2">
              {EXAMPLE_PROMPTS.slice(0, 3).map((p) => (
                <button
                  key={p}
                  type="button"
                  onClick={() => go(p)}
                  className="rounded-full border border-line px-3 py-1 text-sm text-muted hover:border-ink/30 hover:text-ink"
                >
                  {p}
                </button>
              ))}
            </div>
          </div>

          <RoutePreview />
        </section>

        <section className="mt-24 grid gap-10 border-t border-line pt-10 md:grid-cols-3">
          {[
            {
              title: 'Grounded, not guessed',
              body: 'Before writing a plan, Ghoomo looks up the destination, nearby attractions and the weather, and hands the AI that data.',
            },
            {
              title: 'Yours to rearrange',
              body: 'Drag stops between times, drop what you don’t fancy, and the running cost updates as you go.',
            },
            {
              title: 'Remembers how you travel',
              body: 'Set your home city, budget and interests once. Every plan starts from them.',
            },
          ].map((f) => (
            <div key={f.title}>
              <h2 className="text-xl font-semibold">{f.title}</h2>
              <p className="mt-2 text-muted">{f.body}</p>
            </div>
          ))}
        </section>
      </main>

      <footer className="border-t border-line py-6 text-center text-sm text-muted">
        Built with React, Spring Boot and free APIs. Map data © OpenStreetMap contributors.
      </footer>
    </div>
  );
}

/** A miniature itinerary drawn as a route: the landing page's one animated moment. */
function RoutePreview() {
  const stops = [
    { y: 40, time: '09:00', name: 'Amber Fort', note: 'Sight' },
    { y: 128, time: '13:00', name: 'Laxmi Misthan Bhandar', note: 'Pyaaz kachori' },
    { y: 216, time: '16:30', name: 'Hawa Mahal', note: 'Golden hour' },
    { y: 304, time: '19:30', name: 'Chokhi Dhani', note: 'Dinner & folk dance' },
  ];
  return (
    <div className="panel relative mx-auto w-full max-w-sm p-6" aria-hidden="true">
      <div className="flex items-baseline gap-3">
        <span className="font-display text-5xl font-bold leading-none text-marigold">1</span>
        <div>
          <p className="font-display text-lg font-semibold leading-tight">Forts and the old city</p>
          <p className="text-sm text-muted">Jaipur · Sunny, 29° / 17°</p>
        </div>
      </div>
      <svg viewBox="0 0 300 340" className="mt-4 w-full">
        <path
          d="M22 40 C 22 80, 22 90, 22 128 S 22 180, 22 216 S 22 270, 22 304"
          fill="none"
          className="route-draw stroke-marigold"
          strokeWidth="3"
          strokeLinecap="round"
        />
        {stops.map((s) => (
          <g key={s.name}>
            <circle cx="22" cy={s.y} r="7" className="fill-surface stroke-marigold" strokeWidth="3" />
            <text x="44" y={s.y - 4} className="fill-muted" fontSize="12" fontWeight="600">
              {s.time}
            </text>
            <text x="44" y={s.y + 14} className="fill-ink" fontSize="15" fontWeight="600">
              {s.name}
            </text>
            <text x="44" y={s.y + 31} className="fill-muted" fontSize="12">
              {s.note}
            </text>
          </g>
        ))}
      </svg>
    </div>
  );
}
