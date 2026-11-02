import json, collections, re, struct
T = json.load(open("tools/types_by_assembly.json"))
ac = T["Assembly-CSharp.dll"]
ns = collections.Counter(t["ns"] for t in ac)
print("Assembly-CSharp namespaces:", len(ns))
for k,v in ns.most_common(60): print(f"{v:>5}  {k or '<global>'}")
