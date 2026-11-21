// SkillForge chatbot backend. The AI API key exists ONLY here, as an environment variable.
import express from 'express';
import crypto from 'node:crypto';

const { ANTHROPIC_API_KEY, APP_TOKEN, ANTHROPIC_MODEL = 'claude-sonnet-5-5', PORT = 8787,
        ANTHROPIC_BASE_URL = 'https://api.anthropic.com' } = process.env;
if (!ANTHROPIC_API_KEY || !APP_TOKEN) { console.error('Set ANTHROPIC_API_KEY and APP_TOKEN environment variables.'); process.exit(1); }

const app = express();
app.use(express.json({ limit: '300kb' }));

// --- auth + simple rate limit (protects your API bill) ---
const safeEq = (a, b) => { const x = Buffer.from(a), y = Buffer.from(b); return x.length === y.length && crypto.timingSafeEqual(x, y); };
const hits = new Map();
const limited = (ip) => { const now = Date.now(), r = (hits.get(ip) || []).filter(t => now - t < 60000); r.push(now); hits.set(ip, r); return r.length > 20; };

const str = (d) => ({ type: 'string', description: d });
const goalFields = {
  title: str('Short goal title'), description: str('Optional details'),
  category: str('e.g. C++ Programming, Python Programming, Data Structures & Algorithms, AI & Machine Learning, Web Development, App Development, Projects & Prototyping, College Academics, English Communication, Problem Solving, Personal Development'),
  priority: { type: 'string', enum: ['low', 'medium', 'high'] },
  frequency: { type: 'string', enum: ['daily', 'weekly', 'monthly', 'custom', 'once'] },
  custom_days: { type: 'array', items: { type: 'integer', minimum: 0, maximum: 6 }, description: 'For frequency=custom. 0=Monday ... 6=Sunday' },
  start_date: str('YYYY-MM-DD, default today'),
  deadline: str('YYYY-MM-DD. For one-time tasks this is the DUE DATE (use it, e.g. for "tomorrow")'),
  est_minutes: { type: 'integer' }, reminder_time: str('24h HH:mm in the user local timezone, e.g. 19:00 for 7 PM'),
};
const tools = [
  { name: 'create_goal', description: 'Propose creating a new goal/task. The user must confirm before it is created.', input_schema: { type: 'object', properties: goalFields, required: ['title', 'frequency'] } },
  { name: 'update_goal', description: 'Propose editing an existing goal (use its id from app data). Include only fields that change.', input_schema: { type: 'object', properties: { goal_id: { type: 'integer' }, ...goalFields }, required: ['goal_id'] } },
  { name: 'complete_goal', description: 'Propose marking a goal done for its current period.', input_schema: { type: 'object', properties: { goal_id: { type: 'integer' } }, required: ['goal_id'] } },
  { name: 'reschedule_goal', description: 'Propose moving a goal/task to another date.', input_schema: { type: 'object', properties: { goal_id: { type: 'integer' }, new_date: str('YYYY-MM-DD') }, required: ['goal_id', 'new_date'] } },
  { name: 'delete_goal', description: 'Propose deleting a goal. Only when the user clearly asks.', input_schema: { type: 'object', properties: { goal_id: { type: 'integer' } }, required: ['goal_id'] } },
  { name: 'save_memory', description: 'Save a SHORT, useful, NON-SENSITIVE planning fact (skill level, learning goals, preferred study schedule, long-term targets). Never save passwords, IDs, health, financial or contact data.', input_schema: { type: 'object', properties: { key: str('snake_case key'), value: str('short value') }, required: ['key', 'value'] } },
];

const system = (ctx) => `You are SkillForge AI, the personal study planner, coding mentor and productivity coach of a B.Tech AI/ML student.
Be concise, warm and practical. Plain text; short lists are fine.

RULES
- The JSON below is the user's REAL app data and is the only source of truth about their goals, stats and saved memory. Never invent goals or numbers. If asked about goals, answer from this data (use done_this_period / due_today).
- You cannot change data directly. To create/edit/complete/reschedule/delete goals, call the matching tool: it only PROPOSES the action and the user confirms in the app. Never say something is already done; say you've prepared it for confirmation.
- Convert relative dates ("tomorrow", "Friday") using today's date and weekday. Times are 24h in the user's timezone.
- If a needed detail is missing and cannot be reasonably assumed, ask one short question instead of guessing.
- For big planning requests (weekly plan, monthly targets, breaking a goal down), give the plan in text and propose the concrete goals via tools (max ~6 proposals at a time).
- Save to memory only stable, useful, non-sensitive planning context, and mention briefly what you saved.

APP DATA:
${JSON.stringify(ctx)}`;

// Anthropic requires alternating roles starting with "user".
const clean = (msgs) => {
  const out = [];
  for (const m of (Array.isArray(msgs) ? msgs : []).slice(-30)) {
    if (!['user', 'assistant'].includes(m?.role) || typeof m.content !== 'string' || !m.content.trim()) continue;
    const text = m.content.slice(0, 4000);
    if (out.length && out.at(-1).role === m.role) out.at(-1).content += '\n' + text; else out.push({ role: m.role, content: text });
  }
  while (out.length && out[0].role !== 'user') out.shift();
  return out;
};

app.get('/health', (_, res) => res.json({ ok: true }));

app.post('/chat', async (req, res) => {
  const bearer = (req.get('authorization') || '').replace(/^Bearer /, '');
  if (!safeEq(bearer, APP_TOKEN)) return res.status(401).json({ error: 'unauthorized' });
  if (limited(req.ip)) return res.status(429).json({ error: 'slow down' });
  const messages = clean(req.body?.messages);
  if (!messages.length || messages.at(-1).role !== 'user') return res.status(400).json({ error: 'need a user message' });
  try {
    const r = await fetch(`${ANTHROPIC_BASE_URL}/v1/messages`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-api-key': ANTHROPIC_API_KEY, 'anthropic-version': '2023-06-01' },
      body: JSON.stringify({ model: ANTHROPIC_MODEL, max_tokens: 1500, system: system(req.body?.context ?? {}), tools, messages }),
    });
    const j = await r.json();
    if (!r.ok) { console.error('AI error', r.status, JSON.stringify(j).slice(0, 300)); return res.status(502).json({ error: 'AI provider error' }); }
    let reply = ''; const proposals = [];
    for (const b of j.content ?? []) {
      if (b.type === 'text') reply += b.text;
      else if (b.type === 'tool_use') proposals.push({ tool: b.name, args: b.input });
    }
    if (!reply.trim() && proposals.length) reply = 'Here is what I would like to do — please confirm below.';
    res.json({ reply: reply.trim(), proposals });
  } catch (e) { console.error(e); res.status(502).json({ error: 'backend failure' }); }
});

app.listen(PORT, () => console.log(`SkillForge backend on :${PORT}`));
