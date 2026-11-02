import UnityPy, collections, json, sys
p = "extract/data.unity3d"
env = UnityPy.load(p)
print("objects:", len(env.objects), " files:", len(env.files) if hasattr(env,'files') else '?')
c = collections.Counter()
for o in env.objects:
    c[o.type.name] += 1
for k,v in c.most_common(40): print(f"{v:>7}  {k}")
print("total types:", len(c))
