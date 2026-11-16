#!/usr/bin/env python3
"""Verify the scrollable provider home at 150% text size on a disposable emulator."""
import argparse, subprocess, re, time, xml.etree.ElementTree as ET
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('--serial',required=True);args=parser.parse_args()
if not args.serial.startswith('emulator-'):parser.error('Use a disposable emulator')
adb=['adb','-s',args.serial]
def run(*parts):return subprocess.check_output(adb+list(parts),text=True)
root=Path(__file__).resolve().parents[1]
old=run('shell','settings','get','system','font_scale').strip()
try:
 run('shell','settings','put','system','font_scale','1.5')
 density=int(re.findall(r'density: (\d+)',run('shell','wm','density'))[-1]);minimum=round(48*density/160)
 for app,name in [('codex','Codex'),('claude','Claude'),('grokbot','Grok')]:
  package='club.chums.wear.'+app
  run('install','-r',str(root/f'wear/{app}/build/outputs/apk/debug/{app}-debug.apk'))
  run('shell','am','force-stop',package);run('shell','am','start','-W','-n',package+'/club.chums.wear.MainActivity');time.sleep(.7)
  found=set()
  for _ in range(6):
   run('shell','uiautomator','dump','/sdcard/large-text.xml')
   for node in ET.fromstring(run('shell','cat','/sdcard/large-text.xml')).iter('node'):
    if node.get('text') in ['Talk to '+name,'Conversation history','Connection settings']:
     x,y,r,b=map(int,re.findall(r'\d+',node.get('bounds')))
     if r-x>=minimum and b-y>=minimum: found.add(node.get('text'))
   run('shell','input','swipe','204','325','204','135','250')
  assert len(found)==3,(app,found)
  print(app+': all three home controls accessible at 150% text size PASS',flush=True)
finally:
 if old=='null':run('shell','settings','delete','system','font_scale')
 else:run('shell','settings','put','system','font_scale',old)
