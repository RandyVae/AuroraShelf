# Lust Goddess Local Edition 完整项目档案

本档案供后续 AI（包括 integrative/antegrative 类执行代理）直接接手。它记录当前事实、入口、边界、数据、架构目标、执行顺序和验收规则。所有旧版本数字必须以本文件的“版本绑定”与实际文件为准。

## A. 项目身份

- 项目目录：`/Users/itou/Documents/project-hgame`
- 当前技术栈：原生 HTML/CSS/JavaScript、Spine 4.2 WebGL runtime、Android Java WebView 外壳。
- 主产品源：`web/`；Android 壳：`android/`；研究支线：`replica/`；历史单机实现：`offline/`。
- 当前交付性质：本地离线重制版，非原 Unity 客户端 1:1。
- 当前可运行入口：
  - Web：`python3 web/serve.py`，访问 `http://127.0.0.1:8100/`
  - Android：`cd android && gradle --offline :app:assembleRelease`
- 旧原型：`android-app/` 是 Astral Vanguard 净室原型，不属于当前交付，不要混入构建。

## B. 权威来源和阅读顺序

1. `MEMORY.md`：跨轮状态、研究结论和已知坑。
2. `HANDOVER.md`：交接入口和历史交付说明。
3. `docs/FINAL_AUDIT_REPORT.md`：本次成品化审计与 P0/P1 路线。
4. `web/README.md`、`docs/local-edition/README.md`：当前 Web/Android 能力和限制。
5. `web/js/`、`web/data/`：当前真正运行的逻辑和数据。
6. `replica/OFFLINE_STATUS.md`、`replica/ORIGINAL_OFFLINE_REPLAY.md`：原版客户端研究支线，不作为重制版实现依据。

遇到文档与运行结果冲突时，以当前源码、当前数据、当前构建和当前测试为准，并在交接文件中记录冲突。

## C. 当前功能快照

### 已有

- 标题、启动、主大厅、战役章节/关卡、剧情播放/跳过/自动播放。
- 三人编队、55 名单位图鉴、单位详情、Spine 立绘动画。
- 招募单抽/十连、SSR 3%/SR 18%/R 79%、30 抽保底、重复转碎片。
- 3 路自动战斗：普攻、技能触发、伤害、治疗、护盾、中毒、冻结、沉默、反击等近似效果；战斗速度和跳过。
- 8 章 41 关、三星结算、金币/钥匙/碎片/行动力奖励。
- 商店、CG 回放、音频、减少动效、18+ 立绘开关、导出/导入/重置存档。
- Android loopback AssetServer、Range 请求、本地视频/音频、SharedPreferences 存档桥。

### 明确没有或未达成

- 没有正式的角色实例成长：等级、经验、升星、技能升级、装备和材料消耗未形成完整闭环。
- 没有每日/周任务、成就、试炼/Boss 长期循环、活动生命周期和排行榜。
- 战斗不是原 Unity 逻辑；数值公式和 token 语义有推断/自定义部分。
- 原 Unity 客户端离线重放卡 `LoadAllStaticContentInfo / LoadedAssets=0`。
- 没有正式签名、上架合规、素材商业授权、完整 API 36/平板/TalkBack/200% 字体证据。

## D. 目录和职责

```text
web/
  index.html             页面骨架和无障碍语义
  css/app.css            主题、响应式布局、动效、触控尺寸
  js/app.js              启动、导航、流程编排（待拆分）
  js/ui.js               DOM 渲染
  js/battle.js           战斗运行时（待改为状态机）
  js/balance.js          当前自定义平衡公式
  js/save.js             localStorage/AndroidSave 存档
  js/spine.js            战斗 Spine 场景
  js/spinestage.js       大厅/详情/招募 Spine 场景
  js/audio.js            音频播放
  data/*.json            生成后的游戏数据
  assets/                入包资源
  vendor/                Spine runtime 和许可证
android/
  app/src/main/java/...  WebView、AssetServer、SaveBridge
  app/src/main/assets/www  Web 资源打包副本
tools/
  build_game_data.py     配置表 -> web/data
  build_game_assets.py   资源 -> web/assets
  extract_all_assets.py  Bundle -> Spine/音频
  e2e_local.py           Headless Chrome 端到端流程
assets/                  原始/解包资源，体积大，不等同于发布包
data/configs/             原版配置研究数据
replica/                  原版 Unity 客户端本地化和代理研究
dist/                     已生成 APK、截图和哈希历史
```

## E. 运行和验证

### Web

```bash
cd /Users/itou/Documents/project-hgame
python3 web/serve.py
```

### Web 自动化

当前脚本依赖 `websockets` 和本机 Chrome/CDP；本轮使用：

```bash
./.venv-assets/bin/python tools/e2e_local.py
```

成功标准至少包括：标题、主大厅、战役、剧情、战斗胜利、三星写入、抽卡、商店、刷新存档、0 JS 异常。脚本当前会把 `data:` SVG 记录成 external，后续应修正为只统计 `http(s)/ws(s)`。

### Android

```bash
cd /Users/itou/Documents/project-hgame/android
gradle --offline clean :app:assembleRelease
```

构建成功不代表设备验收成功。当前可靠的 runtime 证据是 API 34 模拟器；API 36、真机、平板、TalkBack 和正式签名仍需单独验证。

## F. 当前数据契约

### 新存档 v1（现状）

```json
{
  "v": 1,
  "gold": 1200,
  "keys": 8,
  "energy": 60,
  "shards": 0,
  "owned": ["kitty", "regina", "mitsuki"],
  "squad": ["kitty", "regina", "mitsuki"],
  "stars": {},
  "cleared": {},
  "pity": 0,
  "pulls": 0,
  "storySeen": {},
  "settings": {"bgm": 0.55, "sfx": 0.8, "reduceMotion": false, "adult": false},
  "stats": {"battles": 0, "wins": 0, "energyAt": 0}
}
```

这只是当前实现记录，不是最终方案。下一阶段应建立 `SaveSchema v2`：

```text
meta: {schemaVersion, profileId, createdAt, updatedAt}
wallet: {gold, keys, energy, energyAt, shards, materials}
roster: {instances: [{id, heroKey, level, xp, stars, skillLevels, equipmentIds}]}
squads: [{id, name, members, formation, leader}]
progress: {chapters, stages, stars, storySeen, trials}
missions: {daily, weekly, achievements, claimed}
gacha: {bannerId, pityByBanner, pulls, history}
settings: {audio, motion, adult, language}
```

迁移要求：v1→v2 可重复执行；字段白名单；数值范围；未知 heroKey 过滤；坏 JSON 退回最近有效备份；导入文本限制大小和深度；写入采用临时键 + 校验后替换。

## G. 战斗目标契约

当前战斗是自动三路运行时。后续 AI 不应先改视觉，而应先建立以下规则表：

- 单位：side、lane、position、hp、maxHp、atk、speed、range、class、race、rarity、leader。
- 触发：on_appear、on_attack、on_attacked、on_death、on_kill、on_heal、on_turn。
- 效果：damage、heal、shield、buff/debuff、poison、freeze、silence、counter、lifesteal、cleanse、reborn、summon。
- 目标：self、single enemy/ally、lane、adjacent、column、all enemies/all allies、random。
- 结算：触发顺序、冷却、持续时间、死亡后事件、复活、反击递归上限、时间超时。
- 随机：`seed`、RNG 状态、命中/暴击/闪避和掉落结果。
- 输出：BattleResult、BattleEvent[]、奖励原因和 replay JSON。

当前 `web/js/balance.js` 是自定义公式单一来源；任何调整必须同步模拟器、数据快照和战斗测试，不能只改 UI 文案。

## H. 后续 AI 执行协议

1. 先读本档案、`MEMORY.md` 和 `docs/FINAL_AUDIT_REPORT.md`。
2. 执行 `git status`；若仍非 Git 仓库，先不要假设有可回滚提交，建立文件级 manifest。
3. 运行现有 E2E 和 Android build，记录基线；不要在基线失败时把新改动说成回归。
4. 每次只实现一个垂直切片：数据契约 → domain service → UI → 存档 → 测试 → 文档。
5. 不要把测试模式无限资源、全角色解锁、跳过关卡写入正式默认路径；使用显式开发构建开关。
6. 不得把重制版称为原版 1:1；涉及原版研究时引用 `replica/` 的确切证据。
7. 修改资源生成链后，重新生成 `web/data`/`web/assets` 并做缺失引用检查。
8. 修改 Android 代码后，分别报告构建、模拟器、真实设备和发布签名证据。
9. 每个实质任务完成后更新 `MEMORY.md` 当前状态，并运行：

```bash
python3 ~/.agent-memory/sync.py --project /Users/itou/Documents/project-hgame
```

## I. 推荐首批任务清单

### P0-1：基线和 schema

- [ ] 写 `BUILD_MANIFEST.json`，绑定源码/数据/资源/APK 哈希。
- [ ] 写 `web/data/schema/*.schema.json` 或等价校验器。
- [ ] 修 `constants.maxEnergy` 与默认存档冲突。
- [ ] 实现 SaveSchema v2、迁移、校验、备份和损坏恢复。
- [ ] 为经济变化增加 transaction reason。

### P0-2：战斗可验证性

- [ ] 拆 `BattleState`、`BattleEngine`、`BattleRng`、`BattleReplay`。
- [ ] 固定 seed；添加 1000 场模拟和边界单元测试。
- [ ] 写技能/目标/状态规则表；明确自定义规则与推断规则。
- [ ] 加敌方 archetype 和可解释 AI。

### P0-3：成长闭环

- [ ] 角色实例、等级/经验/星级/技能/装备数据模型。
- [ ] 升级、突破、技能升级、装备和材料来源/消耗。
- [ ] 新手引导和第一次成长任务。

### P0-4：发布门槛

- [ ] 正式签名和产物清单。
- [ ] API 36、真机、平板、TalkBack、200% 字体、低内存、后台恢复。
- [ ] 授权、隐私、18+ 年龄验证、商店文案和截图。

## J. 交付边界

- `assets/` 和 `runtime/` 含原始研究资产，不能默认视作可分发素材。
- `vendor/spine-LICENSE.txt` 必须随任何 Spine 运行时交付一起审查；商业分发前需确认 Editor/runtime 许可。
- 当前 APK 为 debug 签名历史产物；没有用户明确要求，不要直接发布或声称可上架。
- 不要删除 `replica/` 研究证据，也不要用它替代单机版产品验收。
- 不要修改或提交任何密钥、Token、密码、会话标识或私人客户资料。

## K. 版本绑定记录

本档案创建于 2026-10-29。当前实际构建验证得到：

- Web E2E：通过，战斗 3★，抽卡/商店/刷新存档通过，0 JS 异常。
- Android Release：`assembleRelease` 通过；重新构建产物位于 `android/app/build/outputs/apk/release/app-release.apk`，哈希以交付时重新计算为准。
- `dist/LustGoddess-2.3.apk`：历史 v2.3 产物，不能代替当前重新构建的哈希。

任何后续 AI 在报告中都应同时写出：修改的文件、运行的命令、测试结果、未验证的设备/发布边界和新的 manifest/hash。

## L. Antigravity 可直接执行的完整工作包

### L.1 接手后的第一轮

```text
1. 读取 MEMORY.md、HANDOVER.md、FINAL_AUDIT_REPORT.md、本文件。
2. 运行 Web E2E 和 Android assembleRelease，保存 baseline.json。
3. 建立 BUILD_MANIFEST.json，绑定当前源文件、data、assets manifest、APK SHA-256。
4. 修正 constants.maxEnergy 与默认存档冲突。
5. 修复 E2E 对 data: URI 的 external 误报。
6. 建立 data/schema、docs/adr、qa 目录。
```

这一轮不扩充角色和关卡，先把基线、数据一致性和回归验证固定下来。

### L.2 第二轮：状态、存档和经济

交付文件建议：

```text
web/js/domain/save-schema.js
web/js/domain/save-migrations.js
web/js/domain/economy.js
web/js/domain/commands.js
web/data/schema/save-v2.schema.json
web/data/economy.json
qa/save_corruption_cases.json
```

必须支持：版本迁移、字段白名单、非法值修复、备份恢复、交易原因、重复领取保护、体力离线时间处理。完成后再接入 UI。

### L.3 第三轮：角色成长

交付文件建议：

```text
web/js/domain/roster.js
web/js/domain/progression.js
web/js/domain/equipment.js
web/data/progression.json
web/data/equipment.json
web/index.html          # 角色详情/养成入口
web/js/ui.js            # 成长、装备、材料展示
```

先用 3 名 starter 做完整垂直切片：升级、技能升级、升星、装备、卸下、分解、存档、战力变化、战斗生效。通过后再批量扩展 55 名单位。

### L.4 第四轮：战斗重构

交付文件建议：

```text
web/js/battle/state.js
web/js/battle/engine.js
web/js/battle/rng.js
web/js/battle/replay.js
web/js/battle/ai.js
web/data/battle-rules.json
qa/battle/*.json
```

约束：引擎不得读 DOM；UI 不得直接扣血/加金币；所有结果由事件产生。至少完成固定 seed、暂停/继续、重放、1000 场模拟、死亡后不行动、反击递归上限、状态清理和 Boss 阶段测试。

### L.5 第五轮：任务和长期内容

交付文件建议：

```text
web/js/domain/missions.js
web/js/domain/activities.js
web/data/missions.json
web/data/trials.json
web/data/activities.json
```

先实现每日任务、章节任务、成就和一个 Boss 试炼。每个任务都要有条件、进度、奖励、领取状态、重复规则和 UI 路径。

### L.6 第六轮：动画专项

交付文件建议：

```text
web/data/animation-manifest.json
web/js/animation/animation-service.js
web/js/animation/vfx-service.js
web/js/animation/camera-fx.js
web/js/animation/audio-cues.js
qa/animation/                  # 每个主要流程录屏/截图
```

先完成 Kitty、Dominica、Florence 三个角色的 idle、enter、attack、skill、hit、victory、fall；再推广到全部角色。动画 marker 必须触发 BattleEvent，不能靠散落的 `setTimeout`。

### L.7 每轮必须交付的报告

Antigravity 每完成一个工作包，都必须输出：

- 修改文件清单。
- 数据格式和迁移说明。
- Web E2E 结果。
- Android 构建和设备结果。
- 截图或录屏路径，动画则必须包含事件同步证据。
- 新增风险和未验证设备。
- 新的 `BUILD_MANIFEST.json` 或哈希。
- `MEMORY.md` 当前状态更新。

## M. Antigravity 任务优先级

```text
P0  基线/manifest、SaveSchema v2、经济校验、战斗状态机、正式构建和发布合规
P1  角色成长、装备、任务、Boss 试炼、完整动画管线、性能和无障碍
P2  更多角色动作、更多活动、剧情分支、社交/排行、额外 CG 和高级特效
```

任何 P1/P2 内容都不得掩盖 P0 缺陷。尤其不能先扩充美术和关卡，再处理存档、经济和战斗确定性。

## N. 完整游戏的 Definition of Done

Antigravity 只有在以下项目全部完成后，才可以报告“成品候选版”：

- 新玩家可完成新手引导、第一次成长、第一次招募、第一次失败和第一次任务领奖。
- 55 名角色的数据模型统一，至少 3 名角色具备完整成长和动画闭环。
- 主线、试炼、任务、商店、招募、养成和存档相互连接。
- 战斗固定 seed 可重放，技能、状态、AI 和奖励有自动化测试。
- 每个主要行为都有动画、音效、文字或镜头反馈，并支持减少动效。
- Web/Android 在目标设备矩阵通过，正式 APK 使用 release 签名。
- 资源、Spine、音频、视频、成人内容具备可发布授权或已替换为原创资源。
- 报告中清楚写明当前是独立重制版，不能把推断逻辑写成原 Unity 逻辑。
