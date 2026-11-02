# 戰鬥邏輯回收報告（2026-10-29）

## 一、結論

原版戰鬥邏輯位於 Unity WebGL 的 IL2CPP wasm 內，**沒有 name 段**：347,688 個函數、250,117 個 C# 方法定義，
兩者之間沒有現成的符號映射。要達到「逐位相同的原版公式」，必須先建立
`C# 方法 → wasm 函數` 的映射；本輪已排除兩個錯誤假設，並確定可行路徑。

目前已落地的**原版來源**：

| 項目 | 狀態 | 來源／證據 |
|---|---|---|
| 戰鬥常量 | **已回收** | `Constants` 配置表：`battle_timer_time=60`、`noob_battle_timer_time=120`、`skills_delivery_cooldown=4`、`skill_ai_decision_delay=0.1`、`hp_power_coef=1`、`attack_power_coef=2`、`archers_enemy_move_speed=80`、`archers_arrow_move_speed=950`、`min_iq_for_use_skill=0.7`、四組 `warlord_*_slill_coeffs` |
| 技能詞彙表 | **已回收** | metadata 類型表：`BattlefieldScripts.Skills.Implementation.Actions` 共 43 個動作類（AttackAction、BleedingAction、CounterAttackAction、InstantKillAction、RebornAction、StealAction、SwapStatsAction、TransformAction、VampiricAction…）、30+ 觸發器、50+ 目標過濾器、4 種計時器 |
| 技能 token 文法 | **已回收（部分）** | 2,997 個技能 token 以「觸發＋動作＋目標」命名（例 `AppSelfBuffATKInLine`、`AtkdFreezeEn`、`DeathSelfDmgFrNeighbours`）；本輪解析出 1,739 個可完整對上詞彙表，其餘 1,258 個標記 inferred |
| 傷害公式 | **未回收（推斷）** | 需要 `FieldMonster` 的攻擊方法本體；本輪未建立方法映射 |
| 技能數值 | **部分** | `skill_value` 為逗號分隔的等級數值（例 spikes `4,5,6,8,10`），單位語義未證實，暫按百分比使用 |

## 二、本輪嘗試與否證

1. **假設：wasm data 段內有 `Il2CppCodeGenModule.methodPointers` 陣列（值＝函數索引）**
   - 掃描 73,255 個 data 段，找到唯一一段 306,612 個「全部 < 348,000」的整數序列（位址 `0xee70b4`）。
   - 否證：該序列最大值 575,265，超出函數總數（347,688 + 790 imports）；且用它查
     `PnkClient.StaticContentHandler` 的 `SendQueuedRequests` 協程，映射到的函數只有 21–31 bytes 的間接呼叫 thunk，
     不含該方法使用的字串字面量（`HasLoadedAssets` literal 13813）。**此表不是方法指標表。**

2. **假設：`elem` 段可直接提供 table index → function index**
   - `elem` 段確認為 698,297 個項目（offset 1），可正常解析；但缺少 table index 來源，無法單獨完成映射。

3. **可行路徑（已驗證前置條件）**
   - 原客戶端可從暖機 profile 啟動（`https://www.lust-goddess.com/play` 回 200，Profile WLNHHF、v23.0.1、PLAY 畫面正常）。
   - 遊戲 canvas 位於主頁面 `#unity-canvas`；`window.createUnityInstance` 存在，可用
     `Page.addScriptToEvaluateOnNewDocument` 包裹後取得 `unityInstance.Module.HEAPU8`。
   - 下一步：在執行期 dump wasm heap，於記憶體中定位 `Il2CppCodeRegistration` / `Il2CppCodeGenModule`
     （以 `"Assembly-CSharp.dll"` 字串反查 struct），讀出真正的 `methodPointers`（wasm table index），
     再以 `elem[tableIndex]` 取得函數索引，最後用 `tools/wasm_slice.py` 切出單一函數反編譯。

## 三、本輪已落地的戰鬥升級（v2.1）

- `tools/re_skill_grammar.py`：以原版類型詞彙表分類 2,997 個技能 token，輸出
  [`web/data/skills.json`](../web/data/skills.json)（trigger / action / target / value / confidence）。
- `tools/build_game_data.py`：把技能規格掛到每名單位；新增
  [`web/data/battle.json`](../web/data/battle.json)（原版戰鬥常量）。
- `web/js/battle.js`：
  - 觸發式技能系統：`on_appear` / `on_attack` / `on_attacked` / `on_death` / `on_kill` / `on_heal` / `on_turn`；
  - 目標範圍：自身、我方全體、隨機隊友、敵方全體、隨機敵人、同列、相鄰、縱列、近戰／遠程；
  - 動作：傷害、治療、護盾、攻擊強化／削弱、凍結、沉默、中毒、吸血、反擊、淨化、重生、瞬殺、轉換、奪取、召喚、屬性調整；
  - 技能投放冷卻 = 原版 `skills_delivery_cooldown`（4 秒）；戰鬥時限 = 原版 `battle_timer_time`（60 秒），
    新手期（關卡等級 ≤10）使用 `noob_battle_timer_time`（120 秒）。
- 驗證：`tools/e2e_local.py` 全流程通過，戰鬥 2★ 勝利、獎勵入庫、0 JS 異常、0 外部請求。

## 四、仍非 1:1 的部分（明確標注）

1. 技能數值語義（`skill_value` 是百分比、點數還是等級係數）未經反編譯證實，現按百分比套用。
2. 傷害結算（是否含防禦、暴擊、線路係數、Warlord 加成）未回收。
3. 單位上限生命目前使用 `hp × 8`（可玩性調整），非原版 `hp_power_coef=1`。
4. 敵方 AI、技能選擇機率、`min_iq_for_use_skill` 的實際判定未回收。

以上四項都需要第三節的記憶體 dump + 方法映射，才能逐項換成原版公式。
