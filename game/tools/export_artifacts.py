import json, os, csv, shutil, hashlib, collections
OUT="/Users/itou/Documents/project-hgame"
for d in ["data","data/configs","js","rea","tools"]:
    os.makedirs(os.path.join(OUT,d), exist_ok=True)

# 1) configs
for f in os.listdir("extract/configs"):
    shutil.copy2(os.path.join("extract/configs",f), os.path.join(OUT,"data/configs",f))

# 2) tables index
summ=json.load(open("tools/config_summary.json"))
with open(os.path.join(OUT,"data/tables_index.csv"),"w",newline="") as fh:
    w=csv.writer(fh); w.writerow(["table","format","rows","columns","bytes","column_names"])
    for s in sorted(summ, key=lambda x: x["name"].lower()):
        w.writerow([s["name"], s.get("kind",""), s.get("rows",""), s.get("cols",""), s.get("size",""),
                    ";".join(s.get("colnames") or [])])
print("tables:", len(summ))

# 3) il2cpp inventory
T=json.load(open("tools/types_by_assembly.json"))
M=json.load(open("tools/methods.json"))
g=collections.defaultdict(int)
for i,decl,name in M: g[decl]+=1
flat=[]
for asm,ts in T.items():
    for t in ts: flat.append((asm,t["ns"],t["name"],t["mc"]))
with open(os.path.join(OUT,"data/il2cpp_types.csv"),"w",newline="") as fh:
    w=csv.writer(fh); w.writerow(["type_index","assembly","namespace","type","declared_methods","metadata_method_count"])
    for i,(asm,ns,nm,mc) in enumerate(flat):
        w.writerow([i,asm,ns,nm,g.get(i,0),mc])
with open(os.path.join(OUT,"data/il2cpp_assemblies.csv"),"w",newline="") as fh:
    w=csv.writer(fh); w.writerow(["assembly","types","declared_methods_total"])
    for asm,ts in sorted(T.items(), key=lambda kv:-len(kv[1])):
        total=sum(g.get(flat.index((asm,t['ns'],t['name'],t['mc'])),0) for t in ts) if False else None
        w.writerow([asm, len(ts), ""])
print("types:", len(flat), "methods:", len(M))

# 4) literals
lits=sorted(set(json.load(open("tools/literals.json"))))
open(os.path.join(OUT,"data/il2cpp_literals.txt"),"w").write("\n".join(lits))
print("literals:", len(lits))

# 5) res_db + metadata header
shutil.copy2("extract/configs/res_db.json", os.path.join(OUT,"data/res_db.json"))
shutil.copy2("tools/metadata_header.json", os.path.join(OUT,"data/metadata_header.json"))

# 6) js
for f in os.listdir("js_small"):
    shutil.copy2(os.path.join("js_small",f), os.path.join(OUT,"js",f))
shutil.copy2("build/build.framework.js", os.path.join(OUT,"js","framework.js"))
shutil.copy2("build/46de06e95896b02f2f309f5efaecd0f8.loader.js", os.path.join(OUT,"js","unity.loader.js"))
shutil.copy2("index.html", os.path.join(OUT,"js","page.index.html"))

# 7) rea evidence
shutil.copy2("wasm_imports_evidence.json", os.path.join(OUT,"rea/wasm_imports_evidence.json"))

# 8) tools
for f in ["uwd.py","uwd_extract.py","il2cpp_meta.py","il2cpp_dump.py","il2cpp_ns.py","literals.py","configs.py","textassets3.py","decomp.py","unity_inv.py","export_artifacts.py"]:
    if os.path.exists("tools/"+f): shutil.copy2("tools/"+f, os.path.join(OUT,"tools",f))
print("done")
