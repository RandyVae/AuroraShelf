import msgpack, json, os, collections
src="extract/textassets_json"; dst="extract/configs"; os.makedirs(dst, exist_ok=True)
summary=[]
for f in sorted(os.listdir(src)):
    if not f.endswith(".txt"): continue
    name=f[:-4]; b=open(os.path.join(src,f),'rb').read()
    obj=None; kind=""
    if b[:1] == b'\x7b' or b[:1] == b'\x5b':
        try:
            obj=json.loads(b); kind="json"
        except Exception: pass
    if obj is None:
        try:
            obj=msgpack.unpackb(b, raw=False, strict_map_key=False); kind="msgpack"
        except Exception as e:
            summary.append(dict(name=name, kind="unknown", err=str(e)[:80], size=len(b))); continue
    json.dump(obj, open(os.path.join(dst,name+".json"),'w'), ensure_ascii=False)
    info=dict(name=name, kind=kind, size=len(b))
    if isinstance(obj,dict) and "data" in obj and isinstance(obj["data"],dict):
        cols=obj["data"]; info["cols"]=len(cols); info["rows"]=max((len(v) for v in cols.values() if isinstance(v,list)), default=0)
        info["colnames"]=list(cols)[:12]
    elif isinstance(obj,list):
        info["rows"]=len(obj); info["colnames"]=list(obj[0]) if obj and isinstance(obj[0],dict) else None
    elif isinstance(obj,dict):
        info["cols"]=len(obj); info["colnames"]=list(obj)[:12]
    summary.append(info)
json.dump(summary, open("tools/config_summary.json","w"), ensure_ascii=False, indent=1)
print("decoded", len(summary))
for s in sorted(summary, key=lambda x:-(x.get("rows") or 0))[:40]:
    print(f"{s.get('rows',0):>6} rows {s.get('cols',0):>4} cols  {s['name']:32s} {s['kind']:8s} {s.get('colnames')}")
