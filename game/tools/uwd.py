import struct, sys, os, json
p = "build/webgl.data"
data = open(p,'rb').read()
magic = data[:16]
print("magic:", magic)
assert magic.startswith(b"UnityWebData1.0"), magic
hdr_end = struct.unpack_from("<I", data, 16)[0]
print("header end:", hdr_end)
off = 20
files = []
while off < hdr_end:
    foff, fsize, plen = struct.unpack_from("<III", data, off)
    off += 12
    path = data[off:off+plen].decode('utf-8', 'replace')
    off += plen
    files.append((path, foff, fsize))
print("entries:", len(files))
tot=0
for path, foff, fsize in files:
    print(f"{fsize:>12,}  {path}")
    tot+=fsize
print("total:", f"{tot:,}")
json.dump([{"path":a,"offset":b,"size":c} for a,b,c in files], open("tools/uwd_manifest.json","w"), indent=1)
