import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { ArrowUp, Loader2, Route, Square } from 'lucide-react';
import clsx from 'clsx';
import type { ChatMessage } from '../types';
import { RichText } from './RichText';

export const EXAMPLE_PROMPTS = [
  'Plan 3 days in Jaipur under ₹15,000',
  '4 days in Goa from Kolkata, we love beaches and seafood, budget 25k',
  'A slow weekend in Darjeeling for two',
  'What should I pack for Manali in December?',
];

interface Props {
  messages: ChatMessage[];
  status: string | null;
  busy: boolean;
  onSend: (text: string) => void;
  onStop: () => void;
  onOpenItinerary: (id: number) => void;
  activeItineraryId: number | null;
}

export function ChatPanel({ messages, status, busy, onSend, onStop, onOpenItinerary, activeItineraryId }: Props) {
  const [draft, setDraft] = useState('');
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages, status]);

  useEffect(() => {
    const el = inputRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 160) + 'px';
  }, [draft]);

  const submit = (e?: FormEvent) => {
    e?.preventDefault();
    const text = draft.trim();
    if (!text || busy) return;
    onSend(text);
    setDraft('');
  };

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      submit();
    }
  };

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div ref={scrollRef} className="min-h-0 flex-1 overflow-y-auto px-4 py-5 sm:px-5" aria-live="polite">
        {messages.length === 0 ? (
          <div className="mx-auto max-w-sm pt-8">
            <h2 className="text-2xl font-semibold leading-tight">Where do you want to go?</h2>
            <p className="mt-2 text-muted">
              Say the place, how many days and a budget. Ghoomo checks real places and the forecast, then lays out
              each day.
            </p>
            <div className="mt-5 flex flex-col items-start gap-2">
              {EXAMPLE_PROMPTS.map((p) => (
                <button
                  key={p}
                  type="button"
                  onClick={() => onSend(p)}
                  disabled={busy}
                  className="rounded-2xl border border-line bg-surface px-3.5 py-2 text-left text-sm hover:border-ink/30"
                >
                  {p}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <ul className="space-y-4">
            {messages.map((m) => (
              <li key={m.id} className={clsx('flex', m.role === 'user' ? 'justify-end' : 'justify-start')}>
                <div
                  className={clsx(
                    'max-w-[88%] text-[0.95rem]',
                    m.role === 'user'
                      ? 'theme-fade rounded-2xl rounded-br-md bg-bubble px-3.5 py-2 text-on-bubble'
                      : clsx('py-1', m.error && 'text-danger'),
                  )}
                >
                  {m.role === 'assistant' ? (
                    <>
                      {m.content ? (
                        <div className={clsx(m.pending && 'caret')}>
                          <RichText text={m.content} />
                        </div>
                      ) : m.pending ? (
                        <p className="inline-flex items-center gap-2 text-muted">
                          <Loader2 size={15} className="animate-spin" aria-hidden="true" />
                          {status ?? 'Thinking'}…
                        </p>
                      ) : null}
                      {m.itineraryId != null && (
                        <button
                          type="button"
                          onClick={() => onOpenItinerary(m.itineraryId!)}
                          className={clsx(
                            'mt-2 inline-flex items-center gap-2 rounded-full border px-3 py-1 text-sm font-medium',
                            activeItineraryId === m.itineraryId
                              ? 'border-marigold bg-marigold/10'
                              : 'border-line hover:border-ink/30',
                          )}
                        >
                          <Route size={14} aria-hidden="true" />
                          {activeItineraryId === m.itineraryId ? 'Showing this itinerary' : 'Show itinerary'}
                        </button>
                      )}
                    </>
                  ) : (
                    <span className="whitespace-pre-wrap">{m.content}</span>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      <form onSubmit={submit} className="shrink-0 border-t border-line p-3 sm:p-4">
        <div className="flex items-end gap-2 rounded-2xl border border-line bg-surface p-1.5 pl-3.5 focus-within:border-ink/40 focus-within:ring-2 focus-within:ring-marigold/40">
          <label htmlFor="chat-input" className="sr-only">
            Message Ghoomo
          </label>
          <textarea
            id="chat-input"
            ref={inputRef}
            rows={1}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder={messages.length ? 'Ask for changes, e.g. "make day 2 a beach day"' : 'e.g. 5 days in Kerala in December'}
            className="max-h-40 min-h-[2.25rem] flex-1 resize-none bg-transparent py-1.5 text-[0.95rem] placeholder:text-muted/70 focus:outline-none"
            maxLength={2000}
          />
          {busy ? (
            <button type="button" onClick={onStop} className="btn-primary h-9 w-9 p-0" aria-label="Stop reply">
              <Square size={14} fill="currentColor" />
            </button>
          ) : (
            <button type="submit" className="btn-primary h-9 w-9 p-0" disabled={!draft.trim()} aria-label="Send">
              <ArrowUp size={18} />
            </button>
          )}
        </div>
      </form>
    </div>
  );
}
