import struct, json, sys, re
path = "extract/Il2CppData/Metadata/global-metadata.dat"
d = open(path,'rb').read()
sanity, version = struct.unpack_from("<Ii", d, 0)
print("sanity=%08X version=%d" % (sanity, version))
fields = ["stringLiteral","stringLiteralData","string","events","properties","methods",
 "parameterDefaultValues","fieldDefaultValues","fieldAndParameterDefaultValueData",
 "fieldMarshaledSizes","parameters","fields","genericParameters","genericParameterConstraints",
 "genericContainers","nestedTypes","interfaces","vtableMethods","interfaceOffsets","typeDefinitions",
 "images","assemblies","fieldRefs","referencedAssemblies","attributeData","attributeDataRange",
 "unresolvedVirtualCallParameterTypes","unresolvedVirtualCallParameterRanges",
 "windowsRuntimeTypeNames","windowsRuntimeStrings","exportedTypeDefinitions"]
off = 8
hdr = {}
for name in fields:
    if off + 8 > len(d): break
    o, s = struct.unpack_from("<ii", d, off); off += 8
    hdr[name] = (o, s)
for k,(o,s) in hdr.items(): print(f"{k:38s} {o:>12,} {s:>12,}")
json.dump(hdr, open("tools/metadata_header.json","w"), indent=1)

def read_str(off):
    e = d.index(b"\x00", off)
    return d[off:e].decode('utf-8','replace')

# string table
so, ss = hdr["string"]; se = so+ss
strings = {}
i = so
while i < se:
    j = d.index(b"\x00", i)
    strings[i-so] = d[i:j].decode('utf-8','replace')
    i = j+1
print("strings:", len(strings))
json.dump(strings, open("tools/strings.json","w"))
# type definitions
to, ts = hdr["typeDefinitions"]
# determine struct size from a known: v31 = 0x5C? try both
for cand in (0x5C, 0x60, 0x58, 0x64):
    if ts % cand == 0:
        print("typeDefinitions size %d divisible by 0x%X => %d types" % (ts, cand, ts//cand))
# image definitions to get assembly names
io, isz = hdr["images"]
imgcount = isz // 0x28 if isz % 0x28 == 0 else None
print("images size", isz, "count(0x28)", imgcount)
names=[]
for i in range(imgcount or 0):
    base = io + i*0x28
    nameIdx, asmIdx = struct.unpack_from("<ii", d, base)
    nidx, nlen = struct.unpack_from("<ii", d, base+0x08)
    names.append((strings.get(nameIdx,"?"), nidx, nlen))
print("images sample:", [n[0] for n in names[:20]], "total", len(names))
