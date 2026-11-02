import struct, json, re, collections
d = open("extract/Il2CppData/Metadata/global-metadata.dat","rb").read()
H = json.load(open("tools/metadata_header.json"))
so, ss = H["string"]
def s(off):
    if off <= 0 or off >= ss: return ""
    e = d.index(b"\x00", so+off)
    return d[so+off:e].decode('utf-8','replace')
TS = 28; TYPE_SZ = 88
def types():
    to, ts = H["typeDefinitions"]
    n = ts // TYPE_SZ
    for i in range(n):
        b = to + i*TYPE_SZ
        nameIdx, nsIdx, byval, byref, decl, parent, elem, genc, flags = struct.unpack_from("<9i", d, b)
        fstart, mstart, estart, pstart, nstart, istart, vstart, iostart = struct.unpack_from("<8i", d, b+36)
        mc, pc, fc, ec, ntc, vc, ic, ioc = struct.unpack_from("<8H", d, b+68)
        yield dict(idx=i, ns=s(nsIdx), name=s(nameIdx), flags=flags, parent=parent,
                   ms=mstart, mc=mc, fs=fstart, fc=fc, nesting=ntc)
T = list(types())
print("types parsed:", len(T), "first:", T[0]["ns"], T[0]["name"], "last:", T[-1]["ns"], T[-1]["name"])
def methods():
    mo, ms = H["methods"]
    n = ms // TS
    for i in range(n):
        b = mo + i*TS
        nameIdx, declType = struct.unpack_from("<ii", d, b)
        yield i, declType, s(nameIdx)
M = list(methods())
print("methods parsed:", len(M), "sample:", M[0][2], "|", M[100][2])
# images
io, isz = H["images"]
imgs=[]
for i in range(isz//40):
    b = io + i*40
    nameIdx, asmIdx, tstart, tcount = struct.unpack_from("<4i", d, b)
    imgs.append(dict(idx=i, name=s(nameIdx), tstart=tstart, tcount=tcount))
print("images:", len(imgs))
# map types to images
per = collections.defaultdict(list)
for im in imgs:
    for t in T[im["tstart"]:im["tstart"]+im["tcount"]]:
        per[im["name"]].append(t)
summary = sorted(((k, len(v)) for k,v in per.items()), key=lambda x:-x[1])
print("\n=== types per assembly (top 40)")
for k,v in summary[:40]: print(f"{v:>6}  {k}")
json.dump({k:[{kk:t[kk] for kk in ('ns','name','ms','mc','fs','fc')} for t in v] for k,v in per.items()},
          open("tools/types_by_assembly.json","w"))
json.dump([[i,t,mm] for i,t,mm in M], open("tools/methods.json","w"))
