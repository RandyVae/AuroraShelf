#!/usr/bin/env python3
"""Curate the extracted original assets into the local game's web/assets tree.

Reads:  assets/art, assets/units, assets/audio, assets/videos
Writes: web/assets/{heroes,units,bg,audio,videos} + web/data/*.json path rewrite
"""
from __future__ import annotations

import glob
import json
import os
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
WEB = ROOT / "web"
ASSETS = WEB / "assets"
ART = ROOT / "assets" / "art"

UNITS_SRC = ROOT / "assets" / "units"
AUDIO_SRC = ROOT / "assets" / "audio"
VIDEO_SRC = ROOT / "assets" / "videos"


def art_file(rel: str) -> Path | None:
    """rel = '<group>/<name>' -> the exported png."""
    p = ART / rel
    if not p.is_dir():
        return None
    name = p.name
    for cand in (p / f"{name}.png", p / "_textures" / f"{name}.png"):
        if cand.is_file():
            return cand
    hits = sorted(p.glob("*.png")) + sorted((p / "_textures").glob("*.png"))
    return hits[0] if hits else None


def save_webp(src: Path, dst: Path, max_w: int, quality: int = 82, lossless: bool = False) -> bool:
    try:
        im = Image.open(src).convert("RGBA" if src.suffix.lower() == ".png" else "RGB")
    except Exception:
        return False
    if im.width > max_w:
        h = round(im.height * max_w / im.width)
        im = im.resize((max_w, h), Image.LANCZOS)
    dst.parent.mkdir(parents=True, exist_ok=True)
    if lossless:
        im.save(dst, "WEBP", lossless=True, quality=100, method=6)
    else:
        im.save(dst, "WEBP", quality=quality, method=4, alpha_quality=100)
    return True



def upscale_page(src: Path, dst: Path, factor: int = 2) -> bool:
    """Upscale a small spine page so large displays interpolate instead of showing texel blocks."""
    try:
        im = Image.open(src).convert("RGBA")
    except Exception:
        return False
    if im.width > 512 or im.height > 512:
        return False
    im = im.resize((im.width * factor, im.height * factor), Image.LANCZOS)
    dst.parent.mkdir(parents=True, exist_ok=True)
    im.save(dst, "WEBP", lossless=True, quality=100, method=6)
    return True


def scale_atlas(text: str, factor: int) -> str:
    out = []
    for line in text.splitlines():
        line = line.strip()
        if line.lower().startswith("size:"):
            w, h = line.split(":", 1)[1].split(",")
            out.append("size:%d,%d" % (int(w) * factor, int(h) * factor))
        elif line.lower().startswith("bounds:"):
            x, y, w, h = line.split(":", 1)[1].split(",")
            out.append("bounds:%d,%d,%d,%d" % (int(x) * factor, int(y) * factor, int(w) * factor, int(h) * factor))
        else:
            out.append(line)
    return "\n".join(out) + "\n"


def build_heroes() -> dict:
    heroes = json.loads((WEB / "data" / "heroes.json").read_text())
    made = {}
    for h in heroes:
        key = h["key"]
        out = {}
        for slot, rel in h["art"].items():
            src = art_file(rel)
            if not src:
                continue
            relout = f"heroes/{key}/{slot}.webp"
            if save_webp(src, ASSETS / relout, 900 if slot == "base" else 760):
                out[slot] = f"assets/{relout}"
                thumb = f"heroes/{key}/{slot}_thumb.webp"
                if save_webp(src, ASSETS / thumb, 300, 78):
                    out[f"{slot}Thumb"] = f"assets/{thumb}"
        h["artWeb"] = out
        made[key] = out
    (WEB / "data" / "heroes.json").write_text(json.dumps(heroes, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    return made


def copy_unit(unit: str) -> bool:
    src = UNITS_SRC / unit
    if not (src / "skeleton.json").is_file():
        return False
    dst = ASSETS / "units" / unit
    dst.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src / "skeleton.json", dst / "skeleton.json")
    atlas = next(iter(sorted(src.glob("*.atlas"))), None)
    if not atlas:
        return False
    text = atlas.read_text(encoding="utf-8", errors="replace")
    pages = [ln.strip() for ln in text.splitlines() if ln.strip().lower().endswith(".png")]
    ok_pages = {}
    for page in pages:
        cands = list(ART.glob(f"*/{unit}/_textures/{page}")) + list(ART.glob(f"*/{unit}/{page}"))
        if not cands:
            cands = list(ART.glob(f"*/{unit.rsplit('_enemy', 1)[0]}/_textures/{page}"))
        if not cands:
            continue
        newpage = page[:-4] + ".webp"
        # Lossless only: upscaling the page would invalidate baked mesh UVs in skeleton.json
        # (verified: 2x upscale + doubled atlas bounds tears the character apart).
        if save_webp(cands[0], dst / newpage, 4096, 100, lossless=True):
            ok_pages[page] = newpage
    if not ok_pages:
        return False
    for old, new in ok_pages.items():
        text = text.replace(old, new)

    (dst / f"{unit}.atlas").write_text(text, encoding="utf-8")
    return True


def build_units() -> tuple[int, int]:
    heroes = json.loads((WEB / "data" / "heroes.json").read_text())
    enemies = json.loads((WEB / "data" / "enemies.json").read_text())
    ok = miss = 0
    for h in heroes:
        if copy_unit(h["unit"]):
            h["unitWeb"] = f"assets/units/{h['unit']}/{h['unit']}.atlas"
            ok += 1
        else:
            miss += 1
    for e in enemies:
        if copy_unit(e["unit"]):
            e["unitWeb"] = f"assets/units/{e['unit']}/{e['unit']}.atlas"
            ok += 1
        else:
            miss += 1
    (WEB / "data" / "heroes.json").write_text(json.dumps(heroes, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    (WEB / "data" / "enemies.json").write_text(json.dumps(enemies, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    return ok, miss


def build_backgrounds() -> dict:
    chapters = json.loads((WEB / "data" / "chapters.json").read_text())
    out = {}
    for ch in chapters:
        base = ch["bg"]
        names = [base.replace("_small", ""), base]
        rel = None
        for name in names:
            hits = [p for p in ART.glob(f"*/{name}/*.png")]
            if hits:
                # prefer the largest source (the _small variants are thumbnails)
                hits.sort(key=lambda p: p.stat().st_size, reverse=True)
                rel = hits[0]
                break
        if rel and save_webp(rel, ASSETS / "bg" / f"ch{ch['id']}.webp", 2048, 78):
            ch["bgWeb"] = f"assets/bg/ch{ch['id']}.webp"
    hub = art_file("main/bg_main_aw_city") or art_file("main/lobby_bundle_bg")
    for g in ART.glob("*/bg_main_aw_city"):
        hub = g / "bg_main_aw_city.png"
        break
    if hub and save_webp(hub, ASSETS / "bg" / "hub.webp", 1920, 80):
        out["hub"] = "assets/bg/hub.webp"
    battle = None
    for name in ("bg_dialogue_battlefield", "bg_dialogue_robotwar", "bg_dialogue_arena"):
        hits = list(ART.glob(f"*/{name}/*.png"))
        if hits:
            battle = hits[0]
            break
    if battle and save_webp(battle, ASSETS / "bg" / "battle.webp", 1920, 80):
        out["battle"] = "assets/bg/battle.webp"
    # Drop enemy lineups whose skeleton could not be materialised.
    enemies = json.loads((WEB / "data" / "enemies.json").read_text())
    valid = {e["id"] for e in enemies if e.get("unitWeb")}
    for ch in chapters:
        for st in ch["stages"]:
            st["enemy"] = [e for e in st["enemy"] if e.get("unit") in valid] or st["enemy"]
    (WEB / "data" / "enemies.json").write_text(json.dumps(enemies, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    (WEB / "data" / "chapters.json").write_text(json.dumps(chapters, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    out["chapters"] = "assets/bg/ch*.webp"
    return out


def build_audio() -> list:
    inv = json.loads((ROOT / "assets" / "asset_inventory.json").read_text())
    clips = inv["audio"]
    keep = []
    for rel in clips:
        name = rel.split("/")[-1]
        low = name.lower()
        if low.startswith(("bgm_", "mus_")) or low.startswith(("skill_", "fight_", "attack_")) or "sfx" in low:
            keep.append(rel)
        elif low.startswith(("vo_",)) or any(k in low for k in ("ahh", "moan", "breath", "giggle")) and False:
            keep.append(rel)
    # hero voices referenced from data
    heroes = json.loads((WEB / "data" / "heroes.json").read_text())
    for h in heroes:
        keep.extend(h.get("voice", []))
    keep = sorted(set(keep))
    out = []
    for rel in keep:
        src = AUDIO_SRC / rel
        if not src.is_file():
            continue
        dst = ASSETS / "audio" / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        out.append(rel)
    return out


def build_videos(limit: int = 4) -> list:
    vids = sorted(VIDEO_SRC.glob("*.mp4"))
    vids = [v for v in vids if "_censored" not in v.name and v.stat().st_size < 6_000_000][:limit]
    out = []
    for v in vids:
        dst = ASSETS / "videos" / v.name
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(v, dst)
        out.append(f"assets/videos/{v.name}")
    return out


def main() -> int:
    ASSETS.mkdir(parents=True, exist_ok=True)
    made = build_heroes()
    ok, miss = build_units()
    bgs = build_backgrounds()
    audio = build_audio()
    videos = build_videos()
    (WEB / "data" / "videos.json").write_text(json.dumps(videos, ensure_ascii=False), encoding="utf-8")
    total = sum(f.stat().st_size for f in ASSETS.rglob("*") if f.is_file())
    print(json.dumps({
        "heroes_art": len(made), "units_ok": ok, "units_missing": miss,
        "backgrounds": bgs, "audio_clips": len(audio), "videos": videos,
        "assets_mb": round(total / 1e6, 1),
    }, ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
