import json

def main():
    # 1. Update videos.json
    videos_path = 'app/src/main/assets/www/data/videos.json'
    with open(videos_path, 'r', encoding='utf-8') as f:
        videos = json.load(f)
    
    rea_videos = [
        'assets/videos/cg_rea_general.mp4',
        'assets/videos/cg_rea_event049.mp4'
    ]
    for v in rea_videos:
        if v not in videos:
            videos.append(v)
            
    with open(videos_path, 'w', encoding='utf-8') as f:
        json.dump(videos, f, ensure_ascii=False, indent=2)
    print(f"Updated {videos_path}: now has {len(videos)} videos.")

    # 2. Create cgs.json
    cgs_path = 'app/src/main/assets/www/data/cgs.json'
    cgs_data = [
        { "id": "cg_3rd_anni", "title": "諸神降臨 · 三週年盛典", "char": "全體特勤", "src": "assets/cg/cg_3rd_anni.webp", "thumb": "assets/cg/cg_3rd_anni_thumb.webp" },
        { "id": "cg_3half_anni", "title": "愛欲狂潮 · 3.5週年慶", "char": "全體特勤", "src": "assets/cg/cg_3half_anni.webp", "thumb": "assets/cg/cg_3half_anni_thumb.webp" },
        { "id": "cg_kart_police", "title": "制服特警 · 戒嚴執勤", "char": "Cartilla 卡蒂拉", "src": "assets/cg/cg_kart_police.webp", "thumb": "assets/cg/cg_kart_police_thumb.webp" },
        { "id": "cg_kart_jeans", "title": "街頭便裝 · 假日巡警", "char": "Cartilla 卡蒂拉", "src": "assets/cg/cg_kart_jeans.webp", "thumb": "assets/cg/cg_kart_jeans_thumb.webp" },
        { "id": "cg_ines_teacher", "title": "學院教師 · 嚴格受導", "char": "Ines 伊涅絲", "src": "assets/cg/cg_ines_teacher.webp", "thumb": "assets/cg/cg_ines_teacher_thumb.webp" },
        { "id": "cg_luf_yoga", "title": "熱力瑜伽 · 靈動姿態", "char": "Luf 露菲", "src": "assets/cg/cg_luf_yoga.webp", "thumb": "assets/cg/cg_luf_yoga_thumb.webp" },
        { "id": "cg_luf_sweater", "title": "居家毛衣 · 晨光微曦", "char": "Luf 露菲", "src": "assets/cg/cg_luf_sweater.webp", "thumb": "assets/cg/cg_luf_sweater_thumb.webp" },
        { "id": "cg_misa_prisoner", "title": "重犯禁錮 · 破枷死鬥", "char": "Misaki 美咲", "src": "assets/cg/cg_misa_prisoner.webp", "thumb": "assets/cg/cg_misa_prisoner_thumb.webp" },
        { "id": "cg_erin_succubus", "title": "夜色魅魔 · 緋紅迷夢", "char": "Erin 艾琳", "src": "assets/cg/cg_erin_succubus.webp", "thumb": "assets/cg/cg_erin_succubus_thumb.webp" },
        { "id": "cg_fen_nurse", "title": "白衣天使 · 特殊診療", "char": "Fen 芬妮", "src": "assets/cg/cg_fen_nurse.webp", "thumb": "assets/cg/cg_fen_nurse_thumb.webp" },
        { "id": "cg_miri_nurse", "title": "急救護理 · 貼身救護", "char": "Miri 米莉", "src": "assets/cg/cg_miri_nurse.webp", "thumb": "assets/cg/cg_miri_nurse_thumb.webp" },
        { "id": "cg_sera_sailor", "title": "水手蔚藍 · 海風微瀾", "char": "Sera 塞拉", "src": "assets/cg/cg_sera_sailor.webp", "thumb": "assets/cg/cg_sera_sailor_thumb.webp" },
        { "id": "cg_lynn_dancer", "title": "暗夜舞姬 · 幽魅旋律", "char": "Lynn 琳恩", "src": "assets/cg/cg_lynn_dancer.webp", "thumb": "assets/cg/cg_lynn_dancer_thumb.webp" },
        { "id": "cg_lynn_stewardess", "title": "雲端空乘 · 專屬座艙", "char": "Lynn 琳恩", "src": "assets/cg/cg_lynn_stewardess.webp", "thumb": "assets/cg/cg_lynn_stewardess_thumb.webp" },
        { "id": "cg_rana_newyear", "title": "新春盛裝 · 金絲錦緞", "char": "Rana 蕾娜", "src": "assets/cg/cg_rana_newyear.webp", "thumb": "assets/cg/cg_rana_newyear_thumb.webp" },
        { "id": "cg_rana_cow", "title": "純真萌態 · 萌趣玩伴", "char": "Rana 蕾娜", "src": "assets/cg/cg_rana_cow.webp", "thumb": "assets/cg/cg_rana_cow_thumb.webp" }
    ]
    with open(cgs_path, 'w', encoding='utf-8') as f:
        json.dump(cgs_data, f, ensure_ascii=False, indent=2)
    print(f"Created {cgs_path}: {len(cgs_data)} CGs cataloged.")

    # 3. Update chapters.json (Chapter 9 & 10 rewards and upgraded bgWeb)
    chapters_path = 'app/src/main/assets/www/data/chapters.json'
    with open(chapters_path, 'r', encoding='utf-8') as f:
        chapters = json.load(f)
    
    for ch in chapters:
        if ch['id'] == 9:
            ch['bgWeb'] = 'assets/bg/rea_temple_b001.webp'
            ch['rewardCg'] = 'assets/videos/cg_rea_general.mp4'
        elif ch['id'] == 10:
            ch['bgWeb'] = 'assets/bg/rea_throne_b002.webp'
            ch['rewardCg'] = 'assets/videos/cg_rea_event049.mp4'

    with open(chapters_path, 'w', encoding='utf-8') as f:
        json.dump(chapters, f, ensure_ascii=False, indent=2)
    print(f"Updated {chapters_path}: chapters 9 & 10 configured with REA backgrounds and reward CGs.")

    # 4. Update heroes.json
    heroes_path = 'app/src/main/assets/www/data/heroes.json'
    with open(heroes_path, 'r', encoding='utf-8') as f:
        heroes = json.load(f)

    existing_ids = set(h['id'] for h in heroes)
    existing_keys = set(h['key'] for h in heroes)

    new_heroes = [
        {
            "id": "1124",
            "key": "kart",
            "name": "Cartilla",
            "rarity": "legendary",
            "rarityCn": "SSR",
            "class": "ranged",
            "classCn": "远程",
            "race": "order",
            "raceCn": "秩序",
            "atk": 605,
            "hp": 132,
            "battleHp": 1056,
            "skills": [
                {
                    "token": "basic",
                    "name": "执法重击",
                    "desc": "以高动能特勤制式手枪精准压制目标。",
                    "effect": { "type": "damage", "mult": 1.15 }
                },
                {
                    "token": "Cartilla_LawEnforce",
                    "name": "戒严封锁",
                    "desc": "登场时，启动电磁压制力场，降低全体敌方攻击速度与命中率。",
                    "effect": { "type": "debuff", "mult": 0.8 },
                    "trigger": "on_appear",
                    "action": "debuff",
                    "target": "all_enemies",
                    "value": 20,
                    "chance": 100,
                    "duration": 2
                },
                {
                    "token": "Cartilla_BulletStorm",
                    "name": "正义弹雨",
                    "desc": "攻击时，倾泻全弹幕覆盖敌方前排，造成毁灭性穿透伤害。",
                    "effect": { "type": "damage", "mult": 1.55 },
                    "trigger": "on_attack",
                    "action": "damage",
                    "target": "front_row",
                    "value": 185,
                    "chance": 45,
                    "duration": 1
                }
            ],
            "art": { "base": "events_monsters/kart_big" },
            "unit": "casey_player",
            "voice": [],
            "power": 1960,
            "artWeb": {
                "base": "assets/heroes/kart/base.webp",
                "baseThumb": "assets/heroes/kart/base_thumb.webp",
                "lega": "assets/heroes/kart/lega.webp",
                "legaThumb": "assets/heroes/kart/lega_thumb.webp"
            },
            "unitWeb": "assets/units/casey_player/casey_player.atlas"
        },
        {
            "id": "1125",
            "key": "ines",
            "name": "Ines",
            "rarity": "legendary",
            "rarityCn": "SSR",
            "class": "ranged",
            "classCn": "远程",
            "race": "nature",
            "raceCn": "自然",
            "atk": 620,
            "hp": 126,
            "battleHp": 1008,
            "skills": [
                {
                    "token": "basic",
                    "name": "奥术教典",
                    "desc": "咏唱远古符文对敌人施加奥术轰击。",
                    "effect": { "type": "damage", "mult": 1.16 }
                },
                {
                    "token": "Ines_StrictGuidance",
                    "name": "严谨教条",
                    "desc": "登场时，全员施加精力充盈效果，提升暴击伤害与穿透力。",
                    "effect": { "type": "buff", "mult": 1.25 },
                    "trigger": "on_appear",
                    "action": "buff",
                    "target": "all_allies",
                    "value": 25,
                    "chance": 100,
                    "duration": 2
                },
                {
                    "token": "Ines_ArcaneNova",
                    "name": "天罚星芒",
                    "desc": "攻击时，引动星辉法阵对全体敌人造成高额元素伤害并驱散增益。",
                    "effect": { "type": "damage", "mult": 1.5 },
                    "trigger": "on_attack",
                    "action": "damage",
                    "target": "all_enemies",
                    "value": 170,
                    "chance": 40,
                    "duration": 1
                }
            ],
            "art": { "base": "events_monsters/ines_big" },
            "unit": "morana_player",
            "voice": [],
            "power": 1950,
            "artWeb": {
                "base": "assets/heroes/ines/base.webp",
                "baseThumb": "assets/heroes/ines/base_thumb.webp",
                "lega": "assets/heroes/ines/lega.webp",
                "legaThumb": "assets/heroes/ines/lega_thumb.webp"
            },
            "unitWeb": "assets/units/morana_player/morana_player.atlas"
        },
        {
            "id": "1126",
            "key": "luf",
            "name": "Luf",
            "rarity": "legendary",
            "rarityCn": "SSR",
            "class": "melee",
            "classCn": "近战",
            "race": "nature",
            "raceCn": "自然",
            "atk": 610,
            "hp": 138,
            "battleHp": 1104,
            "skills": [
                {
                    "token": "basic",
                    "name": "流光连击",
                    "desc": "以轻盈迅猛的近身格斗术连续打击目标。",
                    "effect": { "type": "damage", "mult": 1.15 }
                },
                {
                    "token": "Luf_VitalityBoost",
                    "name": "活力充盈",
                    "desc": "登场时，激发全身潜能，大幅提高生命上限与受疗效果。",
                    "effect": { "type": "buff", "mult": 1.3 },
                    "trigger": "on_appear",
                    "action": "buff",
                    "target": "self",
                    "value": 30,
                    "chance": 100,
                    "duration": 2
                },
                {
                    "token": "Luf_AcrobaticSmash",
                    "name": "飞影破岳",
                    "desc": "攻击时，飞身跃起以雷霆万钧之势踏碎敌阵，造成范围物理震荡与晕眩。",
                    "effect": { "type": "damage", "mult": 1.6 },
                    "trigger": "on_attack",
                    "action": "damage",
                    "target": "front_row",
                    "value": 190,
                    "chance": 42,
                    "duration": 1
                }
            ],
            "art": { "base": "events_monsters/luf_big" },
            "unit": "bea_player",
            "voice": [],
            "power": 1990,
            "artWeb": {
                "base": "assets/heroes/luf/base.webp",
                "baseThumb": "assets/heroes/luf/base_thumb.webp",
                "lega": "assets/heroes/luf/lega.webp",
                "legaThumb": "assets/heroes/luf/lega_thumb.webp"
            },
            "unitWeb": "assets/units/bea_player/bea_player.atlas"
        },
        {
            "id": "1127",
            "key": "misa",
            "name": "Misaki",
            "rarity": "legendary",
            "rarityCn": "SSR",
            "class": "melee",
            "classCn": "近战",
            "race": "chaos",
            "raceCn": "混沌",
            "atk": 630,
            "hp": 125,
            "battleHp": 1000,
            "skills": [
                {
                    "token": "basic",
                    "name": "死斗瞬斩",
                    "desc": "以锁链双刃撕裂近身防线。",
                    "effect": { "type": "damage", "mult": 1.2 }
                },
                {
                    "token": "Misaki_PrisonerDesire",
                    "name": "囚徒枷锁",
                    "desc": "登场时，斩裂束缚爆发狂暴意志，暴击伤害提升40%。",
                    "effect": { "type": "buff", "mult": 1.4 },
                    "trigger": "on_appear",
                    "action": "buff",
                    "target": "self",
                    "value": 40,
                    "chance": 100,
                    "duration": 2
                },
                {
                    "token": "Misaki_CrimsonCarnage",
                    "name": "猩红裁决",
                    "desc": "攻击时，化身血色旋风切入敌方后排，对核心目标造成致命一击。",
                    "effect": { "type": "damage", "mult": 1.7 },
                    "trigger": "on_attack",
                    "action": "damage",
                    "target": "back_row",
                    "value": 210,
                    "chance": 45,
                    "duration": 1
                }
            ],
            "art": { "base": "events_monsters/misa_big" },
            "unit": "orin_player",
            "voice": [],
            "power": 2020,
            "artWeb": {
                "base": "assets/heroes/misa/base.webp",
                "baseThumb": "assets/heroes/misa/base_thumb.webp",
                "lega": "assets/heroes/misa/lega.webp",
                "legaThumb": "assets/heroes/misa/lega_thumb.webp"
            },
            "unitWeb": "assets/units/orin_player/orin_player.atlas"
        }
    ]

    for nh in new_heroes:
        if nh['id'] not in existing_ids and nh['key'] not in existing_keys:
            heroes.append(nh)

    with open(heroes_path, 'w', encoding='utf-8') as f:
        json.dump(heroes, f, ensure_ascii=False, indent=2)
    print(f"Updated {heroes_path}: now has {len(heroes)} heroes.")

    # 5. Update gacha.json
    gacha_path = 'app/src/main/assets/www/data/gacha.json'
    with open(gacha_path, 'r', encoding='utf-8') as f:
        gacha = json.load(f)

    for k in ['kart', 'ines', 'luf', 'misa']:
        if k not in gacha['pools']['SSR']:
            gacha['pools']['SSR'].append(k)

    with open(gacha_path, 'w', encoding='utf-8') as f:
        json.dump(gacha, f, ensure_ascii=False, indent=2)
    print(f"Updated {gacha_path}: SSR pool now has {len(gacha['pools']['SSR'])} heroes.")

if __name__ == '__main__':
    main()
