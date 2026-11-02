#!/usr/bin/env python3
"""Single-pass inventory/extraction over every downloaded AssetBundle.

Outputs:
  assets/units/<name>/skeleton.json   Spine 4.2 skeleton (JSON)
  assets/units/<name>/<name>.atlas    Spine atlas text
  assets/audio/<group>/<bundle>/<clip>.<ext>
  assets/asset_inventory.json         object-type counts + spine/audio index
"""
from __future__ import annotations

import csv
import json
import os
import sys
from collections import Counter
from multiprocessing import Pool
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUNDLES = ROOT / "assets" / "bundles"
UNITS = ROOT / "assets" / "units"
AUDIO = ROOT / "assets" / "audio"
INV = ROOT / "assets" / "asset_inventory.json"


def manifest() -> list[tuple[str, str, str]]:
    rows = []
    with open(ROOT / "assets" / "bundles_manifest.csv", newline="") as fh:
        for row in csv.DictReader(fh):
            if row.get("status") == "ok":
                rows.append((row["group"], row["name"], row["file"]))
    return rows


AUDIO_EXT = {
    0: ".wav",      # PCM
    1: ".ogg",      # Vorbis
    2: ".wav",      # ADPCM
    3: ".mp3",
    4: ".aiff",
    5: ".wav",      # VAG
    6: ".m4a",      # MPEG
    7: ".m4a",      # AAC
}


def scan(job: tuple[str, str, str]) -> dict:
    group, name, rel = job
    out = {"group": group, "name": name, "types": {}, "spine": None, "atlas": None, "audio": []}
    path = str(ROOT / rel)
    try:
        import UnityPy

        env = UnityPy.load(path)
        texts: dict[str, str] = {}
        types: Counter[str] = Counter()
        for obj in env.objects:
            types[obj.type.name] += 1
            if obj.type.name == "TextAsset":
                try:
                    ta = obj.read()
                    texts[ta.m_Name] = ta.m_Script
                except Exception:
                    pass
            elif obj.type.name == "AudioClip":
                try:
                    clip = obj.read()
                    for fname, data in clip.samples.items():
                        ext = AUDIO_EXT.get(clip.m_CompressionFormat, ".bin")
                        sab = AUDIO / group / name
                        sab.mkdir(parents=True, exist_ok=True)
                        (sab / (fname if fname.endswith(ext) else fname.rsplit(".", 1)[0] + ext)).write_bytes(data)
                        out["audio"].append(f"{group}/{name}/{fname.split('.')[0]}{ext}")
                except Exception:
                    pass
        out["types"] = dict(types)
        skel = None
        for tname, body in texts.items():
            if isinstance(body, str) and body.lstrip().startswith('{"skeleton"'):
                skel = (tname, body)
        if skel:
            tname, body = skel
            d = UNITS / name
            d.mkdir(parents=True, exist_ok=True)
            (d / "skeleton.json").write_text(body, encoding="utf-8")
            out["spine"] = name
            for tname2, body2 in texts.items():
                if tname2.endswith(".atlas") or (
                    isinstance(body2, str) and "\nbounds:" in body2 and ".png" in body2
                ):
                    (d / f"{name}.atlas").write_text(body2, encoding="utf-8")
                    out["atlas"] = f"{name}.atlas"
                    break
    except Exception as exc:  # noqa: BLE001
        out["error"] = f"{type(exc).__name__}: {exc}"
    return out


def main() -> int:
    jobs = manifest()
    if len(sys.argv) > 1:
        jobs = jobs[: int(sys.argv[1])]
    print(f"scanning {len(jobs)} bundles", flush=True)
    UNITS.mkdir(parents=True, exist_ok=True)
    AUDIO.mkdir(parents=True, exist_ok=True)
    results = []
    with Pool(8) as pool:
        for i, res in enumerate(pool.imap_unordered(scan, jobs, chunksize=8), 1):
            results.append(res)
            if i % 500 == 0:
                print(f"{i}/{len(jobs)}", flush=True)
    inv = {
        "bundles": len(results),
        "errors": sum(1 for r in results if r.get("error")),
        "spines": [r["name"] for r in results if r["spine"]],
        "spine_count": sum(1 for r in results if r["spine"]),
        "audio": [a for r in results for a in r["audio"]],
        "audio_count": sum(len(r["audio"]) for r in results),
        "type_totals": dict(Counter({k: v for r in results for k, v in r["types"].items()})),
        "records": results,
    }
    INV.write_text(json.dumps(inv, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps({k: v for k, v in inv.items() if k != "records" and k != "audio" and k != "spines"}, ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
