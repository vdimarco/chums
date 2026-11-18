// The watch's recent-chats reader, against a made-up home folder: no real chat is read.
import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { listChats, readChat, redact } from '../hub/chats.mjs'

const home = fs.mkdtempSync(path.join(os.tmpdir(), 'chums-chats-'))
const write = (file, lines) => { fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, lines.map(l => JSON.stringify(l)).join('\n') + '\n') }
const a = '11111111-1111-4111-8111-111111111111', b = '22222222-2222-4222-8222-222222222222', c = '33333333-3333-4333-8333-333333333333'
write(path.join(home, '.claude/projects/-work-app', a + '.jsonl'), [
  { type: 'user', cwd: '/work/app', message: { content: [{ type: 'text', text: '<system-reminder>ignore</system-reminder>' }, { type: 'text', text: 'Fix the build' }] } },
  { type: 'assistant', message: { content: [{ type: 'tool_use', name: 'Bash' }, { type: 'text', text: 'Done. token = abc123secretvalue' }] } },
  { type: 'assistant', isSidechain: true, message: { content: [{ type: 'text', text: 'subagent chatter' }] } },
])
write(path.join(home, '.claude/projects/-work-app', b + '.jsonl'), [{ type: 'attachment', cwd: '/work/app' }])   // nothing typed
write(path.join(home, `.codex/sessions/2026/11/15/rollout-2026-11-15T10-00-00-${c}.jsonl`), [
  { type: 'session_meta', payload: { cwd: '/work/tool' } },
  { type: 'response_item', payload: { type: 'message', role: 'developer', content: [{ type: 'input_text', text: 'rules' }] } },
  { type: 'response_item', payload: { type: 'message', role: 'user', content: [{ type: 'input_text', text: 'Add a   test' }] } },
  { type: 'response_item', payload: { type: 'message', role: 'assistant', content: [{ type: 'output_text', text: 'Added.' }] } },
])
try {
  const claude = await listChats('claude', 12, home)
  assert.deepEqual(claude.map(x => [x.id, x.title, x.project]), [[a, 'Fix the build', 'app']])
  const chat = await readChat('claude', a, 12, home)
  assert.deepEqual(chat.messages.map(m => m.role), ['user', 'assistant'])
  assert.ok(!JSON.stringify(chat).includes('abc123secretvalue') && !JSON.stringify(chat).includes('subagent'))
  const codex = await listChats('codex', 12, home)
  assert.deepEqual(codex.map(x => [x.id, x.title, x.project]), [[c, 'Add a test', 'tool']])
  assert.deepEqual((await readChat('codex', c, 1, home)).messages, [{ role: 'assistant', text: 'Added.' }])
  // a chat is found only by listing the provider's folder
  for (const id of ['../../etc/passwd', b.replace('2', 'x'), '', 'a'.repeat(200), '44444444-4444-4444-8444-444444444444'])
    assert.equal(await readChat('claude', id, 12, home), null)
  assert.equal(await readChat('codex', a, 12, home), null)        // another provider's chat
  assert.deepEqual(await listChats('hermes', 12, home), [])        // no database on this computer
  assert.deepEqual(await listChats('unknown', 12, home), [])
  assert.ok(!redact('Bearer abcdefgh sk-abcdefghijklmnop').includes('abcdefgh'))
  console.log('chats: per-provider listing, tail, redaction and id checks PASS')
} finally { fs.rmSync(home, { recursive: true, force: true }) }
