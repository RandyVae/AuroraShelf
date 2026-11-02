import csv, json, os, collections
ART='assets/art/manifest.csv'; BUN='assets/bundles_manifest.csv'; BUILD='assets/build_art/manifest.csv'
art=list(csv.DictReader(open(ART)))
bun=list(csv.DictReader(open(BUN)))
build=list(csv.DictReader(open(BUILD)))
uniq=[r for r in art if r.get('file') and not r.get('dup_of') and os.path.exists(r['file'])]
per=collections.defaultdict(lambda: {'sprites':0,'textures':0,'bytes':0,'width_max':0,'pixels':0})
for r in uniq:
    g=r['group']; k='sprites' if r['kind']=='Sprite' else 'textures'
    per[g][k]+=1
    try:
        per[g]['bytes']+=os.path.getsize(r['file'])
        w=int(r['width'] or 0); h=int(r['height'] or 0)
        per[g]['pixels']+=w*h; per[g]['width_max']=max(per[g]['width_max'],w)
    except OSError: pass
bld=[r for r in build if r.get('file') and os.path.exists(r['file'])]
vids=sorted(os.listdir('assets/videos')) if os.path.isdir('assets/videos') else []
site=[f for f in os.listdir('assets/site_art') if os.path.isfile(os.path.join('assets/site_art',f))] if os.path.isdir('assets/site_art') else []
out={
 'generated_for':'lust-goddess.com WebGL build 2026_10_22_10_29 / v23.0.0',
 'als':{'bundles':len(bun),'bytes':sum(int(r['size']) for r in bun),'groups':len(set(r['group'] for r in bun))},
 'bundles_unique_images':{'sprites':sum(1 for r in uniq if r['kind']=='Sprite'),'textures':sum(1 for r in uniq if r['kind']=='Texture2D'),'bytes':sum(per[g]['bytes'] for g in per)},
 'build_art':{'sprites':sum(1 for r in bld if r['type']=='Sprite'),'textures':sum(1 for r in bld if r['type']=='Texture2D'),'bytes':sum(os.path.getsize(r['file']) for r in bld)},
 'videos':{'count':len(vids),'bytes':sum(os.path.getsize(os.path.join('assets/videos',f)) for f in vids)},
 'site_art':{'count':len(site)},
 'groups':{g:{**per[g],'bytes_h':round(per[g]['bytes']/1e6,1)} for g in sorted(per,key=lambda x:-per[x]['bytes'])},
}
json.dump(out,open('assets/catalog.json','w'),indent=1,ensure_ascii=False)
L=['# 美術資源目錄（Lust Goddess v23.0.0／WebGL build 2026_10_22_10_29）','',
'## 總量','',
f"- 資產服務 AssetBundle：**{out['als']['bundles']:,}** 個／**{out['als']['bytes']/1e9:.2f} GB**（{out['als']['groups']} 組）",
f"- 解包後去重圖片：Sprite **{out['bundles_unique_images']['sprites']:,}**／Texture2D **{out['bundles_unique_images']['textures']:,}**／**{out['bundles_unique_images']['bytes']/1e9:.2f} GB**",
f"- 主構建內建圖：Sprite **{out['build_art']['sprites']:,}**／Texture2D **{out['build_art']['textures']:,}**／**{out['build_art']['bytes']/1e6:.0f} MB**",
f"- 原始影片：**{out['videos']['count']}** 個 MP4／**{out['videos']['bytes']/1e6:.0f} MB**（maze_video）",
f"- 站點美術：**{out['site_art']['count']}** 個檔案",
'','## 分組（AssetBundle → 去重圖片）','','| 分組 | Sprite | Texture2D | 圖片體積 | 最大邊 |','|---|---:|---:|---:|---:|']
for g,v in out['groups'].items():
    L.append(f"| {g} | {v['sprites']} | {v['textures']} | {v['bytes_h']} MB | {v['width_max']} |")
open('assets/README.md','w').write('\n'.join(L)+'\n')
print('ok', out['videos'], out['site_art'])
