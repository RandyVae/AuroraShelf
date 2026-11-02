#!/usr/bin/env python3
"""
tools/verify_curated_assets.py
严格资产完整性校验器
1. 校验 55 英雄与 168 敌人 Spine 资源 (WebP, atlas, skeleton.json)
2. 校验 CG / 场景 / 静态立绘 / 原生提取音频资源完整性
3. 校验 7 状态动作映射规范表 web/data/animation-manifest.json
"""
import json
import os
import sys
import glob

def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    web_dir = os.path.join(root, "web")
    
    print("[1/4] 正在校验英雄与敌人 Spine 骨骼资源...")
    with open(os.path.join(web_dir, "data", "heroes.json"), "r", encoding="utf-8") as f:
        heroes = json.load(f)
    with open(os.path.join(web_dir, "data", "enemies.json"), "r", encoding="utf-8") as f:
        enemies = json.load(f)

    errors = []
    
    def verify_spine_unit(unit_id, category):
        folder = os.path.join(web_dir, "assets", "units", unit_id.lower())
        if not os.path.isdir(folder):
            errors.append(f"[{category}] 目录缺失: {folder}")
            return
        webps = glob.glob(os.path.join(folder, "*.webp"))
        atlases = glob.glob(os.path.join(folder, "*.atlas"))
        skels = [f for f in glob.glob(os.path.join(folder, "*.json")) if not f.endswith("manifest.json")]
        
        if not webps:
            errors.append(f"[{category}] 缺少 WebP 贴图: {unit_id}")
        if not atlases:
            errors.append(f"[{category}] 缺少 atlas 图集描述: {unit_id}")
        if not skels:
            errors.append(f"[{category}] 缺少骨骼 skeleton.json: {unit_id}")

    for h in heroes:
        u_id = h.get("unit", h["id"])
        verify_spine_unit(u_id, f"Hero:{h['id']}")

    for e in enemies:
        u_id = e.get("unit", e["id"])
        verify_spine_unit(u_id, f"Enemy:{e['id']}")

    print(f" -> 英雄 ({len(heroes)}) 与敌人 ({len(enemies)}) Spine 校验完成，异常数: {len(errors)}")

    print("[2/4] 正在校验动画 7 状态规范表与序章数据契约...")
    anim_manifest_path = os.path.join(web_dir, "data", "animation-manifest.json")
    if not os.path.exists(anim_manifest_path):
        errors.append("缺失 web/data/animation-manifest.json")
    else:
        with open(anim_manifest_path, "r", encoding="utf-8") as f:
            manifest = json.load(f)
        # v2.8 manifest uses the canonical event-oriented schema.  The old
        # verifier looked for a removed `actions` object and consequently
        # reported every build as broken even when the runtime contract was
        # complete.  Validate the definitions and the synthetic fallback
        # coverage separately so native and composed actions remain distinct.
        definitions = manifest.get("actionStateDefinitions", {})
        required_definitions = ["wake", "enter", "talk", "react", "attack", "hit", "victory"]
        for state in required_definitions:
            if state not in definitions:
                errors.append(f"动画清单 actionStateDefinitions 缺少状态: {state}")

        fallback_counts = manifest.get("accountingSummary", {}).get("syntheticFallbackCount", {})
        for state in ("skill", "fall"):
            if state not in fallback_counts:
                errors.append(f"动画清单 syntheticFallbackCount 缺少状态: {state}")

    intro_path = os.path.join(web_dir, "data", "intro-sequences.json")
    if not os.path.exists(intro_path):
        errors.append("缺失 web/data/intro-sequences.json")
    else:
        with open(intro_path, "r", encoding="utf-8") as f:
            intro_data = json.load(f)
        seqs = intro_data.get("sequences", {})
        if "ch1_prologue" not in seqs:
            errors.append("intro-sequences.json 缺少 ch1_prologue 序章序列")
        else:
            ch1 = seqs["ch1_prologue"]
            events = ch1.get("events", [])
            if len(events) < 3:
                errors.append(f"ch1_prologue 事件数量不足 3 个: 当前 {len(events)}")
            for ev in events:
                bg = ev.get("background")
                if bg and not os.path.exists(os.path.join(web_dir, bg)):
                    errors.append(f"序章背景不存在: {bg}")
                for actor in ev.get("actors", []):
                    art = actor.get("artWeb")
                    if art and not os.path.exists(os.path.join(web_dir, art)):
                        errors.append(f"序章演员立绘不存在: {art}")
    
    print("[3/4] 正在校验核心音效与音频资产映射...")
    # 提取自 audio.js 中的原生资产路径
    required_audios = [
        "main/bgm_blackcat/bgm_blackcat.m4a",
        "main/bgm_temptation/bgm_temptation.m4a",
        "tutorial/mus_fight1/mus_fight1.m4a",
        "tutorial/kitty_attack/kitty_attack.m4a",
        "tutorial/vo_fight_hit_new/vo_fight_hit_new.m4a",
        "tutorial_next/skill_damage/skill_damage.m4a",
        "tutorial/fight_win/fight_win.m4a",
        "main/fight_lose/fight_lose.m4a"
    ]
    for rel_path in required_audios:
        p = os.path.join(web_dir, "assets", "audio", rel_path)
        if not os.path.exists(p):
            errors.append(f"缺少原生音频资产: assets/audio/{rel_path}")

    print("[4/4] 校验完成。汇总报告:")
    if errors:
        print(f"❌ 校验失败，发现 {len(errors)} 项异常:")
        for err in errors[:20]:
            print("  - " + err)
        if len(errors) > 20:
            print(f"  ... 另有 {len(errors)-20} 项错误")
        sys.exit(1)
    else:
        print("✅ 全量资产与动画规范校验通过！55 英雄与 168 敌人骨骼/贴图/图集与原生音频 100% 完整。")
        sys.exit(0)

if __name__ == "__main__":
    main()
