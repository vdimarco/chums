#!/usr/bin/env python3
"""Exercise workspace/model/attachment/alert flows against a local fixture, emulator only."""
import argparse, http.server, json, os, re, subprocess, threading, time
from pathlib import Path
import xml.etree.ElementTree as ET
args=argparse.ArgumentParser()
args.add_argument('--serial', required=True)
serial=args.parse_args().serial
if not serial.startswith('emulator-'): args.error('Use a disposable emulator; this replaces test preferences')
os.chdir(Path(__file__).resolve().parents[1])
adb=['adb','-s',serial]
def run(*parts): return subprocess.check_output(adb+list(parts),text=True)
def nodes():
    run('shell','uiautomator','dump','/sdcard/workspace-test.xml')
    return list(ET.fromstring(run('shell','cat','/sdcard/workspace-test.xml')).iter('node'))
def find(text):
    for _ in range(7):
        for n in nodes():
            if n.get('text','').casefold()==text.casefold(): return n
        run('shell','input','swipe','204','320','204','140','250')
    raise AssertionError('Missing control: '+text)
def tap(n):
    x,y,r,b=map(int,re.findall(r'\d+',n.get('bounds')))
    run('shell','input','tap',str((x+r)//2),str((y+b)//2)); time.sleep(.4)
state={'rev':1,'agents':[{'id':i,'runtime':r,'status':'idle','model':'test-a'} for i,r in [('noodle','codex'),('squint','claude'),('stretch','hermes')]],'tools':[],'spoken':[],'approvals':[]}
models={r:{'models':[{'id':'test-a','name':'Test A'},{'id':'test-b','name':'Test B'}]} for r in ['codex','claude','hermes']}
posts=[]
class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self,*_): pass
    def do_GET(self):
        if self.headers.get('Authorization')!='Bearer fixture': self.send_error(401); return
        if self.path.startswith('/v1/artifact/'):
            body=Path('wear/app/src/main/assets/providers/noodle.png').read_bytes(); kind='image/png'
        else:
            body=json.dumps(models if self.path=='/v1/models' else state).encode(); kind='application/json'
        self.send_response(200);self.send_header('Content-Type',kind);self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
    def do_POST(self):
        body=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        posts.append((self.path,body));data=b'{}'
        self.send_response(200);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler)
threading.Thread(target=server.serve_forever,daemon=True).start()
port=server.server_port
run('reverse',f'tcp:{port}',f'tcp:{port}')
try:
    for app,agent in [('codex','noodle'),('claude','squint'),('grokbot','stretch')]:
        package='club.chums.wear.'+app
        run('shell','am','force-stop',package)
        prefs=f'<map><string name="url">http://127.0.0.1:{port}</string><string name="token">fixture</string><string name="draft">retained draft</string></map>'
        subprocess.run(adb+['shell',f"run-as {package} sh -c 'mkdir -p shared_prefs; cat > shared_prefs/hub.xml'"],input=prefs.encode(),check=True)
        state['spoken']=[{'id':'fixture-art','agent':agent,'kind':'artifact','text':'Preview test','artifact':{'id':'abcdef012345','title':'Fixture image'}}]
        state['rev']+=1
        run('shell','am','start','-W','-n',package+'/club.chums.wear.MainActivity');time.sleep(1)
        tap(next(n for n in nodes() if n.get('content-desc')=='Conversation history'))
        tap(find('Agent workspace')); tap(find('Models')); tap(find('Test B')); tap(find('Use model'))
        find('Model updated')
        assert posts[-1]==(f'/v1/agents/{agent}/model',{'model':'test-b'}),posts[-1]
        tap(find('Refresh workspace'));tap(find('Background alerts'))
        run('shell','pm','grant',package,'android.permission.POST_NOTIFICATIONS')
        tap(find('Enable alerts'));find('Background checks enabled')
        state['spoken'].append({'id':'new-'+app,'agent':agent,'kind':'done','text':'fixture complete'})
        run('shell','cmd','jobscheduler','run','-f',package,'41');time.sleep(2)
        notification=run('shell','dumpsys','notification','--noredact')
        assert 'Agent update available' in notification and package in notification
        tap(find('Disable alerts')); find('Background checks disabled')
        run('shell','input','keyevent','KEYCODE_BACK');time.sleep(.4)
        tap(find('Open Fixture image'));find('Preview — scroll to read')
        run('shell','input','keyevent','KEYCODE_BACK');time.sleep(.3)
        print(app+': provider model routing, scheduled alert, disable, authenticated artifact preview PASS',flush=True)
finally:
    run('reverse','--remove',f'tcp:{port}')
    server.shutdown()
