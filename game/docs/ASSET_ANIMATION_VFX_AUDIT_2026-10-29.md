# 美术、动画与特效资源专项审计

审计日期：2026-10-29  
审计对象：`web/` 本地复刻版 v2.5 与 Android WebView 打包资源  
目的：判断当前美术、动画、特效是否达到可维护的游戏开发标准，并为 Antigravity 提供下一轮执行与验收依据。

## 结论

**判定：可玩 Vertical Slice；美术资源管线基础合格，动画覆盖、战斗特效和发布合规未达到成品候选标准。**

角色立绘、场景背景、Spine 纹理页、音频和影片均有可用资产；Curated Web 资源可解析，已运行的 Web E2E 与 Android 构建通过。主要短板不在资源总量，而在资源到玩法的闭环：技能缺少专用动作与效果反馈，4 套单位骨骼没有可用图集，减少动效设置没有覆盖战斗 Spine；目前也没有足以支持商业分发的素材授权证据。

## 审计范围与证据

- 项目记忆与交接：`MEMORY.md`、`HANDOVER.md`、`docs/PROJECT_DOSSIER.md`、`ASSETS_REPORT.md`。
- 资源管线与运行时：`tools/build_game_assets.py`、`web/assets/`、`web/js/spine.js`、`web/js/spinestage.js`、`web/js/battle.js`、`web/js/audio.js`、`web/css/app.css`。
- Web：执行 `./.venv-assets/bin/python tools/e2e_local.py`，流程完成；148 次请求、无外部 HTTP 请求、无 JS exception，完成第 1 章战斗三星、扫荡、升级、装备、招募、商店和存档重载。15 个 `ERR_ABORTED` 请求来自流程中切屏/停止加载，未观察到资源 404。
- 战斗确定性：`node tools/test_battle_determinism.js` 通过，固定种子事件流一致，1,000 场模拟无 NaN 或负生命值。此测试不覆盖动画同步或视觉效果。
- 资源校验：`./.venv-assets/bin/python tools/verify_assets.py` 通过；7,445 个源 Bundle 校验无错误，17,363 张导出图片可解码，3,282 张构建内置图中 64 项缺少所需 StreamingInfo，已记为已知缺口。
- Curated Web 输出独立检查：555 张 WebP 全部可解码，均为 RGBA；219 个 atlas 引用的 219 张 WebP 页均存在。223 个 skeleton JSON 可解析；其中只有 219 个有 atlas。
- 音视频：169 段 M4A 均由 `ffprobe` 识别为 AAC；抽样音频为 44.1 kHz、双声道。4 段影片均为 H.264、1920×1080、30 fps，时长约 6–10 秒。未做全量试听、响度/削波检查或不同 Android 设备解码检查。
- Android：`gradle --offline clean :app:assembleRelease` 与后续 `gradle --offline :app:assembleRelease` 均通过。API 34 模拟器在线，但本轮没有安装或操作 APK，因此**设备流程未验证**。当前构建输出 SHA-256 为 `cfc796fdab9e15088889f141718f8015bd01798c4ada89c5ba754e9a45a6d937`；留存 `dist/LustGoddess-2.5.apk` 为 `66a7a63d18ae851ef84d6da3625de79ffa561366f50851907ad024fe7dc23ff9`。两者大小相同但字节不一致，构建尚未证明可复现。
- 视觉抽查：检查 Kitty/Dominica 立绘、战斗背景及 E2E 大厅/战斗截图。资源主体清晰、透明边缘正常；战斗画面单位与 HUD 可辨，但战场当前主要靠背景、网格线和单位站位构成，截图没有显示技能专属特效。

## 发现

### P0：商业发布的素材权利未获证明

本地包使用从原作 AssetBundle 解出的角色、Spine、音频、影片和成人内容。`ASSETS_REPORT.md` 明确写明权利属于 POCHEMU LTD，素材只供本机研究/个人使用；项目未提供覆盖当前分发用途的授权清单。Spine runtime 的许可证文件也不能代替原作资源授权或 Spine Editor 商业许可证明。

**处理要求：** 商业/公开分发前补齐逐类授权与成人内容发布依据；无法证明的资源替换为原创/有明确许可的资产。取得证据之前，发布状态不得标记为商业成品候选。

### P1：4 套单位 Spine 资源链断裂，Charlotte 可进入队伍但无法构建

`web/assets/units/` 中 `charlotte_player`、`charlotte_enemy`、`chesterbox_enemy`、`cryochamber_enemy` 有 skeleton JSON，但没有 atlas 文件。数据引用检查确认：55 名英雄中 Charlotte 仍列在 `heroes.json`；168 名敌人中另有 3 个单位引用缺失。`Battle.preload()` 会排队加载图集，`SpineField.build()` 随后直接 `require()` atlas 并创建 Skeleton；未选中的初始队伍不触发该缺口，因此现有 E2E 不会覆盖它。

**处理要求：** 从有效来源补齐并核验 atlas 页，或把缺资源单位从可选队伍/战斗阵容和关卡数据中一致地禁用；增加覆盖每个可选角色和关卡编队的引用完整性检查。

### P1：动作资源不能支撑报告定义的角色动作闭环

223 套可解析骨骼的动画统计如下：`idle` 223/223，`damage` 223/223，`hit` 96/223，`rangehit` 122/223；其余主要是少量 `pick_up`、`step` 与个别变体。没有名为 `attack`、`skill`、`victory`、`fall` 或 `enter` 的动画。`battle.js` 只在普通攻击时尝试 `hit`/`rangehit`，受击时尝试 `hit`/`damage`；`castSpec()` 在技能触发时把施法者设回 `idle`。`SpineField.setAnim()` 找不到动画时静默返回 false。

这意味着技能/攻击目前由数值、日志、音效和少量位移表示，没有按技能类型切换动作；缺少动作时也没有诊断记录。项目档案里的 Kitty、Dominica、Florence 完整七动作要求尚未实现。

**处理要求：** 建立实际动作 manifest，按角色列出已验证的 Spine 动画和回退方案；先为 3 个代表角色完成 idle/enter/attack/skill/hit/victory/fall 闭环，并把动画触发与 BattleEvent 对齐。现有提取资源若不含动作，应明确列为资源缺口，不能把 idle/hit 组合描述为完整技能动画。

### P1：特效服务与战斗事件表现未形成

未发现独立 VFX 服务、效果 manifest 或粒子/序列帧特效目录。`Battle.projectiles` 已声明但没有生产/更新/绘制逻辑；普通攻击会产生 0.16 秒 `lunge` 状态，但审阅到的更新循环没有把它应用到位置；伤害反馈主要是 800 ms 浮动数字、HP 条变化和 Spine hit/damage。技能触发只写日志、回到 idle，并按效果改数值。没有技能命中特效、投射物、护盾/治疗可视持续状态、战斗胜利/倒下演出或事件同步证据。

**处理要求：** 增加小范围的 VFX 事件层，覆盖攻击命中、暴击、治疗、护盾、控制、持续伤害、召唤、死亡与胜利；为效果提供清理、低性能降级和事件时间戳测试。先完成首发 3 名角色与 1 个 Boss，之后再扩展。

### P1：减少动效设置没有应用到战斗骨骼

`app.js` 的 `initStages()` 在设置开启时不创建大厅/详情/招募舞台；设置变更只更新状态与 CSS class。战斗仍创建 `SpineField` 并持续 `update()` / `draw()`，没有读取 `reduceMotion`。因此设置名为减少动效，但实际战斗角色动画不受控制。

**处理要求：** 让减少动效贯穿所有 Spine 舞台、战斗动作、镜头/粒子和 CSS 动画；以相同流程的状态切换截图或帧更新计数证明开启后采用静态/简化表现，并保留战斗状态可读性。

### P1：Release 变体仍使用 Debug 签名

`android/app/build.gradle` 在 `release` build type 配置 `signingConfig signingConfigs.debug`。这适合本地侧载和构建验证，但不满足正式上架的签名、密钥保管与升级连续性要求。

**处理要求：** 正式发布前使用受控的 release keystore 签名，核验 application id、versionCode、签名证书指纹和升级安装；不得把 keystore 或密码写入仓库或审计产物。

### P2：Curated 资源缺少可审查的版本清单与 QA 记录

原始 Bundle、原始解包图有 manifest 与校验结果，但运行时 `web/assets/` 没有逐项清单来绑定源资源、输出文件、尺寸、格式、SHA-256、显示用途和许可状态。`BUILD_MANIFEST.json` 也没有绑定 Web 资源树哈希；APK 文档仍自称 v2.4 并列出旧文件/hash。E2E 能证明页面加载与流程完成，不能代替立绘裁切/Spine attachment、音频试听、特效帧检查和 Android 设备视觉验收。

**处理要求：** 生成运行时资源 manifest（含源 Bundle/对象、输出路径、哈希、尺寸、角色/动作、显示尺寸、授权状态），构建时校验所有引用；更新 APK 文档；把桌面/Android 的代表动作录屏、减少动效对照和资源失败日志归档到 `qa/animation/`。

## 资产标准逐项判定

| 领域 | 判定 | 依据 |
|---|---|---|
| 角色/场景图像 | 技术质量通过，发布许可未通过 | WebP/RGBA 可解码；抽样画面清楚；来源为原作资产且没有发布授权证据 |
| Spine 数据与 atlas | 大部分可用，完整性不通过 | 223 skeleton 可解析，219 atlas 页齐全；4 个 skeleton 无 atlas |
| 动画覆盖 | 不通过成品标准 | 只有 idle/damage 和部分 hit/rangehit；缺动作 manifest、技能动作及完整角色动作矩阵 |
| 战斗 VFX | 不通过成品标准 | 无 VFX 服务/资源 manifest；大部分效果是文本、HP 条和基础位移 |
| 音频/影片 | 文件和编码抽查通过，体验验收未完成 | AAC 与 H.264/1080p 可识别；未全量试听、响度检查或设备验收 |
| 资源打包/可复现 | 部分通过 | Web/Android 树的抽样源码哈希相同、资源计数一致；APK 重建 hash 不同，且没有 Web 资源树哈希清单 |
| 自动化/设备 QA | Web 流程通过，视觉与 Android 设备验收未完成 | E2E 和模拟战斗通过；API 34 模拟器在线但本轮未安装/操作 APK |

## Antigravity 下一轮工作包

按顺序交付，先关闭发布与资源完整性门槛，再扩动作和特效：

1. **资源引用修复：** 解决 Charlotte、Chesterbox、Cryochamber 的 atlas 缺失；生成引用检查，保证每个可选角色、敌人和关卡单位都能加载。
2. **动画闭环：** 建立 `web/data/animation-manifest.json`，为 Kitty、Dominica、Florence 逐项登记并实测 idle/enter/attack/skill/hit/victory/fall；缺动作时给出明示回退及 QA 日志。动画 marker 或事件回调要对应战斗事件，避免固定时长和数值结算漂移。
3. **VFX 闭环：** 新增轻量 VFX service/manifest，覆盖攻击、技能、暴击、治疗、护盾、控制、召唤、死亡、胜利；处理 WebGL 资源释放、连续触发重叠和 Reduce Motion。
4. **减少动效：** 同时覆盖大厅、详情、招募、战斗 Spine、CSS、镜头和 VFX；桌面与 Android 各保存一组开启/关闭证据。
5. **资源与发布清单：** 构建运行时逐项 manifest 与校验器，明确素材来源、SHA-256、显示尺寸和授权状态；更新 `dist/README-APK.md`，核实 debug 签名与当前产物版本/hash。
6. **验收：** Web E2E 加入所有单位引用检查、三角色动作和 VFX 事件检查、Reduce Motion 检查；Android API 34 模拟器走通相同战斗并归档截图/录屏、console、缺失资源日志。所有授权仍未确认的资产需在报告中保持发布阻断状态。

交付报告应列修改文件、manifest、Web E2E、Android build/device 状态、媒体证据、未验证平台和新的构建哈希。当前不应把资源数量或 Spine runtime 已接入等同于“美术/动画/特效达到成品标准”。
