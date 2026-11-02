#!/usr/bin/env python3
"""Recover the original skill-token grammar from the IL2CPP type inventory.

The game encodes each monster's kit as comma-separated C#-style skill tokens
(e.g. AppSelfBuffATKInLine, AtkdFreezeEn, DeathSelfDmgFrNeighbours).  The code
behind them lives in wasm, but the *vocabulary* of triggers / actions / targets
is visible in the metadata type table (BattlefieldScripts.Actions and
BattlefieldScripts.Skills.Implementation.*).  This script classifies every
token used by the shipped config tables with that vocabulary and emits
web/data/skills.json.

Anything the vocabulary does not cover is marked confidence="inferred".
"""
from __future__ import annotations

import glob
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "web" / "data" / "skills.json"

TRIGGERS = [
    ("on_appear", ["App", "Appear", "Start", "ST"]),
    ("on_attacked", ["Atkd", "Attaked", "Attacked", "AttackedBy", "Hit"]),
    ("on_death", ["Death", "Deth", "Die"]),
    ("on_kill", ["Kill"]),
    ("on_heal", ["Heal"]),
    ("on_turn", ["Turn", "Every", "Round", "ET"]),
    ("on_attack", ["Atk", "Attack"]),
]

ACTIONS = [
    ("instant_kill", ["InstantKill", "SilentKill", "Destroy"]),
    ("reborn", ["Reborn", "Revive"]),
    ("summon", ["Summon", "Spawn"]),
    ("transform", ["Transform", "Morph"]),
    ("steal", ["Steal"]),
    ("silence", ["Silence", "Mute"]),
    ("cleanse", ["Cleanse", "Purify"]),
    ("counter", ["Counter"]),
    ("shield", ["Barrier", "Shield", "DivShield", "Block", "Spikes"]),
    ("freeze", ["Freeze", "Stun"]),
    ("poison", ["Poison", "Bleed", "Curse"]),
    ("heal", ["Heal", "GetHP", "Vampiric"]),
    ("buff_atk", ["BuffATK", "BuffAttack", "BuffAtk", "GiveATK", "GiveAtk", "Strong", "Amp"]),
    ("debuff_atk", ["Debuff", "LowerATK", "SetAttack", "Weak"]),
    ("buff_hp", ["BuffHP", "AddHP", "Immune"]),
    ("damage", ["Damage", "Dmg", "Attack", "Atk", "Splash", "Pierce", "pierce", "spikes", "splash"]),
    ("stat_change", ["SetParam", "ChangeParam", "SwapStats", "Bit"]),
    ("skill_control", ["AddSkill", "RemoveSkill", "ChangeWarlordSkill"]),
]

TARGETS = [
    ("all_enemies", ["AllEn", "EnemiesAll", "AllEnemy"]),
    ("random_enemy", ["RndEn", "RandomEn"]),
    ("enemy_warlord", ["EnWL", "EnemyWarlord", "WLEn"]),
    ("warlord", ["WL", "Warlord"]),
    ("all_allies", ["Fr", "Friend", "Ally", "Allies", "AllFr"]),
    ("random_ally", ["RndAlly", "RandomAlly"]),
    ("self", ["Self", "Me"]),
    ("lane", ["Line", "InLine"]),
    ("adjacent", ["Neigh", "Neighbours", "R1", "Near"]),
    ("column", ["Column", "Col"]),
    ("melee", ["Melee", "Mel"]),
    ("ranged", ["Ranged", "Rang"]),
]

ACTION_CN = {
    "instant_kill": ("瞬杀", "直接消灭目标。"),
    "reborn": ("重生", "阵亡时以部分生命复活。"),
    "summon": ("召唤", "召唤援军加入战场。"),
    "transform": ("形态转换", "转换为更强形态。"),
    "steal": ("夺取", "夺取目标的增益或生命。"),
    "silence": ("沉默", "使目标无法施放技能。"),
    "cleanse": ("净化", "清除自身或队友的负面状态。"),
    "counter": ("反击", "受击时反击攻击者。"),
    "shield": ("护盾", "获得吸收伤害的护盾。"),
    "freeze": ("冰冻", "冻结目标，使其短暂无法行动。"),
    "poison": ("剧毒", "使目标持续损失生命。"),
    "heal": ("治疗", "恢复生命。"),
    "buff_atk": ("攻击强化", "提升攻击力。"),
    "debuff_atk": ("攻击削弱", "降低目标攻击力。"),
    "buff_hp": ("生命强化", "提升生命上限或获得免疫。"),
    "damage": ("伤害", "对目标造成伤害。"),
    "stat_change": ("属性调整", "临时改变单位属性。"),
    "skill_control": ("技能操控", "添加或移除技能。"),
}
TRIGGER_CN = {
    "on_appear": "登场", "on_attacked": "受击", "on_death": "阵亡", "on_kill": "击杀",
    "on_heal": "治疗", "on_turn": "回合", "on_attack": "攻击",
}
TARGET_CN = {
    "all_enemies": "敌方全体", "random_enemy": "随机敌人", "enemy_warlord": "敌方指挥官",
    "warlord": "指挥官", "all_allies": "我方全体", "random_ally": "随机队友", "self": "自身",
    "lane": "同列", "adjacent": "相邻", "column": "纵列", "melee": "近战单位", "ranged": "远程单位",
}


def pick(text: str, table):
    for key, words in table:
        for w in words:
            if w in text:
                return key
    return None


def classify(token: str, value: str):
    trig = pick(token, TRIGGERS) or "on_attack"
    act = pick(token, ACTIONS) or "damage"
    tgt = pick(token, TARGETS) or ("self" if "Self" in token else "single_enemy")
    nums = [int(n) for n in re.findall(r"-?\d+", value or "")]
    magnitude = nums[0] if nums else 0
    chance = nums[1] if len(nums) > 1 else 0
    dur = 1
    m = re.search(r"(\d+)Trn", token)
    if m:
        dur = int(m.group(1))
    name_cn, desc_cn = ACTION_CN.get(act, ("技能", "对目标生效。"))
    trigger_cn = TRIGGER_CN.get(trig, "攻击")
    target_cn = TARGET_CN.get(tgt, "单个敌人")
    pct = f"{magnitude}%" if magnitude else ""
    desc = f"{trigger_cn}时，对{target_cn}{desc_cn.replace('。','')}{('，强度 ' + pct) if pct else ''}。"
    known = pick(token, TRIGGERS) is not None and pick(token, ACTIONS) is not None
    return {
        "token": token,
        "trigger": trig,
        "action": act,
        "target": tgt,
        "value": magnitude,
        "chance": chance,
        "duration": dur,
        "name": name_cn,
        "desc": desc,
        "confidence": "recovered" if known else "inferred",
    }


def main() -> int:
    tokens = {}
    for f in glob.glob(str(ROOT / "data" / "configs" / "Monsters_*.json")):
        try:
            d = json.loads(Path(f).read_text())["data"]
        except Exception:
            continue
        n = len(next(iter(d.values())))
        for i in range(n):
            raw = (d.get("skills_id", [""] * n)[i] or "")
            val = (d.get("skill_value", [""] * n)[i] or "")
            for tok in [t.strip() for t in raw.split(",") if t.strip()]:
                tokens.setdefault(tok, classify(tok, val))
    OUT.write_text(json.dumps(tokens, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    from collections import Counter
    print(json.dumps({
        "tokens": len(tokens),
        "recovered": sum(1 for t in tokens.values() if t["confidence"] == "recovered"),
        "inferred": sum(1 for t in tokens.values() if t["confidence"] == "inferred"),
        "top_actions": Counter(t["action"] for t in tokens.values()).most_common(8),
        "top_triggers": Counter(t["trigger"] for t in tokens.values()).most_common(8),
    }, ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
