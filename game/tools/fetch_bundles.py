import json, os, sys, time, hashlib, csv, threading, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed

ALS = sys.argv[1] if len(sys.argv)>1 else 'runtime/assets_api/als_response.json'
OUT = sys.argv[2] if len(sys.argv)>2 else 'assets/bundles'
MAN = sys.argv[3] if len(sys.argv)>3 else 'assets/bundles_manifest.csv'
WORKERS = int(os.environ.get('WORKERS','10'))
LIMIT = int(os.environ.get('LIMIT','0'))
GROUP_FILTER = [s for s in os.environ.get('GROUPS','').split(',') if s]

data = json.load(open(ALS))['result']['assets']
items = []
for g, lst in data.items():
    if GROUP_FILTER and g not in GROUP_FILTER: continue
    for it in lst:
        it = dict(it); it['group'] = g; items.append(it)
if LIMIT: items = items[:LIMIT]
print('items', len(items), 'total MB', round(sum(i['size'] for i in items)/1e6,1), flush=True)

os.makedirs(OUT, exist_ok=True)
manifest_path = MAN
lock = threading.Lock()
done_state = {'n':0,'bytes':0,'fail':0,'skip':0,'start':time.time()}
rows = []
def fetch(it):
    name = it['name']; group = it['group']
    d = os.path.join(OUT, group); os.makedirs(d, exist_ok=True)
    path = os.path.join(d, name)
    if os.path.exists(path) and os.path.getsize(path) == it['size']:
        md5 = hashlib.md5(open(path,'rb').read()).hexdigest()
        if md5 == it['hash']:
            return row(it, path, 'cached', md5, None)
    url = it['url']; last = None
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={'User-Agent':'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/156 Safari/537.36','Accept':'*/*'})
            with urllib.request.urlopen(req, timeout=60) as r:
                buf = r.read()
            if len(buf) != it['size']:
                raise IOError(f'size {len(buf)} != {it["size"]}')
            md5 = hashlib.md5(buf).hexdigest()
            if md5 != it['hash']:
                raise IOError(f'md5 {md5} != {it["hash"]}')
            tmp = path + '.part'
            with open(tmp,'wb') as f: f.write(buf)
            os.replace(tmp, path)
            return row(it, path, 'ok', md5, None)
        except Exception as e:
            last = e
            url = it.get('backup_url') or it['url']
            time.sleep(1.5*(attempt+1))
    return row(it, None, 'fail', None, str(last)[:200])

def row(it, path, status, md5, err):
    return {'group':it['group'],'name':it['name'],'uid':it['uid'],'size':it['size'],'hash':it['hash'],
            'file':path or '', 'md5':md5 or '', 'status':status, 'error':err or ''}

fields = ['group','name','uid','size','hash','file','md5','status','error']
with open(manifest_path,'w',newline='') as f:
    w = csv.DictWriter(f, fieldnames=fields); w.writeheader()
    with ThreadPoolExecutor(max_workers=WORKERS) as ex:
        futs = {ex.submit(fetch, it): it for it in items}
        for fut in as_completed(futs):
            r = fut.result()
            with lock:
                w.writerow(r); f.flush()
                done_state['n'] += 1
                if r['status']=='ok':
                    done_state['bytes'] += r['size']
                elif r['status']=='cached': done_state['skip'] += 1; done_state['bytes'] += r['size']
                else: done_state['fail'] += 1
                n=done_state['n']
            if n % 100 == 0 or n == len(items):
                el = time.time()-done_state['start']
                print(f"progress {n}/{len(items)} ok+{done_state['skip']} fail {done_state['fail']} MB {done_state['bytes']/1e6:.0f} elapsed {el/60:.1f}m", flush=True)
print(json.dumps(done_state, ensure_ascii=False), flush=True)
