#!/usr/bin/env python3
"""Mirror the live config service tables into replica/configs/ for offline replay."""
import json, os, sys, time, hashlib, uuid, datetime, gzip, io
import urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
HASHES = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('/tmp/gh_response.bin')
OUT = ROOT / 'replica' / 'configs'
MANIFEST = OUT / 'manifest.json'
WORKERS = int(os.environ.get('WORKERS', '6'))
LIMIT = int(os.environ.get('LIMIT', '0'))
HEADERS_FILE = ROOT / 'replica' / 'headers' / 'configs.chickgoddess.com.json'

res = json.loads(HASHES.read_text())
config_data = res['result']['config_data']
ctx = None
for name, meta in config_data.items():
    url = meta.get('url', '')
    parts = [p for p in url.split('/') if p]
    if 'configs_msgpack' in parts:
        ctx = parts[parts.index('configs_msgpack') + 1]
        break
print('ctx', ctx, 'tables', len(config_data))
base_headers = json.loads(HEADERS_FILE.read_text())
def make_headers():
    h = {k: v for k, v in base_headers.items()
         if k.lower() not in ('host', 'content-length', 'connection', 'accept-encoding',
                              'if-none-match', 'if-modified-since')}
    now = datetime.datetime.now(datetime.timezone.utc)
    h['PNK-request-client-start-time'] = now.strftime('%Y-%m-%dT%H:%M:%S.%f0')
    h['PNK-request-client-system-time'] = datetime.datetime.now().astimezone().isoformat(timespec='microseconds')
    h['PNK-request-client-system-time-utc'] = now.strftime('%Y-%m-%dT%H:%M:%S.%f0Z')
    h['PNK-Request-Id'] = str(uuid.uuid4())
    h['PNK-Retry'] = '1'
    h['Content-Type'] = 'application/x-msgpack'
    h['PNK-Config-Ctx'] = ctx
    h['Pnk-Config-Ctx'] = ctx
    return h

items = list(config_data.items())
if LIMIT: items = items[:LIMIT]
OUT.mkdir(parents=True, exist_ok=True)
if ctx: (OUT / ctx).mkdir(parents=True, exist_ok=True)
manifest = {}
if MANIFEST.exists():
    try: manifest = json.loads(MANIFEST.read_text())
    except Exception: manifest = {}
lock_total = {'ok': 0, 'fail': 0, 'bytes': 0}

def fetch(item):
    name, meta = item
    url_path = meta.get('url')
    if not url_path:
        return name, None
    url = 'https://configs.chickgoddess.com' + url_path
    last = None
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers=make_headers())
            with urllib.request.urlopen(req, timeout=30) as r:
                data = r.read()
                enc = r.headers.get('Content-Encoding')
                ctype = r.headers.get('Content-Type', 'application/x-msgpack')
            if len(data) < 2:
                raise IOError('empty body')
            if data[:2] == b'\x1f\x8b':
                raw = gzip.decompress(data)  # store decompressed payload
                enc = None
            else:
                raw = data
            d = OUT / ctx
            d.mkdir(parents=True, exist_ok=True)
            (d / (name + '.msgpack')).write_bytes(raw)
            return name, {'hash': meta.get('hash'), 'url': url_path, 'bytes': len(raw),
                          'ctype': ctype, 'encoding': enc or '', 'status': 'ok'}
        except Exception as e:
            last = str(e)[:180]
            time.sleep(1.0 * (attempt + 1))
    return name, {'hash': meta.get('hash'), 'url': url_path, 'status': 'fail', 'error': last}

start = time.time()
with ThreadPoolExecutor(max_workers=WORKERS) as ex:
    futs = [ex.submit(fetch, it) for it in items]
    for n, f in enumerate(as_completed(futs), 1):
        name, info = f.result()
        if info:
            manifest[name] = info
            if info.get('status') == 'ok':
                lock_total['ok'] += 1; lock_total['bytes'] += info.get('bytes', 0)
            else:
                lock_total['fail'] += 1
        if n % 25 == 0 or n == len(items):
            MANIFEST.write_text(json.dumps({'ctx': ctx, 'tables': manifest}, indent=1))
            print(f"progress {n}/{len(items)} ok {lock_total['ok']} fail {lock_total['fail']} MB {lock_total['bytes']/1e6:.1f} elapsed {time.time()-start:.0f}s", flush=True)
MANIFEST.write_text(json.dumps({'ctx': ctx, 'tables': manifest}, indent=1))
print(json.dumps({'ctx': ctx, 'ok': lock_total['ok'], 'fail': lock_total['fail'], 'MB': round(lock_total['bytes']/1e6, 1)}, ensure_ascii=False))
