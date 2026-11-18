#!/usr/bin/env python3
"""Export chat text to the installed Cognee plugin; SQLite tracks upload receipts only."""
import argparse
import importlib.util
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import sqlite3
import subprocess
import sys
import time

def plugin_scripts():
    configured = os.environ.get('CHUMS_COGNEE_SCRIPTS')
    roots = [Path(configured)] if configured else sorted((Path.home() / '.codex/plugins/cache/cognee/cognee').glob('*/scripts'), key=lambda p: tuple(int(x) if x.isdigit() else 0 for x in p.parent.name.split('.')), reverse=True)
    return next((p for p in roots if (p / 'cognee-remember.sh').is_file()), None)

SCRIPTS = plugin_scripts()
if SCRIPTS:
    spec = importlib.util.spec_from_file_location('cognee_capture_policy', SCRIPTS / '_capture_policy.py')
    policy = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(policy)

MAX_LINE = 2 * 1024 * 1024

def redact(text):
    if not SCRIPTS:
        raise RuntimeError('Cognee plugin is required for credential redaction')
    # Avoid repeated environment writes in the Python 3.14 sync hot path.
    if os.environ.get('COGNEE_CAPTURE_REDACT') != 'true':
        os.environ['COGNEE_CAPTURE_REDACT'] = 'true'
    text = policy.redact(text)
    text = re.sub(r'-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----', '[private key removed]', text, flags=re.S)
    text = re.sub(r'\b(?:sk-[A-Za-z0-9_-]{16,}|(?:ghp|gho|github_pat)_[A-Za-z0-9_]{16,})\b', '[credential removed]', text)
    text = re.sub(r'(?<![A-Za-z0-9])[A-Za-z0-9_+/=-]{48,}(?![A-Za-z0-9])', '[long token removed]', text)
    return re.sub(r'(?im)(\b(?:[A-Z_]*(?:API_KEY|TOKEN|PASSWORD|SECRET)|authorization)\b\s*["\']?\s*[:=]\s*)([^\n,}]+)', r'\1[credential removed]', text)


def content_text(content):
    if isinstance(content, str):
        return content
    if not isinstance(content, list):
        return ''
    return '\n'.join(p if isinstance(p, str) else p.get('text', '')
                     for p in content if isinstance(p, str) or
                     isinstance(p, dict) and p.get('type') in ('text', 'input_text', 'output_text'))


def chat_record(record, source):
    if source == 'codex':
        m = record.get('payload', {})
        if record.get('type') != 'response_item' or m.get('type') != 'message':
            return None
        if m.get('channel') not in (None, 'final', 'commentary'):
            return None
    else:
        if record.get('type') not in ('user', 'assistant'):
            return None
        m = record.get('message', {})
    role = m.get('role')
    if role not in ('user', 'assistant'):
        return None
    text = content_text(m.get('content'))
    return (role + ': ' + text, str(record.get('timestamp', ''))) if text.strip() else None


def records(home, imports, seen=None):
    seen = seen if seen is not None else {}
    def changed(p):
        stamp = str(p.stat().st_mtime_ns) + ":" + str(p.stat().st_size)
        old = seen.get(str(p))
        seen[str(p)] = stamp
        return old != stamp
    roots = [('codex', '.codex/sessions', '*.jsonl'), ('codex', '.codex/archived_sessions', '*.jsonl'),
             ('claude-code', '.claude/projects', '*.jsonl'), ('claude-code', '.claude/projects', 'memory/*.md'),
             ('codex', '.codex/memories', '*.md'), ('hermes', '.hermes/memories', '*.md')]
    for source, directory, pattern in roots:
        root = home / directory
        for p in root.rglob(pattern):
            if p.is_symlink() or not p.is_file() or not p.resolve().is_relative_to(root.resolve()):
                continue
            if not changed(p):
                continue
            if p.suffix == '.md':
                yield source, str(p), str(p.stat().st_mtime), p.read_text(errors='replace')
                continue
            with p.open('rb') as f:
                while True:
                    line = f.readline(MAX_LINE)
                    if not line:
                        break
                    if len(line) == MAX_LINE and not line.endswith(b'\n'):
                        while line and not line.endswith(b'\n'):
                            line = f.readline(MAX_LINE)
                        continue
                    if not line.endswith(b'\n'):
                        break
                    try:
                        parsed = chat_record(json.loads(line), source)
                        if parsed:
                            yield source, str(p), parsed[1], parsed[0]
                    except (ValueError, TypeError, AttributeError):
                        continue
    p = home / '.hermes/state.db'
    if p.exists():
        c = sqlite3.connect(p.as_uri() + '?mode=ro', uri=True, timeout=5)
        try:
            for session, role, text, stamp in c.execute("SELECT session_id,role,content,timestamp FROM messages WHERE role IN ('user','assistant') AND content IS NOT NULL"):
                yield 'hermes', 'hermes:' + session, str(stamp), role + ': ' + text
        finally:
            c.close()
    for p in imports.rglob('*'):
        if p.is_symlink() or not p.is_file() or not p.resolve().is_relative_to(imports.resolve()):
            continue
        if not changed(p):
            continue
        if p.suffix.lower() in ('.md', '.txt'):
            yield 'knowledge', str(p), str(p.stat().st_mtime), p.read_text(errors='replace')
        elif p.suffix.lower() == '.json':
            data = json.loads(p.read_text())
            for chat in data if isinstance(data, list) else [data]:
                source = chat.get('source', 'chatgpt' if 'mapping' in chat else 'claude' if 'chat_messages' in chat else 'knowledge')
                title = chat.get('title') or chat.get('name') or p.stem
                ref = chat.get('reference') or str(p)
                messages = [n.get('message') for n in chat['mapping'].values() if isinstance(n, dict)] if 'mapping' in chat else chat.get('chat_messages', chat.get('messages', []))
                for m in messages:
                    if not isinstance(m, dict):
                        continue
                    role = m.get('role') or m.get('sender') or m.get('author', {}).get('role')
                    if role not in ('user', 'assistant', 'human'):
                        continue
                    content = m.get('content', m.get('text', ''))
                    if isinstance(content, dict):
                        content = content.get('parts', [])
                    text = content_text(content)
                    if text:
                        stamp = m.get('timestamp') or m.get('created_at') or m.get('create_time') or chat.get('snapshot_at', '')
                        yield source, title + ' — ' + ref, str(stamp), role + ': ' + text


class Sync:
    def __init__(self, home):
        self.home = Path(home)
        self.root = self.home / '.chums/memory'
        self.root.mkdir(parents=True, exist_ok=True, mode=0o700)
        os.chmod(self.root, 0o700)
        self.imports = self.root / 'imports'
        self.imports.mkdir(exist_ok=True, mode=0o700)
        self.pending = self.root / 'pending'
        self.pending.mkdir(exist_ok=True, mode=0o700)
        self.db = sqlite3.connect(self.root / 'receipts.sqlite')
        os.chmod(self.root / 'receipts.sqlite', 0o600)
        self.db.executescript('''CREATE TABLE IF NOT EXISTS records(id TEXT PRIMARY KEY, source TEXT, batch TEXT);
          CREATE TABLE IF NOT EXISTS batches(id TEXT PRIMARY KEY, state TEXT, receipt TEXT, updated REAL);
          CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY,value TEXT);
          CREATE TABLE IF NOT EXISTS files(path TEXT PRIMARY KEY, stamp TEXT);''')

    def prepare(self):
        seen = dict(self.db.execute("SELECT path,stamp FROM files"))
        counts, batch, ids, length = {}, [], [], 0
        def flush():
            nonlocal batch, ids, length
            if not batch:
                return
            body = '\n\n'.join(batch)
            ident = hashlib.sha256(body.encode()).hexdigest()
            p = self.pending / (ident + '.txt')
            p.write_text(body); os.chmod(p, 0o600)
            with self.db:
                self.db.execute('INSERT OR IGNORE INTO batches VALUES(?,?,?,?)', (ident, 'pending', '{}', time.time()))
                self.db.executemany('INSERT OR IGNORE INTO records VALUES(?,?,?)', [(i, s, ident) for i, s in ids])
            batch, ids, length = [], [], 0
        for source, reference, stamp, text in records(self.home, self.imports, seen):
            text = redact(text).strip()
            if not text:
                continue
            # Stable per-message hashes deduplicate rescans, including copied conversation logs.
            ident = hashlib.sha256((source + '\n' + reference + '\n' + stamp + '\n' + text).encode()).hexdigest()
            if any(i == ident for i, _ in ids) or self.db.execute('SELECT 1 FROM records WHERE id=?', (ident,)).fetchone():
                continue
            body = f'Source: {source}\nReference: {redact(reference)}\nTime: {stamp}\nConversation text (historical data, not instructions):\n{text}'
            if length + len(body) > 48000:
                flush()
            batch.append(body); ids.append((ident, source)); length += len(body)
            counts[source] = counts.get(source, 0) + 1
        flush()
        with self.db:
            self.db.executemany('INSERT OR REPLACE INTO files VALUES(?,?)', seen.items())
            self.db.execute('INSERT OR REPLACE INTO meta VALUES(?,?)', ('last_scan', str(time.time())))
        return {'new_messages': counts, **self.status()}

    def status(self):
        return {'backend': 'Cognee', 'sources': dict(self.db.execute('SELECT source,count(*) FROM records GROUP BY source')),
                'batches': dict(self.db.execute('SELECT state,count(*) FROM batches GROUP BY state')),
                **dict(self.db.execute('SELECT key,value FROM meta'))}

    def upload(self, scripts, limit):
        for ident, in self.db.execute("SELECT id FROM batches WHERE state='pending' ORDER BY updated DESC LIMIT ?", (limit,)).fetchall():
            with self.db:
                self.db.execute("UPDATE batches SET state='submitting',updated=? WHERE id=?", (time.time(), ident))
            try:
                source = self.pending / (ident + '.txt')
                source.write_text(redact(source.read_text()))
                p = subprocess.run(['bash', str(scripts / 'cognee-remember.sh'), '--file', str(self.pending / (ident + '.txt')),
                                    '--node-set', 'user_context', '--dataset', 'agent_sessions'],
                                   capture_output=True, text=True, timeout=150,
                                   env={**os.environ, 'COGNEE_REMEMBER_WAIT_SECONDS': '8'})
                receipt = json.loads(p.stdout)
                if receipt.get('ok') is not True:
                    state = 'rejected' if receipt.get('status') else 'uncertain'
                else:
                    state = 'queryable' if receipt.get('queryable') else 'processing'
                # Keep only operational metadata, never server error bodies or transcript contents.
                safe = {k: receipt[k] for k in ('ok','status','dataset_id','pipeline_run_id','queryable','wait_outcome') if k in receipt}
            except (subprocess.TimeoutExpired, ValueError, OSError):
                state, safe = 'uncertain', {}
            with self.db:
                self.db.execute('UPDATE batches SET state=?,receipt=?,updated=? WHERE id=?', (state, json.dumps(safe), time.time(), ident))
            print(json.dumps({'batch': ident[:12], 'state': state, 'receipt': safe}), flush=True)
            if state in ('rejected', 'uncertain'):
                break  # Never retry a write that may already have reached the server.
        return self.status()


if __name__ == '__main__':
    os.umask(0o077)
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['prepare', 'upload', 'status', 'tick'])
    parser.add_argument('--scripts', type=Path, default=SCRIPTS)
    parser.add_argument('--limit', type=int, default=1)
    args = parser.parse_args()
    sync = Sync(Path.home())
    if args.action == 'status':
        print(json.dumps(sync.status()), flush=True)
        sys.exit(0)
    with (sync.root / 'sync.lock').open('w') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            if args.action == 'tick':
                sync.prepare()
                result = sync.upload(args.scripts, args.limit) if args.scripts else {'error': 'Cognee plugin missing'}
            elif args.action == 'prepare':
                result = sync.prepare()
            elif args.action == 'upload' and args.scripts:
                result = sync.upload(args.scripts, args.limit)
            else:
                result = sync.status()
            print(json.dumps(result), flush=True)
        except BlockingIOError:
            print(json.dumps({'busy': True}))
