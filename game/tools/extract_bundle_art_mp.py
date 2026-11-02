import UnityPy, os, sys, csv, json, hashlib, collections, re, multiprocessing as mp

MAN = sys.argv[1] if len(sys.argv)>1 else 'assets/bundles_manifest.csv'
OUT = sys.argv[2] if len(sys.argv)>2 else 'assets/art'
WORKERS = int(os.environ.get('WORKERS','6'))
TAG = os.environ.get('TAG','')

def safe(n):
    n = re.sub(r'[\\/:*?"<>|\x00-\x1f]', '_', n or 'unnamed')
    return n[:100] or 'unnamed'

def process(item):
    group, name, path = item['group'], item['name'], item['file']
    rows=[]
    used=collections.Counter()
    try:
        env=UnityPy.load(path)
        sprites=[]; textures=[]; others=collections.Counter()
        for obj in env.objects:
            t=obj.type.name
            if t=='Sprite': sprites.append(obj)
            elif t=='Texture2D': textures.append(obj)
            else: others[t]+=1
        tex_used=set()
        def uniq(kind, base):
            used[(kind,base)]+=1
            fn=base if used[(kind,base)]==1 else f'{base}__{used[(kind,base)]}'
            return fn
        for obj in sprites:
            try:
                d=obj.read(); im=d.image
                if im is None:
                    rows.append([group,name,'Sprite',getattr(d,'m_Name',''),None,None,'','','no image']); continue
                try: tex_used.add(d.m_RD.texture.m_PathID)
                except Exception: pass
                sname=getattr(d,'m_Name','') or f'sprite_{obj.path_id}'
                dpath=os.path.join(OUT, safe(group), safe(name)); os.makedirs(dpath, exist_ok=True)
                fp=os.path.join(dpath, uniq('s',safe(sname))+'.png')
                im.save(fp,'PNG')
                h=hashlib.sha256(open(fp,'rb').read()).hexdigest()
                rows.append([group,name,'Sprite',sname,im.width,im.height,fp,h,''])
            except Exception as e:
                rows.append([group,name,'Sprite','',None,None,'','',type(e).__name__+':'+str(e)[:80]])
        for obj in textures:
            try:
                if obj.path_id in tex_used: continue
                d=obj.read(); im=d.image
                if im is None: continue
                tname=getattr(d,'m_Name','') or f'texture_{obj.path_id}'
                dpath=os.path.join(OUT, safe(group), safe(name), '_textures'); os.makedirs(dpath, exist_ok=True)
                fp=os.path.join(dpath, uniq('t',safe(tname))+'.png')
                im.save(fp,'PNG')
                h=hashlib.sha256(open(fp,'rb').read()).hexdigest()
                rows.append([group,name,'Texture2D',tname,im.width,im.height,fp,h,''])
            except Exception as e:
                rows.append([group,name,'Texture2D','',None,None,'','',type(e).__name__+':'+str(e)[:80]])
        other=dict(others)
        return rows, other
    except Exception as e:
        return [[group,name,'ERROR','',None,None,'','',type(e).__name__+':'+str(e)[:120]]], {}

if __name__=='__main__':
    os.makedirs(OUT, exist_ok=True)
    items=[]
    with open(MAN) as f:
        for r in csv.DictReader(f):
            if r['status'] in ('ok','cached') and r['file']: items.append(r)
    print('bundles', len(items), flush=True)
    fields=['group','bundle','kind','asset','width','height','file','sha256','dup_of','error']
    seen={}
    rowsbuf=[]
    stats=collections.Counter()
    man=open(os.path.join(OUT,'manifest.csv'),'w',newline=''); w=csv.writer(man); w.writerow(fields)
    other_all=collections.Counter()
    with mp.Pool(WORKERS) as pool:
        for n,(rows,other) in enumerate(pool.imap_unordered(process, items, chunksize=4),1):
            for r in rows:
                r=list(r)
                h=r[7]
                if h and h in seen:
                    r[8]=seen[h]
                    try: os.remove(r[6])
                    except OSError: pass
                    stats['dup']+=1
                elif h:
                    seen[h]=r[6]
                rowsbuf.append(r)
                if r[2]=='Sprite' and not r[8]: stats['sprite_ok']+=1
                elif r[2]=='Texture2D' and not r[8]: stats['tex_ok']+=1
            for k,v in other.items(): other_all[k]+=v
            if len(rowsbuf)>=500:
                w.writerows(rowsbuf); rowsbuf=[]; man.flush()
            if n%200==0 or n==len(items):
                print(f'progress {n}/{len(items)} sprites {stats["sprite_ok"]} tex {stats["tex_ok"]} dup {stats["dup"]}', flush=True)
    w.writerows(rowsbuf); man.close()
    json.dump({'bundles':len(items),'stats':dict(stats),'other_objects':dict(other_all.most_common(40))}, open(os.path.join(OUT,'summary.json'),'w'), indent=1, ensure_ascii=False)
    print(json.dumps({'stats':dict(stats),'top_other':dict(other_all.most_common(20))}, ensure_ascii=False), flush=True)
