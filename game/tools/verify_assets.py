import csv, json, os, hashlib, collections
from PIL import Image
res={}
# 1 bundles
bun=list(csv.DictReader(open('assets/bundles_manifest.csv')))
bad=[]; total=0; byhash=collections.Counter()
for r in bun:
    p=r['file']; total+=int(r['size'])
    if r['status'] not in ('ok','cached') or not p or not os.path.exists(p):
        bad.append({'name':r['name'],'reason':'missing','status':r['status']}); continue
    if os.path.getsize(p)!=int(r['size']):
        bad.append({'name':r['name'],'reason':'size'}); continue
res['bundles']={'count':len(bun),'bytes':total,'bad':bad[:10],'bad_count':len(bad),'groups':len(set(r['group'] for r in bun))}
# 2 art
art=list(csv.DictReader(open('assets/art/manifest.csv')))
files=[r for r in art if r.get('file') and os.path.exists(r['file'])]
dups=[r for r in art if r.get('dup_of')]
errs=[r for r in art if r.get('error')]
badimg=[]
w=h=0
for r in files:
    try:
        with Image.open(r['file']) as im:
            if im.size != (int(r['width']),int(r['height'])): badimg.append(r['file'])
    except Exception as e:
        badimg.append(r['file'])
res['art']={'rows':len(art),'files':len(files),'unique_hashes':len(set(r['sha256'] for r in files if r['sha256'])),
            'duplicates':len(dups),'errors':len(errs),'bad_images':badimg[:5],'bad_image_count':len(badimg),
            'sprites':sum(1 for r in files if r['kind']=='Sprite'),'textures':sum(1 for r in files if r['kind']=='Texture2D')}
# 3 build art
b=list(csv.DictReader(open('assets/build_art/manifest.csv')))
bf=[r for r in b if r.get('file') and os.path.exists(r['file'])]
res['build_art']={'rows':len(b),'files':len(bf),'sprites':sum(1 for r in bf if r['type']=='Sprite'),'textures':sum(1 for r in bf if r['type']=='Texture2D'),
                  'bytes':sum(os.path.getsize(r['file']) for r in bf)}
# 4 site art
sa=[]
for f in sorted(os.listdir('assets/site_art')):
    p=os.path.join('assets/site_art',f)
    if os.path.isfile(p):
        sa.append({'file':f,'bytes':os.path.getsize(p),'sha256':hashlib.sha256(open(p,'rb').read()).hexdigest()})
res['site_art']={'files':len(sa),'items':sa}
json.dump(res, open('assets/verification.json','w'), indent=1, ensure_ascii=False)
print(json.dumps({k:(v if k!='site_art' else {'files':v['files']}) for k,v in res.items()}, ensure_ascii=False, indent=1))
