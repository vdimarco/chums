// Recent chats on this computer, by provider, for the watch to browse. Read-only: nothing here writes to a
// provider's files, and a chat is only ever found by listing the provider's own folder (never by a path the
// watch sends).
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

const ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
const HEAD = 512 * 1024, TAIL = 768 * 1024

// what a watch on someone's wrist should not show, or carry over the internet
export const redact = text => String(text)
  .replace(/-----BEGIN [^-]*PRIVATE KEY-----[\s\S]*?-----END [^-]*PRIVATE KEY-----/g, '[redacted]')
  .replace(/\b(?:sk-|ck_|ek_|ghp_|gho_|github_pat_|xox[abp]-)[A-Za-z0-9_-]{12,}/g, '[redacted]')
  .replace(/\bBearer\s+\S+/gi, 'Bearer [redacted]')
  .replace(/[A-Za-z0-9_+/=-]{48,}/g, '[redacted]')
  .replace(/(?:api[_ -]?key|token|password|secret|authorization)\s*["']?\s*[:=][^\n,}]+/gi, '[credential redacted]')

const clean = (text, max) => {
  const t = redact(text).replace(/\s+/g, ' ').trim()
  return t.length > max ? t.slice(0, max - 1) + '…' : t
}
// a person's own words, not what a harness wrapped around them (<system-reminder>, <environment_context>, …)
const typed = text => typeof text === 'string' && text.trim() && !/^\s*[<\[{]/.test(text) && !/^Caveat:|^# AGENTS/.test(text.trim())

function slice(file, from, length) {
  const fd = fs.openSync(file, 'r')
  try {
    const size = fs.fstatSync(fd).size, start = from < 0 ? Math.max(0, size + from) : 0
    const buffer = Buffer.alloc(Math.min(length, size - start))
    fs.readSync(fd, buffer, 0, buffer.length, start)
    const lines = buffer.toString('utf8').split('\n')
    if (start > 0) lines.shift()                                   // a line cut in half by where the read began
    return lines
  } finally { fs.closeSync(fd) }
}
const parsed = lines => lines.map(l => { try { return JSON.parse(l) } catch { return null } }).filter(Boolean)

// ---- each provider's own layout: where its chats are, and what one line of a chat says ----
function claudeFiles(home) {
  const root = path.join(home, '.claude', 'projects'), out = []
  for (const dir of safeList(root)) for (const f of safeList(path.join(root, dir)))
    if (f.endsWith('.jsonl') && ID.test(f.slice(0, -6))) out.push({ id: f.slice(0, -6), file: path.join(root, dir, f) })
  return out
}
function codexFiles(home) {
  const out = []
  const walk = (dir, depth) => {
    for (const f of safeList(dir)) {
      const full = path.join(dir, f), id = f.match(/([0-9a-f-]{36})\.jsonl$/)?.[1]
      if (id && ID.test(id)) out.push({ id, file: full })
      else if (depth < 3 && /^\d+$/.test(f)) walk(full, depth + 1)
    }
  }
  walk(path.join(home, '.codex', 'sessions'), 0)
  return out
}
const safeList = dir => { try { return fs.readdirSync(dir) } catch { return [] } }
const parts = (content, kinds) => typeof content === 'string' ? content
  : Array.isArray(content) ? content.filter(c => kinds.includes(c?.type) && typeof c.text === 'string').map(c => c.text).filter(typed).join('\n') : ''

const claudeLine = d => {
  if (d.isSidechain || d.isMeta || !['user', 'assistant'].includes(d.type)) return null
  const text = parts(d.message?.content, ['text'])
  return typed(text) ? { role: d.type, text, cwd: d.cwd } : d.cwd ? { cwd: d.cwd } : null
}
const codexLine = d => {
  if (d.type === 'session_meta') return { cwd: d.payload?.cwd }
  const p = d.payload
  if (d.type !== 'response_item' || p?.type !== 'message' || !['user', 'assistant'].includes(p.role)) return null
  const text = parts(p.content, ['input_text', 'output_text', 'text'])
  return typed(text) ? { role: p.role, text } : null
}
const FILES = { claude: [claudeFiles, claudeLine], codex: [codexFiles, codexLine] }

function fileChats(provider, limit, home) {
  const [find, read] = FILES[provider]
  const recent = find(home).map(c => { try { return { ...c, at: fs.statSync(c.file).mtimeMs } } catch { return null } })
    .filter(Boolean).sort((a, b) => b.at - a.at)
  const out = []
  for (const chat of recent) {
    if (out.length >= limit) break
    let title = '', cwd = ''
    try {
      for (const line of parsed(slice(chat.file, 0, HEAD)).map(read)) {
        if (!line) continue
        cwd ||= line.cwd ?? ''
        if (!title && line.role === 'user') title = line.text
        if (title && cwd) break
      }
    } catch { continue }
    if (!title) continue                                           // nothing a person typed: a subagent's or an empty run
    out.push({ id: chat.id, title: clean(title, 90), project: cwd ? path.basename(cwd) : '', at: Math.round(chat.at) })
  }
  return out
}
function fileChat(provider, id, limit, home) {
  const [find, read] = FILES[provider]
  const chat = find(home).find(c => c.id === id)
  if (!chat) return null
  const messages = parsed(slice(chat.file, -TAIL, TAIL)).map(read).filter(l => l?.role)
    .map(l => ({ role: l.role, text: clean(l.text, 700) }))
  return { id, messages: messages.slice(-limit) }
}

// Hermes (which the Grok app talks through) keeps its chats in a database
async function hermes(home, run) {
  const file = path.join(home, '.hermes', 'state.db')
  if (!fs.existsSync(file)) return null
  const { DatabaseSync } = await import('node:sqlite')
  const db = new DatabaseSync(file, { readOnly: true })
  try { return run(db) } finally { db.close() }
}
const hermesChats = (limit, home) => hermes(home, db => db.prepare(
  `SELECT s.id, s.title, s.cwd, COALESCE(s.last_activity_at, s.started_at) AS at,
     (SELECT content FROM messages m WHERE m.session_id = s.id AND m.role = 'user' AND m.active = 1 ORDER BY m.id LIMIT 1) AS first
   FROM sessions s WHERE s.hidden = 0 AND s.archived = 0 AND s.parent_session_id IS NULL AND s.message_count > 0
   ORDER BY at DESC LIMIT ?`).all(limit * 3)
  .map(r => ({ id: r.id, title: clean(r.title || (typed(r.first) ? r.first : ''), 90), project: r.cwd ? path.basename(r.cwd) : '', at: Math.round(r.at * 1000) }))
  .filter(r => r.title).slice(0, limit))
const hermesChat = (id, limit, home) => hermes(home, db => {
  if (!db.prepare('SELECT 1 FROM sessions WHERE id = ?').get(id)) return null
  const rows = db.prepare(`SELECT role, content FROM messages WHERE session_id = ? AND active = 1 AND role IN ('user','assistant')
    AND content IS NOT NULL AND content <> '' ORDER BY id DESC LIMIT ?`).all(id, limit * 3)
  return { id, messages: rows.reverse().filter(r => typed(r.content)).map(r => ({ role: r.role, text: clean(r.content, 700) })).slice(-limit) }
})

export const PROVIDERS = ['claude', 'codex', 'hermes']
/** The newest chats this computer holds for a provider: [{id, title, project, at}], newest first. */
export async function listChats(provider, limit = 12, home = os.homedir()) {
  if (provider === 'hermes') return await hermesChats(limit, home) ?? []
  return FILES[provider] ? fileChats(provider, limit, home) : []
}
/** The last messages of one chat, or null when this computer has no such chat. */
export async function readChat(provider, id, limit = 12, home = os.homedir()) {
  if (typeof id !== 'string' || id.length > 120 || !/^[\w.:-]+$/.test(id)) return null
  if (provider === 'hermes') return hermesChat(id, limit, home)
  return FILES[provider] && ID.test(id) ? fileChat(provider, id, limit, home) : null
}
