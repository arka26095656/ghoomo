import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { History, Plus, Trash2 } from 'lucide-react';
import clsx from 'clsx';
import { ChatPanel } from '../components/ChatPanel';
import { DestinationBanner } from '../components/DestinationBanner';
import { RouteItinerary } from '../components/RouteItinerary';
import { TripMap } from '../components/TripMap';
import { api, ApiError } from '../lib/api';
import { sendChatMessage } from '../lib/stream';
import { relativeTime } from '../lib/format';
import { useDestinationTheme } from '../lib/theme';
import { useDismiss } from '../lib/useDismiss';
import type { ChatMessage, ChatSession, ItineraryDetail } from '../types';

export function Planner() {
  const [params, setParams] = useSearchParams();
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [itinerary, setItinerary] = useState<ItineraryDetail | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [focusedDay, setFocusedDay] = useState<number | null>(null);
  const [showHistory, setShowHistory] = useState(false);
  const [mobileView, setMobileView] = useState<'chat' | 'plan'>('chat');
  const abortRef = useRef<AbortController | null>(null);
  const startedFromQuery = useRef(false);
  const historyButtonRef = useRef<HTMLButtonElement>(null);
  const historyPanelRef = useRef<HTMLDivElement>(null);

  // Close the history list on a click outside it or Escape.
  const closeHistory = useCallback(() => setShowHistory(false), []);
  useDismiss(showHistory, closeHistory, [historyButtonRef, historyPanelRef]);

  // The whole app takes on the destination's colours while a trip is open.
  useDestinationTheme(itinerary?.themeKey);

  const refreshSessions = useCallback(() => api.sessions().then(setSessions).catch(() => {}), []);

  const openItinerary = useCallback(async (id: number) => {
    try {
      setItinerary(await api.itinerary(id));
      setFocusedDay(null);
      setMobileView('plan');
    } catch {
      /* deleted itinerary: ignore */
    }
  }, []);

  const loadSession = useCallback(
    async (id: number) => {
      setSessionId(id);
      setShowHistory(false);
      setItinerary(null);
      const msgs = await api.messages(id);
      setMessages(msgs);
      const lastWithPlan = [...msgs].reverse().find((m) => m.itineraryId != null);
      if (lastWithPlan?.itineraryId) {
        await openItinerary(lastWithPlan.itineraryId);
        setMobileView('chat');
      }
    },
    [openItinerary],
  );

  const newChat = () => {
    abortRef.current?.abort();
    setSessionId(null);
    setMessages([]);
    setItinerary(null);
    setShowHistory(false);
    setMobileView('chat');
  };

  const send = useCallback(
    async (text: string) => {
      if (busy) return;
      setBusy(true);
      setStatus(null);
      const userMsg: ChatMessage = { id: `u-${Date.now()}`, role: 'user', content: text, itineraryId: null };
      const replyId = `a-${Date.now()}`;
      setMessages((m) => [...m, userMsg, { id: replyId, role: 'assistant', content: '', itineraryId: null, pending: true }]);
      const patchReply = (fn: (m: ChatMessage) => ChatMessage) =>
        setMessages((all) => all.map((m) => (m.id === replyId ? fn(m) : m)));

      const controller = new AbortController();
      abortRef.current = controller;
      try {
        let sid = sessionId;
        if (sid == null) {
          const s = await api.createSession();
          sid = s.id;
          setSessionId(s.id);
        }
        await sendChatMessage(
          sid,
          text,
          {
            onStatus: setStatus,
            onItinerary: (it) => {
              setItinerary(it);
              setFocusedDay(null);
              patchReply((m) => ({ ...m, itineraryId: it.id }));
            },
            onToken: (t) => patchReply((m) => ({ ...m, content: m.content + t })),
            onDone: (messageId, provider) => patchReply((m) => ({ ...m, id: messageId, pending: false, provider })),
            onError: (message) => patchReply((m) => ({ ...m, content: message, pending: false, error: true })),
          },
          controller.signal,
        );
      } catch (e) {
        const message = e instanceof ApiError ? e.message : 'Something went wrong. Try again.';
        patchReply((m) => ({ ...m, content: message, pending: false, error: true }));
      } finally {
        patchReply((m) => (m.pending ? { ...m, pending: false, content: m.content || 'Stopped.' } : m));
        setBusy(false);
        setStatus(null);
        abortRef.current = null;
        refreshSessions();
      }
    },
    [busy, sessionId, refreshSessions],
  );

  useEffect(() => {
    refreshSessions();
  }, [refreshSessions]);

  // "Chat history" in the account menu links to /plan?history=1
  useEffect(() => {
    if (params.get('history') === '1') {
      setShowHistory(true);
      setMobileView('chat');
      const next = new URLSearchParams(params);
      next.delete('history');
      setParams(next, { replace: true });
    }
  }, [params, setParams]);

  // Prompt typed on the landing page: /plan?q=...
  useEffect(() => {
    const q = params.get('q');
    if (q && !startedFromQuery.current) {
      startedFromQuery.current = true;
      setParams({}, { replace: true });
      send(q);
    }
  }, [params, setParams, send]);

  const deleteSession = async (id: number) => {
    await api.deleteSession(id).catch(() => {});
    if (id === sessionId) newChat();
    refreshSessions();
  };

  return (
    <div className="flex h-full min-h-0 flex-col lg:flex-row">
      {/* Mobile switcher */}
      <div className="flex shrink-0 gap-1 border-b border-line p-2 lg:hidden" role="tablist">
        {(['chat', 'plan'] as const).map((v) => (
          <button
            key={v}
            role="tab"
            aria-selected={mobileView === v}
            onClick={() => setMobileView(v)}
            className={clsx('flex-1 rounded-full py-1.5 text-sm font-medium', mobileView === v ? 'bg-brand text-on-brand' : 'text-muted')}
          >
            {v === 'chat' ? 'Chat' : 'Itinerary'}
          </button>
        ))}
      </div>

      {/* Chat column */}
      <section
        className={clsx(
          'relative min-h-0 flex-1 flex-col border-line lg:flex lg:max-w-[440px] lg:border-r',
          mobileView === 'chat' ? 'flex' : 'hidden',
        )}
        aria-label="Chat"
      >
        <div className="flex h-12 shrink-0 items-center gap-1 border-b border-line px-3">
          <h1 className="min-w-0 flex-1 truncate px-1 font-sans text-sm font-semibold">
            {sessions.find((s) => s.id === sessionId)?.title ?? 'New trip chat'}
          </h1>
          <button
            ref={historyButtonRef}
            className="btn-quiet px-2.5 py-1.5"
            onClick={() => setShowHistory((v) => !v)}
            aria-expanded={showHistory}
          >
            <History size={16} aria-hidden="true" />
            <span className="sr-only sm:not-sr-only">History</span>
          </button>
          <button className="btn-quiet px-2.5 py-1.5" onClick={newChat}>
            <Plus size={16} aria-hidden="true" />
            <span className="sr-only sm:not-sr-only">New</span>
          </button>
        </div>

        {showHistory && (
          <div
            ref={historyPanelRef}
            className="absolute inset-x-0 top-12 z-20 max-h-[60%] overflow-y-auto border-b border-line bg-surface shadow-lg"
          >
            {sessions.length === 0 ? (
              <p className="p-4 text-sm text-muted">Your past chats will show up here.</p>
            ) : (
              <ul>
                {sessions.map((s) => (
                  <li key={s.id} className="group flex items-center border-b border-line last:border-0">
                    <button
                      className={clsx('min-w-0 flex-1 px-4 py-2.5 text-left hover:bg-sunk', s.id === sessionId && 'bg-sunk')}
                      onClick={() => loadSession(s.id)}
                    >
                      <span className="block truncate text-sm font-medium">{s.title}</span>
                      <span className="text-xs text-muted">{relativeTime(s.updatedAt)}</span>
                    </button>
                    <button
                      className="mr-2 rounded-md p-1.5 text-muted opacity-0 hover:text-danger group-hover:opacity-100 focus:opacity-100"
                      onClick={() => deleteSession(s.id)}
                      aria-label={`Delete chat ${s.title}`}
                    >
                      <Trash2 size={15} />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        <ChatPanel
          messages={messages}
          status={status}
          busy={busy}
          onSend={send}
          onStop={() => abortRef.current?.abort()}
          onOpenItinerary={openItinerary}
          activeItineraryId={itinerary?.id ?? null}
        />
      </section>

      {/* Itinerary column */}
      <section
        className={clsx('min-h-0 flex-[1.6] overflow-hidden lg:block', mobileView === 'plan' ? 'block' : 'hidden')}
        aria-label="Itinerary"
      >
        {itinerary ? (
          <div className="grid h-full min-h-0 grid-rows-[240px_1fr] xl:grid-cols-[minmax(0,1fr)_minmax(0,0.9fr)] xl:grid-rows-1">
            <div className="p-3 xl:order-2 xl:p-4 xl:pl-0">
              <TripMap plan={itinerary.plan} focusedDay={focusedDay} className="h-full" />
            </div>
            <div className="min-h-0 overflow-y-auto px-5 pt-5 sm:px-8 xl:order-1">
              <DestinationBanner itinerary={itinerary} />
              <RouteItinerary
                plan={itinerary.plan}
                budgetInr={itinerary.budgetInr}
                focusedDay={focusedDay}
                onFocusDay={setFocusedDay}
              />
            </div>
          </div>
        ) : (
          <EmptyPlan busy={busy} status={status} />
        )}
      </section>
    </div>
  );
}

function EmptyPlan({ busy, status }: { busy: boolean; status: string | null }) {
  return (
    <div className="flex h-full items-center justify-center p-8">
      <div className="max-w-sm text-center">
        <svg viewBox="0 0 220 120" className="mx-auto h-28 w-auto" aria-hidden="true">
          <path
            d="M20 95 C 60 95, 50 30, 100 40 S 150 100, 200 25"
            fill="none"
            className={clsx('stroke-marigold', busy && 'route-draw')}
            strokeWidth="4"
            strokeLinecap="round"
            strokeDasharray={busy ? undefined : '2 10'}
          />
          <circle cx="20" cy="95" r="7" className="fill-ink" />
          <circle cx="200" cy="25" r="7" className="fill-leaf" />
        </svg>
        <p className="mt-4 font-display text-xl font-semibold">
          {busy ? status ?? 'Working on it' : 'Your itinerary appears here'}
        </p>
        <p className="mt-1 text-muted">
          {busy
            ? 'Hang on, this takes a few seconds.'
            : 'Each day gets a route of stops, costs and the forecast, pinned on the map.'}
        </p>
      </div>
    </div>
  );
}
