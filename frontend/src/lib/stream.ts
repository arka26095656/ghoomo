import { api, ApiError, toApiError } from './api';
import type { ItineraryDetail } from '../types';

export interface StreamHandlers {
  onStatus?: (text: string) => void;
  onItinerary?: (it: ItineraryDetail) => void;
  onToken?: (text: string) => void;
  onDone?: (messageId: number, provider: string) => void;
  onError?: (message: string) => void;
}

/**
 * POSTs a chat message and reads the Server-Sent Events reply.
 * EventSource can't send a POST body or an Authorization header, so this parses the stream by hand.
 */
export async function sendChatMessage(
  sessionId: number,
  content: string,
  h: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  let res: Response;
  try {
    res = await fetch(`${api.base}/api/chat/sessions/${sessionId}/messages`, {
      method: 'POST',
      headers: { ...api.headers(true), Accept: 'text/event-stream, application/json' },
      body: JSON.stringify({ content }),
      signal,
    });
  } catch (e) {
    if ((e as Error).name === 'AbortError') return;
    throw new ApiError(0, "Can't reach the Ghoomo server. Check that the backend is running.");
  }
  if (!res.ok || !res.body) throw await toApiError(res);

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  const dispatch = (block: string) => {
    let event = 'message';
    const data: string[] = [];
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim();
      else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
    }
    if (!data.length) return;
    let payload: any;
    try {
      payload = JSON.parse(data.join('\n'));
    } catch {
      return;
    }
    switch (event) {
      case 'status':
        h.onStatus?.(payload.text);
        break;
      case 'itinerary':
        h.onItinerary?.(payload);
        break;
      case 'token':
        h.onToken?.(payload.text);
        break;
      case 'done':
        h.onDone?.(payload.messageId, payload.provider);
        break;
      case 'error':
        h.onError?.(payload.message);
        break;
    }
  };

  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n');
      let idx;
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        dispatch(buffer.slice(0, idx));
        buffer = buffer.slice(idx + 2);
      }
    }
    if (buffer.trim()) dispatch(buffer);
  } catch (e) {
    if ((e as Error).name !== 'AbortError') throw e;
  }
}
