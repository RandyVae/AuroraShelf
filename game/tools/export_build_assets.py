import UnityPy, os, json, re, csv, sys, collections

SRC = sys.argv[1] if len(sys.argv)>1 else 'runtime/extract/data.unity3d'
OUT = sys.argv[2] if len(sys.argv)>2 else 'assets/build_art'
os.makedirs(OUT+'/sprites', exist_ok=True)
os.makedirs(OUT+'/textures', exist_ok=True)

def safe(n):
    n = re.sub(r'[\\/:*?"<>|\x00-\x1f]', '_', n or 'unnamed')
    return n[:120] or 'unnamed'

env = UnityPy.load(SRC)
rows=[]
used=collections.Counter()
def save(img, name, kind, typ):
    key=(kind, safe(name))
    used[key]+=1
    fn=f"{safe(name)}" + (f"__{used[key]}" if used[key]>1 else "") + ".png"
    path=os.path.join(OUT, kind, fn)
    try:
        img.save(path, 'PNG')
        rows.append({'type':typ,'name':name,'width':img.width,'height':img.height,'file':os.path.relpath(path,'.')})
    except Exception as e:
        rows.append({'type':typ,'name':name,'width':None,'height':None,'file':'','error':str(e)[:100]})

ok=err=0
for obj in env.objects:
    t=obj.type.name
    if t not in ('Texture2D','Sprite'): continue
    try:
        d=obj.read()
        img=d.image
        if img is None:
            err+=1; rows.append({'type':t,'name':getattr(d,'m_Name',''),'width':None,'height':None,'file':'','error':'no image'}); continue
        save(img, getattr(d,'m_Name',''), 'sprites' if t=='Sprite' else 'textures', t)
        ok+=1
    except Exception as e:
        err+=1; rows.append({'type':t,'name':'','width':None,'height':None,'file':'','error':type(e).__name__+':'+str(e)[:80]})
with open(os.path.join(OUT,'manifest.csv'),'w',newline='') as f:
    w=csv.DictWriter(f, fieldnames=['type','name','width','height','file','error'])
    w.writeheader(); w.writerows(rows)
print(json.dumps({'ok':ok,'err':err,'rows':len(rows)}, ensure_ascii=False))
