import UnityPy, os, struct
env = UnityPy.load("extract/data.unity3d")
out="extract/textassets_raw"; os.makedirs(out, exist_ok=True)
def align4(x): return (x + 3) & ~3
n=0; rows=[]
for o in env.objects:
    if o.type.name != "TextAsset": continue
    raw = o.get_raw_data()
    p = 0
    namelen = struct.unpack_from("<i", raw, p)[0]; p += 4
    name = raw[p:p+namelen].decode('utf-8','replace'); p = align4(p+namelen)
    slen = struct.unpack_from("<i", raw, p)[0]; p += 4
    data = raw[p:p+slen]
    open(os.path.join(out, name+".bin"),"wb").write(data)
    rows.append((name, len(data), data[:8].hex()))
    n+=1
print("written", n)
print("non-plaintext sample:")
for name,l,h in sorted(rows, key=lambda r:-r[1])[:8]: print(f"  {name:30s} {l:>9,} {h}")
