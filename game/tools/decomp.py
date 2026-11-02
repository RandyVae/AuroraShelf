import os, gzip, json, collections
src="extract/textassets_raw"; dst="extract/textassets_json"; os.makedirs(dst, exist_ok=True)
ok=0; fail=[]; gz=0
for f in sorted(os.listdir(src)):
    b = open(os.path.join(src,f),'rb').read()
    name = f[:-4]
    data = b
    if b[:2] == b'\x1f\x8b':
        gz += 1
        try: data = gzip.decompress(b)
        except Exception as e: fail.append((name,str(e))); continue
    open(os.path.join(dst,name+".txt"),'wb').write(data)
    ok+=1
print("ok", ok, "gzip", gz, "fail", len(fail), fail[:5])
