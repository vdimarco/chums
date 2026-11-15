#!/usr/bin/env python3
"""Install a debug APK and provision its hub credentials over an authorized ADB connection."""
import argparse
import json
import pathlib
import subprocess
import xml.etree.ElementTree as ET
from urllib.parse import urlsplit

ROOT = pathlib.Path(__file__).resolve().parents[1]
PACKAGES = {name: 'club.chums.wear' + ('' if name == 'app' else '.' + name)
            for name in ('app', 'codex', 'claude', 'grokbot')}


def preferences(address, token):
    url = urlsplit(address)
    local = url.scheme == 'http' and url.hostname == '127.0.0.1'
    if not (url.scheme == 'https' or local) or not url.hostname or url.username or url.password or url.query or url.fragment or url.path not in ('', '/'):
        raise ValueError('Use an HTTPS origin, or http://127.0.0.1:7718 for ADB forwarding')
    if not isinstance(token, str) or not token.strip() or '\n' in token or '\r' in token:
        raise ValueError('The watch token is missing or invalid')
    root = ET.Element('map')
    for name, value in [('url', address.rstrip('/')), ('token', token)]:
        ET.SubElement(root, 'string', name=name).text = value
    return ET.tostring(root, encoding='utf-8', xml_declaration=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app', choices=PACKAGES, default='app', help='Watch app to install')
    parser.add_argument('--serial', help='ADB device serial; required when multiple devices are connected')
    parser.add_argument('--url', default='http://127.0.0.1:7718')
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    if args.self_test:
        parsed = ET.fromstring(preferences('https://example.com', 'a&<b'))
        assert parsed.find("string[@name='token']").text == 'a&<b'
        for url in ['http://example.com', 'https://user@example.com', 'https://example.com/path']:
            try:
                preferences(url, 'test')
            except ValueError:
                continue
            raise AssertionError('Accepted an invalid endpoint')
        print('Wear provisioning checks passed')
        return
    package = PACKAGES[args.app]
    apk = ROOT / f'wear/{args.app}/build/outputs/apk/debug/{args.app}-debug.apk'
    if not apk.is_file():
        parser.error(f'Build the APK first: cd wear && ./gradlew :{args.app}:assembleDebug')
    config = json.loads((pathlib.Path.home() / '.chums/config.json').read_text())
    data = preferences(args.url, config.get('watchToken'))
    adb = ['adb'] + (['-s', args.serial] if args.serial else [])
    subprocess.run(adb + ['get-state'], check=True, capture_output=True)
    subprocess.run(adb + ['install', '-r', str(apk)], check=True)
    subprocess.run(adb + ['shell', 'am', 'force-stop', package], check=True)
    subprocess.run(adb + ['shell', f"run-as {package} sh -c 'mkdir -p shared_prefs && cat > shared_prefs/hub.xml'"], input=data, check=True, stdout=subprocess.DEVNULL)
    if urlsplit(args.url).scheme == 'http':
        port = urlsplit(args.url).port or 80
        subprocess.run(adb + ['reverse', f'tcp:{port}', f'tcp:{port}'], check=True)
    subprocess.run(adb + ['shell', 'am', 'start', '-n', package + '/club.chums.wear.MainActivity'], check=True, stdout=subprocess.DEVNULL)
    print(f'{args.app} installed and opened. Credentials were transferred without printing them.')


if __name__ == '__main__':
    main()
