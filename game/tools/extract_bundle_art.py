import UnityPy, os, sys, csv, json, hashlib, threading, collections, re, traceback
from concurrent.futures import ThreadPoolExecutor, as_completed

MAN = sys.argv[1] if len(sys.argv)>1 else 'assets/bundles_manifest.csv'
OUT = sys.argv[2] if len(sys.argv)>2 else 'assets/art'
MANOUT = os.path.join(OUT, 'manifest.csv')
os.makedirs(OUT, exist_ok=True)
rows_lock = threading.Lock()
seen = {}
rows = []
stats = collections.Counter()
WORKERS = int(os.environ.get('WORKERS','8'))

def safe(n):
    n = re.sub(r'[\\/:*?"<>|\x00-\x1f]', '_', n or 'unnamed')
    return n[:100] or 'unnamed'

def write_row(r):
    rows.append(r)

def process(item):
    group, name, path = item['group'], item['name'], item['file']
    local = []
    try:
        env = UnityPy.load(path)
        sprites = []
        textures = []
        others = collections.Counter()
        for obj in env.objects:
            t = obj.type.name
            if t == 'Sprite': sprites.append(obj)
            elif t == 'Texture2D': textures.append(obj)
            else: others[t] += 1
        tex_used = set()
        for obj in sprites:
            try:
                d = obj.read()
                im = d.image
                if im is None:
                    local.append({'group':group,'bundle':name,'kind':'Sprite','asset':getattr(d,'m_Name',''),'width':None,'height':None,'file':'','sha256':'','dup_of':'','error':'no image'})
                    stats['sprite_noimg'] += 1
                    continue
                try: tex_used.add(d.m_RD.texture.m_PathID)
                except Exception: pass
                sname = getattr(d,'m_Name','') or f'sprite_{obj.path_id}'
                dpath = os.path.join(OUT, safe(group), safe(name))
                os.makedirs(dpath, exist_ok=True)
                fp = os.path.join(dpath, safe(sname) + '.png')
                im.save(fp, 'PNG')
                h = hashlib.sha256(open(fp,'rb').read()).hexdigest()
                local.append({'group':group,'bundle':name,'kind':'Sprite','asset':sname,'width':im.width,'height':im.height,'file':fp,'sha256':h,'dup_of':'','error':''})
                stats['sprite_ok'] += 1
            except Exception as e:
                stats['sprite_err'] += 1
                local.append({'group':group,'bundle':name,'kind':'Sprite','asset':'','width':None,'height':None,'file':'','sha256':'','dup_of':'','error':type(e).__name__+':'+str(e)[:80]})
        for obj in textures:
            try:
                if obj.path_id in tex_used:
                    stats['tex_covered'] += 1
                    continue
                d = obj.read()
                im = d.image
                if im is None:
                    stats['tex_noimg'] += 1; continue
                tname = getattr(d,'m_Name','') or f'texture_{obj.path_id}'
                dpath = os.path.join(OUT, safe(group), safe(name), '_textures')
                os.makedirs(dpath, exist_ok=True)
                fp = os.path.join(dpath, safe(tname) + '.png')
                im.save(fp, 'PNG')
                h = hashlib.sha256(open(fp,'rb').read()).hexdigest()
                local.append({'group':group,'bundle':name,'kind':'Texture2D','asset':tname,'width':im.width,'height':im.height,'file':fp,'sha256':h,'dup_of':'','error':''})
                stats['tex_ok'] += 1
            except Exception as e:
                stats['tex_err'] += 1
        if others:
            stats['bundles_with_other'] += 1
            stats['other_total'] += sum(others.values())
        # dedupe by hash
        with rows_lock:
            for r in local:
                h = r['sha256']
                if h and h in seen:
                    r['dup_of'] = seen[h]
                    try: os.remove(r['file'])
                    except OSError: pass
                    stats['dup'] += 1
                elif h:
                    seen[h] = r['file']
                write_row(r)
        return True
    except Exception as e:
        with rows_lock:
            write_row({'group':group,'bundle':name,'kind':'ERROR','asset':'','width':None,'height':None,'file':'','sha256':'','dup_of':'','error':type(e).__name__+':'+str(e)[:150]})
            stats['bundle_err'] += 1
        return False

items = []
with open(MAN) as f:
    for r in csv.DictReader(f):
        if r['status'] in ('ok','cached') and r['file']:
            items.append(r)
print('bundles', len(items), flush=True)
fields = ['group','bundle','kind','asset','width','height','file','sha256','dup_of','error']
with open(MANOUT,'w',newline='') as f:
    w = csv.DictWriter(f, fieldnames=fields); w.writeheader()
    with ThreadPoolExecutor(max_workers=WORKERS) as ex:
        futs = [ex.submit(process, it) for it in items]
        n=0
        for fut in as_completed(futs):
            fut.result(); n+=1
            if n % 200 == 0 or n == len(items):
                with rows_lock:
                    w.writerows(rows); rows.clear(); f.flush()
                print(f'progress {n}/{len(items)} sprites {stats["sprite_ok"]} tex {stats["tex_ok"]} dup {stats["dup"]} err {stats["sprite_err"]}/{stats["tex_err"]}/{stats["bundle_err"]}', flush=True)
    with rows_lock:
        w.writerows(rows); rows.clear()
json.dump({'bundles':len(items),'stats':dict(stats)}, open(os.path.join(OUT,'summary.json'),'w'), indent=1, ensure_ascii=False)
print(json.dumps(dict(stats), ensure_ascii=False), flush=True)
