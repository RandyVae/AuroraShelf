#!/usr/bin/env python3
"""Build the local replica's game data from the extracted config tables.

Everything written here is derived from files already on disk:
  data/configs/*.json           (shipped client tables)
  assets/units/                 (extracted Spine skeletons)
  assets/art/                   (extracted sprite/atlas art)
  assets/audio/                 (extracted AudioClips)

Output: web/data/*.json
"""
from __future__ import annotations

import glob
import json
import os
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "web" / "data"
UNITS = {p.name for p in (ROOT / "assets" / "units").iterdir() if p.is_dir()}

NAME_FIX = {
    "zoechloe": "Zoe", "kitty": "Kitty", "regina": "Regina", "mitsuki": "Mitsuki",
    "bea": "Bea", "hilda": "Hilda", "lucretia": "Lucretia", "wendy": "Wendy",
    "nicole": "Nicole", "lorraine": "Lorraine", "blanche": "Blanche", "lilith": "Lilith",
    "rose": "Rose", "victoria": "Victoria", "morana": "Morana", "harper": "Harper",
    "sixtine": "Sixtine", "omega": "Omega", "nymeria": "Nymeria", "asha": "Asha",
    "orin": "Orin", "thora": "Thora", "arachne": "Arachne", "kirsten": "Kirsten",
    "charlotte": "Charlotte", "grace": "Grace", "belle": "Belle", "lola": "Lola",
    "casey": "Casey", "ravenna": "Ravenna", "river": "River", "naomi": "Naomi",
    "alisha": "Alisha", "pixie": "Pixie", "zelia": "Zelia", "selina": "Selina",
    "tsintah": "Tsintah", "britney": "Britney", "vespina": "Vespina", "kiana": "Kiana",
    "bonita": "Bonita", "vivienne": "Vivienne", "shelby": "Shelby", "nonna": "Nonna",
    "irma": "Irma", "esther": "Esther", "agrat": "Agrat", "falenia": "Falenia",
    "justina": "Justina", "maddie": "Maddie", "paloma": "Paloma", "gwenny": "Gwenny",
    "diya": "Diya",
}

CLASS_CN = {"melee": "近战", "ranged": "远程", "building": "建筑", "support": "辅助",
            "tank": "重装", "assassin": "刺客", "mage": "法师", "healer": "医疗"}
RACE_CN = {"order": "秩序", "chaos": "混沌", "nature": "自然"}
RARITY_CN = {"legendary": "SSR", "epic": "SR", "rare": "R", "common": "N"}
RARITY_ORDER = ["legendary", "epic", "rare", "common"]

# Skill token -> (中文名, 说明, 效果)
SKILL_SPECS = json.loads((ROOT / "web" / "data" / "skills.json").read_text()) if (ROOT / "web" / "data" / "skills.json").is_file() else {}

SKILL_RULES = [
    (("lifesteal", "Lifesteal", "Steal", "steal"), "生命汲取", "攻击并回复自身生命。", {"type": "lifesteal", "mult": 0.6}),
    (("heal", "Heal", "GetHP", "Regen"), "治愈之息", "回复全队生命。", {"type": "heal", "mult": 1.4}),
    (("Barrier", "barrier", "Shield", "shield", "DivShield", "Block"), "圣盾屏障", "为全队附加护盾，吸收伤害。", {"type": "shield", "mult": 1.8}),
    (("Freeze", "freeze", "Stun", "stun"), "冰霜禁锢", "冻结目标 1 回合，使其无法行动。", {"type": "stun", "chance": 0.55}),
    (("Poison", "poison", "Curse", "curse"), "剧毒侵蚀", "使目标持续中毒，每回合损失生命。", {"type": "poison", "mult": 0.5}),
    (("Cleanse", "cleanse", "Immune", "ImmuneSilence"), "净化之息", "解除自身负面状态并获得短时免疫。", {"type": "cleanse"}),
    (("BuffATK", "buffATK", "BuffAtk", "GiveATK", "giveATK", "BuffAttack", "buffAtk"), "狂热战意", "提升全队攻击力，持续 2 回合。", {"type": "buff_atk", "mult": 0.35}),
    (("Debuff", "LowerATK", "lowerATK", "debuffATK", "SetAttack"), "破甲咆哮", "降低目标攻击力。", {"type": "debuff_atk", "mult": 0.3}),
    (("Splash", "splash", "AoE", "AllEn", "RndEn", "RangeAttack", "RangeAttackRndEnemies"), "范围轰击", "对敌方全体造成伤害。", {"type": "aoe", "mult": 0.9}),
    (("Summon", "summon", "Spawn"), "召唤援军", "召唤一个分身协战。", {"type": "summon"}),
    (("Counter", "counter", "CounterAttack", "counter_attack"), "反击姿态", "进入反击状态，受击时回敬伤害。", {"type": "counter"}),
    (("Evade", "evade", "Agility"), "疾影闪避", "提升闪避与移速。", {"type": "evade"}),
    (("BuffHP", "buffHP", "Reborn", "reborn", "Transform", "transform"), "不朽之躯", "提升生命上限并获得额外战力。", {"type": "fortify"}),
    (("spikes", "Spikes"), "荆棘护甲", "受到近战攻击时反弹伤害。", {"type": "thorns"}),
]
DEFAULT_SKILL = ("战术突刺", "对目标造成一次强力攻击。", {"type": "damage", "mult": 1.35})


def load_table(name: str):
    p = ROOT / "data" / "configs" / f"{name}.json"
    try:
        d = json.loads(p.read_text())["data"]
    except Exception:
        return []
    if not d:
        return []
    n = len(next(iter(d.values())))
    return [{k: (v[i] if i < len(v) else "") for k, v in d.items()} for i in range(n)]


def skill_from_token(tok: str):
    for keys, name, desc, eff in SKILL_RULES:
        if any(k in tok for k in keys):
            return {"token": tok, "name": name, "desc": desc, "effect": eff}
    return {"token": tok, "name": DEFAULT_SKILL[0], "desc": DEFAULT_SKILL[1], "effect": DEFAULT_SKILL[2]}


def titleize(key: str) -> str:
    if key in NAME_FIX:
        return NAME_FIX[key]
    return key.replace("_", " ").title()


def art_dirs() -> dict[str, str]:
    out = {}
    for g in os.listdir(ROOT / "assets" / "art"):
        gp = ROOT / "assets" / "art" / g
        if not gp.is_dir():
            continue
        for d in os.listdir(gp):
            if (gp / d).is_dir():
                out.setdefault(d, f"{g}/{d}")
    return out


def pick_art(base: str, art: dict[str, str]) -> dict:
    """Return available art variants for one character key."""
    variants = {}
    cand = {
        "base": [f"{base}_big", f"{base}_lega_big", base],
        "lega": [f"{base}_lega_big"],
        "nude": [f"{base}_nude_big", f"{base}_lega_nude_big"],
    }
    for slot, names in cand.items():
        for n in names:
            if n in art:
                variants[slot] = art[n]
                break
    return variants


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    art = art_dirs()

    # ---- monsters indexed by id ----
    by_id: dict[str, list[dict]] = defaultdict(list)
    enemy_rows: dict[str, list[dict]] = defaultdict(list)
    for f in glob.glob(str(ROOT / "data" / "configs" / "Monsters_*.json")):
        rows = load_table(Path(f).stem)
        for r in rows:
            if r.get("id"):
                by_id[r["id"]].append(r)
            if r.get("animation_enemy"):
                enemy_rows[r["animation_enemy"]].append(r)

    def rep(rows: list[dict]) -> dict:
        return max(rows, key=lambda r: int(r.get("inner_id") or 0))

    # ---- heroes from Relationships ----
    rel = load_table("Relationships")
    heroes = []
    for row in rel:
        key = row["love_direct_channel"]
        mid = row["monster_id"]
        rows = by_id.get(mid, [])
        if not rows:
            continue
        r = rep(rows)
        image = r.get("image") or key
        unit = None
        for cand in (r.get("animation_name"), f"{image}_player", f"{key}_player"):
            if cand and cand in UNITS:
                unit = cand
                break
        if not unit:
            continue
        skills_raw = []
        for blob in (r.get("skills_id") or "").split(","):
            blob = blob.strip()
            if blob:
                skills_raw.append(blob)
        # de-duplicate, keep up to 3 additional skills after the basic attack
        seen_names = set()
        skills = []
        for t in dict.fromkeys(skills_raw):
            sk = skill_from_token(t)
            if sk["name"] in seen_names:
                continue
            seen_names.add(sk["name"])
            skills.append(sk)
            if len(skills) >= 3:
                break
        atk = int(float(r.get("attack") or 10))
        hp = int(float(r.get("hp") or 10))
        if atk <= 0 or (r.get("class") or "") == "building":
            continue
        for sk in skills:
            spec = SKILL_SPECS.get(sk["token"])
            if spec:
                sk.update({k: spec[k] for k in ("trigger", "action", "target", "value", "chance", "duration")})
                sk["name"] = spec["name"]
                sk["desc"] = spec["desc"]
        heroes.append({
            "id": mid,
            "key": key,
            "name": titleize(key),
            "rarity": "rare",
            "rarityCn": "R",
            "class": r.get("class") or "melee",
            "classCn": CLASS_CN.get(r.get("class") or "melee", "近战"),
            "race": r.get("race") or "order",
            "raceCn": RACE_CN.get(r.get("race") or "order", "秩序"),
            "atk": atk,
            "hp": hp,
            "battleHp": hp * 8,
            "skills": skills,
            "art": pick_art(key, art),
            "unit": unit,
            "voice": [],
        })
    # Extra warlord-tier units that are playable but not in the Relationships table.
    for key in ("dominica", "yumiko", "kira"):
        unit = f"{key}_player"
        if unit not in UNITS:
            continue
        cand_rows = [r for rows in by_id.values() for r in rows
                     if r.get("animation_name") == unit or (r.get("image") or "").startswith(key)]
        if not cand_rows:
            continue
        r = rep(cand_rows)
        atk = int(float(r.get("attack") or 10))
        hp = int(float(r.get("hp") or 10))
        if atk <= 0:
            continue
        heroes.append({
            "id": r.get("id") or key,
            "key": key,
            "name": titleize(key),
            "rarity": "rare", "rarityCn": "R",
            "class": r.get("class") or "melee",
            "classCn": CLASS_CN.get(r.get("class") or "melee", "近战"),
            "race": r.get("race") or "order",
            "raceCn": RACE_CN.get(r.get("race") or "order", "秩序"),
            "atk": atk, "hp": hp, "battleHp": hp * 8,
            "skills": [skill_from_token(t) for t in dict.fromkeys(
                x.strip() for x in (r.get("skills_id") or "").split(",") if x.strip())][:3],
            "art": pick_art(key, art),
            "unit": unit,
            "voice": [],
        })

    # Combat power decides the banner tier so the gacha has real SSR/SR/R spread.
    for h in heroes:
        h["power"] = h["atk"] * 3 + h["hp"]
        base = [{"token": "basic", "name": "战术打击", "desc": "对目标造成一次普通攻击。",
                 "effect": {"type": "damage", "mult": 1.0}}]
        h["skills"] = base + h["skills"]
    heroes.sort(key=lambda h: -h["power"])
    tiers = [(0, 18, "legendary", "SSR"), (18, 38, "epic", "SR"), (38, len(heroes), "rare", "R")]
    for lo, hi, rarity, label in tiers:
        for h in heroes[lo:hi]:
            h["rarity"], h["rarityCn"] = rarity, label
    heroes.sort(key=lambda h: (RARITY_ORDER.index(h["rarity"]) if h["rarity"] in RARITY_ORDER else 9, -h["power"]))

    # ---- voices: clips that reference the character key ----
    audio = json.loads((ROOT / "assets" / "asset_inventory.json").read_text())["audio"]
    for h in heroes:
        hits = [a for a in audio if h["key"] in a.split("/")[-1].lower()]
        h["voice"] = hits[:4]

    # ---- enemies ----
    enemies = []
    for anim, rows in enemy_rows.items():
        if anim not in UNITS:
            continue
        r = rep(rows)
        base = anim.replace("_enemy", "")
        enemies.append({
            "id": anim,
            "name": titleize(base.replace("_lvl", "").split("_lvl")[0]),
            "unit": anim,
            "class": r.get("class") or "melee",
            "race": r.get("race") or "chaos",
            "rarity": r.get("rarity") or "common",
            "atk": int(float(r.get("attack") or 8)),
            "hp": int(float(r.get("hp") or 8)),
            "battleHp": int(float(r.get("hp") or 8)) * 8,
        })
    enemies.sort(key=lambda e: e["atk"] * 2 + e["hp"])
    for i, e in enumerate(enemies):
        e["rank"] = round(i / max(1, len(enemies) - 1), 3)
    enemies.sort(key=lambda e: e["id"])

    # ---- chapters / stages ----
    story = json.loads((ROOT / "tools" / "story.json").read_text())
    bg_pool = sorted({v for k, v in art.items() if k.startswith("bg_dialogue_") and k.endswith("_small")})
    big_bgs = sorted({v for k, v in art.items() if k.startswith("bg_") and "_small" not in k})
    chapters = []
    enemy_bucket = [e for e in enemies if e["class"] != "building"]
    # Difficulty curve: chapter c draws from the c-th power band of the enemy pool.
    bands = []
    total = len(enemy_bucket)
    for ci in range(len(story["chapters"])):
        lo_r = 0.05 + 0.11 * ci
        hi_r = lo_r + 0.13
        band = [e for e in enemy_bucket if lo_r <= e.get("rank", 0.5) <= hi_r]
        if len(band) < 4:
            center = int(total * (lo_r + hi_r) / 2)
            band = enemy_bucket[max(0, center - 4):center + 6]
        bands.append(band)
    for ci, ch in enumerate(story["chapters"], 1):
        nodes = []
        band = bands[ci - 1]
        for si in range(1, ch["stages"] + 1):
            offset = (si * 3) % max(1, len(band) - 3)
            lineup = [band[(offset + k) % len(band)] for k in range(3)]
            nodes.append({
                "id": f"{ci}-{si}",
                "stage": si,
                "title": f"{ch['title']} · {si}",
                "enemy": lineup,
                "level": ci * 3 + si,
                "rewardGold": 120 + ci * 40 + si * 20,
                "rewardKeys": 1 if si == ch["stages"] else 0,
                "rewardShards": 5 + ci,
            })
        chapters.append({
            "id": ci,
            "title": ch["title"],
            "subtitle": ch["subtitle"],
            "bg": ch["bg"] if ch["bg"] in art else (big_bgs[(ci - 1) % len(big_bgs)] if big_bgs else ""),
            "stages": nodes,
            "story": ch["story"],
        })

    # ---- gacha ----
    pools = {"SSR": [h["key"] for h in heroes if h["rarity"] == "legendary"],
             "SR": [h["key"] for h in heroes if h["rarity"] == "epic"],
             "R": [h["key"] for h in heroes if h["rarity"] == "rare"]}
    if not pools["SR"]:
        pools["SR"] = pools["SSR"][:8]
    if not pools["R"]:
        pools["R"] = pools["SSR"][8:16]
    gacha = {"rates": {"SSR": 0.03, "SR": 0.18, "R": 0.79}, "pools": pools,
             "pity": 30, "costSingle": {"gold": 300, "keys": 1},
             "costTen": {"gold": 2700, "keys": 10}}

    # ---- shop ----
    shop = [
        {"id": "gold_small", "name": "金币补给", "cost": {"keys": 1}, "gain": {"gold": 1200}, "desc": "用 1 把契约钥匙换 1200 金币。"},
        {"id": "gold_large", "name": "金币空投", "cost": {"keys": 3}, "gain": {"gold": 4200}, "desc": "用 3 把契约钥匙换 4200 金币。"},
        {"id": "key_pack", "name": "钥匙包", "cost": {"gold": 800}, "gain": {"keys": 2}, "desc": "用 800 金币换 2 把契约钥匙。"},
        {"id": "shard_pack", "name": "突破碎片", "cost": {"gold": 500}, "gain": {"shards": 30}, "desc": "用 500 金币换 30 个通用突破碎片。"},
        {"id": "energy", "name": "行动力", "cost": {"gold": 200}, "gain": {"energy": 30}, "desc": "用 200 金币补充 30 点行动力。"},
    ]

    consts = load_table("Constants")
    cmap = {c["name"]: c["value"] for c in consts}

    def num(name: str, default: int) -> int:
        raw = (cmap.get(name) or "").split(",")[0].strip()
        try:
            return int(float(raw))
        except ValueError:
            return default

    constants = {
        "maxEnergy": num("max_resource_meat", 100),
        "energyRestoreMin": num("resource_restore_time_meat", 6),
        "maxGold": num("max_resource_gold", 99999999),
        "teamSize": 3,
    }

    write = lambda name, obj: (OUT / name).write_text(json.dumps(obj, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    write("heroes.json", heroes)
    write("enemies.json", enemies)
    write("chapters.json", chapters)
    write("gacha.json", gacha)
    write("shop.json", shop)
    write("constants.json", constants)
    write("strings.json", json.loads((ROOT / "tools" / "strings_cn.json").read_text()))
    write("battle.json", {
        "timerSeconds": num("battle_timer_time", 60),
        "noobTimerSeconds": num("noob_battle_timer_time", 120),
        "skillDeliveryCooldown": float((cmap.get("skills_delivery_cooldown") or "4").split(",")[0]),
        "skillAiDecisionDelay": float((cmap.get("skill_ai_decision_delay") or "0.1").split(",")[0]),
        "hpPowerCoef": float((cmap.get("hp_power_coef") or "1").split(",")[0]),
        "attackPowerCoef": float((cmap.get("attack_power_coef") or "2").split(",")[0]),
        "archerEnemyMoveSpeed": num("archers_enemy_move_speed", 80),
        "archerArrowMoveSpeed": num("archers_arrow_move_speed", 950),
        "minIqForSkill": float((cmap.get("min_iq_for_use_skill") or "0.7").split(",")[0]),
        "warlordSkillCoeffs": [cmap.get("warlord_%d_slill_coeffs" % i, "") for i in (1, 2, 3, 4)],
        "note": "values recovered from the shipped Constants config table; damage resolution still inferred",
    })

    print(json.dumps({
        "heroes": len(heroes), "enemies": len(enemies),
        "chapters": len(chapters), "stages": sum(len(c["stages"]) for c in chapters),
        "rarity": dict(Counter(h["rarity"] for h in heroes)),
        "art_variants": dict(Counter(len(h["art"]) for h in heroes)),
        "voices": sum(1 for h in heroes if h["voice"]),
    }, ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
