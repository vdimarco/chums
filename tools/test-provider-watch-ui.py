#!/usr/bin/env python3
import subprocess,time,xml.etree.ElementTree as ET,re
from pathlib import Path
import argparse, os
parser = argparse.ArgumentParser(description="Offline UI smoke test; replaces preview app preferences on an emulator.")
parser.add_argument('--serial', required=True)
args = parser.parse_args()
if not args.serial.startswith('emulator-'):
    parser.error('Use a disposable Wear OS emulator, not a physical watch')
os.chdir(Path(__file__).resolve().parents[1])
adb=['adb','-s',args.serial]
def run(*args):return subprocess.check_output(adb+list(args),text=True)
def nodes():
 run('shell','uiautomator','dump','/sdcard/provider.xml')
 return list(ET.fromstring(run('shell','cat','/sdcard/provider.xml')).iter('node'))
def tap(node):
 x,y,r,b=map(int,re.findall(r'\d+',node.get('bounds')))
 run('shell','input','tap',str((x+r)//2),str((y+b)//2));time.sleep(.5)
density = int(re.findall(r'density: (\d+)', run('shell', 'wm', 'density'))[-1])
minimum = round(48 * density / 160)
out=Path('wear/screenshots/providers'); out.mkdir(parents=True, exist_ok=True)
for name in ['codex','claude','grokbot']:
 package='club.chums.wear.'+name
 run('shell','input','keyevent','KEYCODE_WAKEUP')
 run('install','-r',f'wear/{name}/build/outputs/apk/debug/{name}-debug.apk')
 run('shell','am','force-stop',package)
 prefs=b'<?xml version="1.0"?><map><string name="url">http://127.0.0.1:7718</string><string name="token">offline-preview</string></map>'
 subprocess.run(adb+['shell',f"run-as {package} sh -c 'mkdir -p shared_prefs && cat > shared_prefs/hub.xml'"],input=prefs,check=True)
 run('shell','am','start','-W','-n',package+'/club.chums.wear.MainActivity');time.sleep(2)
 home=nodes()
 rectangles=[]
 for description in ['Conversation history','Connection settings','Talk to '+{'codex':'Codex','claude':'Claude','grokbot':'Grok'}[name]]:
  node=next(n for n in home if n.get('content-desc')==description)
  x,y,r,b=map(int,re.findall(r'\d+',node.get('bounds')))
  assert r-x>=minimum and b-y>=minimum,(name,description,node.get('bounds'))
  rectangles.append((x,y,r,b))
 for i,(x,y,r,b) in enumerate(rectangles):
  for a,c,d,e in rectangles[i+1:]:
   assert r<=a or d<=x or b<=c or e<=y,(name,'overlapping controls')
 with (out/f'{name}-higgsfield.png').open('wb') as f:subprocess.run(adb+['exec-out','screencap','-p'],stdout=f,check=True)
 tap(next(n for n in home if n.get('content-desc')=='Conversation history'))
 history = nodes()
 assert any(n.get('text')=='Type a message' for n in history),name+' history failed'
 tap(next(n for n in history if n.get('text')=='Type a message'))
 assert any(n.get('resource-id')=='android:id/button1' for n in nodes()),name+' composer failed'
 tap(next(n for n in nodes() if n.get('resource-id')=='android:id/button1'))
 assert any(n.get('resource-id')=='android:id/button1' for n in nodes()),name+' empty message was accepted'
 run('shell','input','swipe','204','320','204','140','300');time.sleep(.3)
 tap(next(n for n in nodes() if n.get('resource-id')=='android:id/button2'))
 assert not any(n.get('resource-id')=='android:id/button1' for n in nodes()),name+' cancel failed'
 run('shell','input','keyevent','KEYCODE_BACK');time.sleep(.4)
 tap(next(n for n in home if n.get('content-desc')=='Connection settings'))
 assert any(n.get('text','').startswith('Connect ') for n in nodes()),name+' settings failed'
 run('shell','input','keyevent','KEYCODE_BACK')
 print(name+' home, touch targets, history and settings passed',flush=True)
