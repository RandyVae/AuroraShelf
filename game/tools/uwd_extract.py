import struct, os, json
data = open("build/webgl.data",'rb').read()
hdr_end = struct.unpack_from("<I", data, 16)[0]
off = 20; files=[]
while off < hdr_end:
    foff, fsize, plen = struct.unpack_from("<III", data, off); off += 12
    path = data[off:off+plen].decode(); off += plen
    files.append((path,foff,fsize))
out = "extract"
for path,foff,fsize in files:
    dst = os.path.join(out, path)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    with open(dst,'wb') as f: f.write(data[foff:foff+fsize])
    print(f"{fsize:>12,}  {dst}")
