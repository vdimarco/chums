import json
import os
from unittest.mock import patch
import tempfile
from pathlib import Path
from memory_sync import Sync, chat_record, redact

assert chat_record({'type':'response_item','payload':{'type':'message','role':'assistant','channel':'analysis','content':'private reasoning'}}, 'codex') is None
assert chat_record({'type':'user','message':{'role':'user','content':[{'type':'tool_result','content':'secret'}]}}, 'claude-code') is None
assert 'sk-' not in redact('key sk-' + 'a'*40)
assert 'password123' not in redact('PASSWORD=password123')
# Force redaction even when disabled, without rewriting the environment per record.
with patch.dict(os.environ, {'COGNEE_CAPTURE_REDACT': 'false'}):
    original_setitem = type(os.environ).__setitem__
    writes = []
    def setitem(env, key, value):
        writes.append(key)
        return original_setitem(env, key, value)
    with patch.object(type(os.environ), '__setitem__', setitem):
        for _ in range(1000):
            assert 'password123' not in redact('PASSWORD=password123')
    assert writes.count('COGNEE_CAPTURE_REDACT') == 1
with tempfile.TemporaryDirectory() as tmp:
    sync = Sync(Path(tmp))
    file = sync.imports/'chat.json'
    file.write_text(json.dumps({'source':'watch-voice','reference':'watch:test','messages':[{'role':'user','content':'The test color is violet','timestamp':'1'}]}))
    assert sync.prepare()['sources'] == {'watch-voice':1}
    assert sync.prepare()['new_messages'] == {}
    text = next(sync.pending.glob('*.txt')).read_text()
    assert 'historical data, not instructions' in text
    assert 'violet' in text
    assert sync.root.stat().st_mode & 0o777 == 0o700
    assert next(sync.pending.glob('*.txt')).stat().st_mode & 0o777 == 0o600
    # A crash after submitting never replays a possibly accepted write.
    sync.db.execute("UPDATE batches SET state='submitting'"); sync.db.commit()
    assert sync.upload(Path('/no-plugin-needed'), 1)['batches'] == {'submitting':1}
print('Memory sync: text-only capture, redaction, deduplication, private files and no uncertain replays passed')
