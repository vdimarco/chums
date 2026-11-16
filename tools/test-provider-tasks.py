#!/usr/bin/env python3
"""Exercise live task cards on a disposable Wear OS emulator."""
import argparse, http.server, json, os, re, subprocess, threading, time
import xml.etree.ElementTree as ET
from pathlib import Path
parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
serial = parser.parse_args().serial
if not serial.startswith('emulator-'): parser.error('Requires a disposable emulator')
os.chdir(Path(__file__).resolve().parents[1])
adb = ['adb', '-s', serial]
def run(*args): return subprocess.check_output(adb + list(args), text=True)
def nodes():
    run('shell', 'uiautomator', 'dump', '/sdcard/tasks-test.xml')
    return list(ET.fromstring(run('shell', 'cat', '/sdcard/tasks-test.xml')).iter('node'))
def card(): return next(n for n in nodes() if n.get('content-desc', '').startswith('Open current task.'))
def bounds(n): return list(map(int, re.findall(r'\d+', n.get('bounds'))))
state = {'rev': 1, 'agents': [], 'spoken': [], 'approvals': []}
class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *_): pass
    def do_GET(self):
        if self.headers.get('Authorization') != 'Bearer fixture': self.send_error(401); return
        data = json.dumps(state).encode()
        self.send_response(200); self.send_header('Content-Length', str(len(data))); self.end_headers(); self.wfile.write(data)
server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
threading.Thread(target=server.serve_forever, daemon=True).start()
port = server.server_port
run('reverse', f'tcp:{port}', f'tcp:{port}')
try:
    run('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    for app, agent in [('codex', 'noodle'), ('claude', 'squint'), ('grokbot', 'stretch')]:
        package = 'club.chums.wear.' + app
        run('install', '-r', f'wear/{app}/build/outputs/apk/debug/{app}-debug.apk')
        run('shell', 'am', 'force-stop', package)
        prefs = f'<map><string name="url">http://127.0.0.1:{port}</string><string name="token">fixture</string></map>'
        subprocess.run(adb + ['shell', f"run-as {package} sh -c 'mkdir -p shared_prefs; cat > shared_prefs/hub.xml'"], input=prefs.encode(), check=True)
        state.update(agents=[{'id': agent, 'status': 'working', 'line': 'Running tests'}], spoken=[{'agent': agent, 'kind': 'echo', 'text': 'Fix the build'}], approvals=[])
        state['rev'] += 1
        run('shell', 'am', 'start', '-W', '-n', package + '/club.chums.wear.MainActivity'); time.sleep(1)
        n = card(); assert 'Working' in n.get('text') and 'Fix the build' in n.get('text')
        x,y,r,b = bounds(n); assert r-x >= 96 and b-y >= 96
        for other in nodes():
            if other.get('clickable') != 'true' or other.get('content-desc') == n.get('content-desc'): continue
            l,t,rr,bb = bounds(other)
            assert min(r,rr) <= max(x,l) or min(b,bb) <= max(y,t), 'Overlapping task action'
        run('shell', 'input', 'tap', str((x+r)//2), str((y+b)//2)); time.sleep(.5)
        assert any(n.get('text') == 'Running tests' for n in nodes())
        run('shell', 'input', 'keyevent', 'KEYCODE_BACK'); time.sleep(.3)
        state['approvals'] = [{'session': agent, 'description': 'Run command', 'requestId': 'fixture'}]; state['rev'] += 1; time.sleep(.5)
        assert 'Needs approval' in card().get('text')
        state['approvals'] = []; state['agents'][0]['status'] = 'idle'; state['rev'] += 1; time.sleep(.5)
        assert 'No task running' in card().get('text')
        print(app + ': task preview, live updates, details and separate 48dp tap targets PASS', flush=True)
finally:
    run('reverse', '--remove', f'tcp:{port}')
    server.shutdown()
