import struct, json, re, collections
d = open("extract/Il2CppData/Metadata/global-metadata.dat","rb").read()
H = json.load(open("tools/metadata_header.json"))
lo, ls = H["stringLiteral"]; do, ds = H["stringLiteralData"]
n = ls // 8
lits = []
for i in range(n):
    amount, idx = struct.unpack_from("<Ii", d, lo + i*8)
    if 0 <= idx < ds and amount > 0:
        raw = d[do+idx: do+idx+amount*2] if False else d[do+idx: do+idx+amount]
        try: lits.append(raw.decode('utf-8'))
        except: lits.append(raw.decode('latin-1'))
print("literals:", len(lits))
json.dump(lits, open("tools/literals.json","w"))
urls = sorted(set(l for l in lits if re.search(r'https?://|wss?://', l)))
print("urls:", len(urls))
for u in urls[:120]: print("  ", u[:160])
print("\n=== endpoint-ish literals")
pat = re.compile(r'^(/|api|v\d)|/api/|\.php|graphql', re.I)
eps = [l for l in lits if 3 < len(l) < 80 and pat.search(l)]
for e in sorted(set(eps))[:80]: print("  ", e)
