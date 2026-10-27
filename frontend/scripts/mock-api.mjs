// A zero-dependency stand-in for the Spring Boot API, for UI work without Java.
// Same routes and payloads, in memory, with a fake streaming planner.
// Run: npm run mock   (listens on :8080, which the Vite dev proxy targets)
import http from 'node:http';

const PORT = Number(process.env.PORT ?? 8080);
const db = { users: [], sessions: [], messages: [], itineraries: [], seq: 1 };
const id = () => db.seq++;
const now = () => new Date().toISOString();

const CITIES = {
  goa: [15.4909, 73.8278], jaipur: [26.9124, 75.7873], udaipur: [24.5854, 73.7125], manali: [32.2432, 77.1892],
  darjeeling: [27.041, 88.2663], varanasi: [25.3176, 82.9739], kochi: [9.9312, 76.2673], rishikesh: [30.0869, 78.2676],
  alleppey: [9.4981, 76.3388], leh: [34.1526, 77.5771], kyoto: [35.0116, 135.7681], santorini: [36.3932, 25.4615],
};

// Name-only version of the backend's DestinationTheme, enough for UI work.
const THEME_NAMES = {
  rajasthan: ['rajasthan', 'jaipur', 'udaipur', 'jodhpur', 'jaisalmer', 'pushkar'],
  kerala: ['kerala', 'kochi', 'alleppey', 'alappuzha', 'munnar', 'varkala'],
  goa: ['goa'],
  himalayas: ['manali', 'leh', 'ladakh', 'shimla', 'darjeeling', 'gangtok', 'rishikesh'],
  japan: ['japan', 'tokyo', 'kyoto', 'osaka'],
  santorini: ['santorini', 'greece', 'mykonos', 'athens'],
};
const themeFor = (destination = '') => {
  const d = destination.toLowerCase();
  const hit = Object.entries(THEME_NAMES).find(([, names]) => names.some((n) => new RegExp(`\\b${n}\\b`).test(d)));
  return hit ? hit[0] : 'default';
};
const PLACES = {
  jaipur: [['Amber Fort', 'culture'], ['Laxmi Misthan Bhandar', 'food'], ['Hawa Mahal', 'sight'], ['Chokhi Dhani', 'food'],
    ['City Palace', 'culture'], ['Johari Bazaar', 'shopping'], ['Nahargarh Fort', 'sight'], ['Tapri Central', 'food'],
    ['Jal Mahal', 'sight'], ['Rawat Mishtan Bhandar', 'food'], ['Albert Hall Museum', 'culture'], ['Bar Palladio', 'nightlife']],
};

function send(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(body === undefined ? '' : JSON.stringify(body));
}
const readBody = (req) =>
  new Promise((resolve) => {
    let s = '';
    req.on('data', (c) => (s += c));
    req.on('end', () => {
      try { resolve(s ? JSON.parse(s) : {}); } catch { resolve({}); }
    });
  });
const userFrom = (req) => {
  const t = (req.headers.authorization ?? '').replace('Bearer ', '');
  return db.users.find((u) => u.token === t);
};
const profile = ({ token, password, ...u }) => u;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function parseIntent(text) {
  const lower = text.toLowerCase();
  const planning = /\b(plan|trip|itinerary|visit|travel|holiday|weekend|getaway|days? in|make it)\b/.test(lower);
  const dest = planning ? Object.keys(CITIES).find((c) => lower.includes(c) && !lower.includes('from ' + c)) : undefined;
  const days = Number(lower.match(/(\d{1,2})\s*-?\s*days?/)?.[1] ?? (lower.includes('weekend') ? 2 : 3));
  const b = lower.match(/(?:₹|rs\.?|under|budget)\s*([\d,]+)\s*(k)?/);
  const budget = b ? Number(b[1].replace(/,/g, '')) * (b[2] ? 1000 : 1) : null;
  return { dest, days: Math.min(days, 7), budget };
}

function buildPlan({ dest, days }) {
  const name = dest[0].toUpperCase() + dest.slice(1);
  const [lat, lng] = CITIES[dest];
  const base = PLACES[dest] ?? [['Old Quarter', 'sight'], ['Thali House', 'food'], ['Heritage Museum', 'culture'],
    ['Night Market', 'nightlife'], ['Sunset Point', 'nature'], ['Café Corner', 'food'], ['Craft Bazaar', 'shopping'], ['Lakeside Dhaba', 'food']]
    .map(([n, c]) => [`${name} ${n}`, c]);
  const times = ['09:00', '12:30', '15:30', '19:30'];
  const cost = { food: 700, nightlife: 1400, shopping: 900, culture: 300, sight: 200, nature: 0 };
  const themes = ['Forts and the old city', 'Markets and local flavours', 'Slow morning, sunset views', 'Hidden corners'];
  const plan = {
    title: `${days} days in ${name}`, destination: name,
    summary: `A ${days}-day plan for ${name} that balances big sights with long, unhurried meals.`,
    centerLat: lat, centerLng: lng, days: [], estimatedTotalInr: 0,
    tips: ['Start early: queues at the big sights build after 10am.', 'Carry cash for small eateries and autos.', 'Use app cabs from the airport; agree auto fares upfront.'],
    weather: Array.from({ length: days }, (_, i) => ({
      date: new Date(Date.now() + (i + 7) * 86400000).toISOString().slice(0, 10), maxC: 29 - i, minC: 17, rainChance: i === 1 ? 60 : 10, code: i === 1 ? 61 : 1,
    })),
  };
  let k = 0;
  for (let d = 1; d <= days; d++) {
    const activities = times.map((time, i) => {
      const [n, c] = base[k++ % base.length];
      const a = (k * 2.4) % (Math.PI * 2);
      const r = 0.01 + 0.003 * k;
      return { time, name: n, description: `A favourite stop for ${c === 'food' ? 'a proper local meal' : 'first-time visitors'}.`, category: c,
        lat: lat + r * Math.sin(a), lng: lng + r * Math.cos(a), estCostInr: cost[c] ?? 200 };
    });
    plan.days.push({ day: d, theme: themes[(d - 1) % themes.length], activities });
  }
  plan.estimatedTotalInr = plan.days.flatMap((d) => d.activities).reduce((s, a) => s + (a.estCostInr ?? 0), 0);
  return plan;
}

const detail = (it) => ({ ...it, plan: it.plan, themeKey: themeFor(it.plan.destination) });
const summary = ({ plan, userId, ...rest }) => rest;

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://x');
  const p = url.pathname;
  const m = req.method;
  const body = ['POST', 'PUT', 'PATCH'].includes(m) ? await readBody(req) : {};

  if (p === '/api/auth/register' && m === 'POST') {
    if (!body.email?.includes('@') || (body.password ?? '').length < 8 || !body.name)
      return send(res, 400, { status: 400, message: 'Some fields need fixing.', fields: { ...(!body.email?.includes('@') && { email: 'must be a well-formed email address' }), ...((body.password ?? '').length < 8 && { password: 'size must be between 8 and 100' }) } });
    if (db.users.some((u) => u.email === body.email)) return send(res, 409, { status: 409, message: 'An account with this email already exists. Log in instead.' });
    const u = { id: id(), name: body.name, email: body.email, password: body.password, homeCity: null, travelStyle: null, budgetInr: null, interests: [], token: 'tok-' + Math.random().toString(36).slice(2) };
    db.users.push(u);
    return send(res, 201, { token: u.token, user: profile(u) });
  }
  if (p === '/api/auth/login' && m === 'POST') {
    const u = db.users.find((x) => x.email === body.email && x.password === body.password);
    return u ? send(res, 200, { token: u.token, user: profile(u) }) : send(res, 401, { status: 401, message: 'Email or password is incorrect.' });
  }

  const user = userFrom(req);
  if (!user) return send(res, 401, { status: 401, message: 'Log in to continue.' });

  if (p === '/api/me') {
    if (m === 'PUT') Object.assign(user, body);
    return send(res, 200, profile(user));
  }
  if (p === '/api/chat/sessions' && m === 'GET')
    return send(res, 200, db.sessions.filter((s) => s.userId === user.id).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).map(({ userId, ...s }) => s));
  if (p === '/api/chat/sessions' && m === 'POST') {
    const s = { id: id(), userId: user.id, title: 'New trip chat', createdAt: now(), updatedAt: now() };
    db.sessions.push(s);
    return send(res, 201, s);
  }
  let mm;
  if ((mm = p.match(/^\/api\/chat\/sessions\/(\d+)$/)) && m === 'DELETE') {
    db.sessions = db.sessions.filter((s) => !(s.id === +mm[1] && s.userId === user.id));
    return send(res, 204);
  }
  if ((mm = p.match(/^\/api\/chat\/sessions\/(\d+)\/messages$/))) {
    const s = db.sessions.find((x) => x.id === +mm[1] && x.userId === user.id);
    if (!s) return send(res, 404, { status: 404, message: 'Chat not found.' });
    if (m === 'GET') return send(res, 200, db.messages.filter((x) => x.sessionId === s.id).map(({ sessionId, ...x }) => x));

    // POST → SSE
    const text = String(body.content ?? '').trim();
    db.messages.push({ id: id(), sessionId: s.id, role: 'user', content: text, itineraryId: null, createdAt: now() });
    if (s.title === 'New trip chat') s.title = text.slice(0, 60);
    s.updatedAt = now();
    res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' });
    const ev = (name, data) => res.write(`event:${name}\ndata:${JSON.stringify(data)}\n\n`);
    ev('status', { text: 'Understanding your request' });
    await sleep(500);
    // carry destination across turns
    const history = db.messages.filter((x) => x.sessionId === s.id && x.role === 'user').map((x) => x.content);
    let intent = parseIntent(text);
    if (!intent.dest && /make it|change|swap|cheaper|add/i.test(text)) {
      for (const h of history.slice(0, -1).reverse()) { const prev = parseIntent(h); if (prev.dest) { intent = { ...prev, days: text.match(/\d/) ? intent.days : prev.days }; break; } }
    }
    let reply, itineraryId = null;
    if (intent.dest) {
      for (const st of [`Finding ${intent.dest} on the map`, 'Looking up places to visit', 'Checking the forecast for your dates', `Drafting your ${intent.days}-day itinerary`]) {
        ev('status', { text: st }); await sleep(450);
      }
      const plan = buildPlan(intent);
      const it = { id: id(), userId: user.id, title: plan.title, destination: plan.destination, startDate: plan.weather[0].date, days: intent.days,
        budgetInr: intent.budget, estimatedInr: plan.estimatedTotalInr, createdAt: now(), updatedAt: now(), plan };
      db.itineraries.push(it);
      itineraryId = it.id;
      ev('itinerary', detail(it));
      reply = `Here's a ${intent.days}-day plan for ${plan.destination}. Day 2 looks rainy in the afternoon, so I've kept the outdoor stops in the morning. Activities and food come to about ₹${plan.estimatedTotalInr.toLocaleString('en-IN')}${intent.budget ? `, which leaves roughly ₹${(intent.budget - plan.estimatedTotalInr).toLocaleString('en-IN')} for travel and stay` : ''}. Want me to swap anything?`;
    } else {
      reply = /hi|hello|hey/i.test(text)
        ? 'Namaste! Tell me where you want to go, how many days and a rough budget, and I’ll lay out each day.'
        : 'For **Manali in December**, pack for real cold:\n\n- Thermal base layers and a down jacket\n- Waterproof boots with grip for snow\n- Gloves, woollen cap and lip balm\n\nWant me to plan the trip too?';
    }
    for (const word of reply.split(/(?<= )/)) { ev('token', { text: word }); await sleep(25); }
    const msg = { id: id(), sessionId: s.id, role: 'assistant', content: reply, itineraryId, createdAt: now() };
    db.messages.push(msg);
    ev('done', { messageId: msg.id, provider: 'mock' });
    return res.end();
  }

  if (p === '/api/itineraries' && m === 'GET')
    return send(res, 200, db.itineraries.filter((i) => i.userId === user.id).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).map(summary));
  if ((mm = p.match(/^\/api\/itineraries\/(\d+)$/))) {
    const it = db.itineraries.find((i) => i.id === +mm[1] && i.userId === user.id);
    if (!it) return send(res, 404, { status: 404, message: 'Itinerary not found.' });
    if (m === 'GET') return send(res, 200, summaryDetail(it));
    if (m === 'DELETE') { db.itineraries = db.itineraries.filter((i) => i !== it); return send(res, 204); }
    if (m === 'PUT') {
      it.title = body.title; it.startDate = body.startDate; it.budgetInr = body.budgetInr;
      it.plan = { ...body.plan, title: body.title, weather: it.plan.weather, centerLat: it.plan.centerLat, centerLng: it.plan.centerLng };
      it.days = it.plan.days.length;
      it.estimatedInr = it.plan.days.flatMap((d) => d.activities).reduce((s, a) => s + (a.estCostInr ?? 0), 0);
      it.plan.estimatedTotalInr = it.estimatedInr;
      it.updatedAt = now();
      return send(res, 200, summaryDetail(it));
    }
  }
  send(res, 404, { status: 404, message: 'No such endpoint.' });
});
const summaryDetail = ({ userId, ...it }) => it;

server.listen(PORT, () => console.log(`Mock Ghoomo API on http://localhost:${PORT}`));
