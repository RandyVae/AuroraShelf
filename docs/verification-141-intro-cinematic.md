# 验证记录 — 141（电影感开屏动画，原创）— 2026-10-24

> 独立文件，避免覆盖并行 Netgrey 品牌会话在 `docs/verification.md` 的记录。

## 行为

- **Release 冷启动**：系统 splash → 4.2 s 电影感原创开场（深空极光 → 扁平多彩折面 N 展开/折痕/投影/3D 回正 → 冲击波/粒子 → 逐字渐变字标 → 光线收束）→ 黑底品牌过渡页覆盖 App 首帧组合 → 淡入首页。音效（原创合成，2.9 s）与画面首帧同步。
- **跳过**：点击任意处 / 返回键 → 立即走过渡页进入 App。
- **Reduce Motion**：静态完整锁定帧 1.2 s 后淡出；音效关闭。
- **开关**：设置 → 体验 → 「开屏动画」「开屏音效」，默认开；静音/媒体音量 0 时音效不播放。
- **Debug**：跳过开场，自动化测试不受影响。
- **启动时序**：App 组合推迟到开场结束，ViewModel 在 `onCreate` 提前创建以在开场期间预热首页数据；过渡页在 App 首帧后 140 ms 淡出。

## 证据

- `docs/screenshots/launch-intro-cinematic-2026-10-24.png`（多彩折面 N + 字标锁定帧）
- `docs/screenshots/launch-intro-cinematic-ribbons-2026-10-24.png`（展开中的 N + 粒子）
- 本地录屏：`captures/intro-141-cinematic.mp4`
- JVM：**156 / 0 失败 / 1 跳过**（`IntroAnimationTest` 6 项：时间线阶段、冲击、字标、淡出、Reduce Motion、时长换算）。
- 设备全量套件：**32 / 0 失败**；Lint：**0 errors / 19 warnings / 3 hints**。

## Release

`dist/AuroraShelf-2.15.0-141-intro-cinematic.apk`，versionCode 141 / 2.15.0，v2 签名 + 16 KB zipalign 通过；SHA-256 `d549583682531ef372c04b1fa662aa123bcbd60623c56f415766e1328edaa55a`。

## 限制

- 模拟器（host 同时运行多台 AVD）实测开场墙钟约 5.9 s，存在掉帧；真机预期接近 4.4 s，未在真机验证。
- 未复刻 Netflix 标识/音效（版权/商标边界），只做到同类型的电影感原创。
- TalkBack 语音走查未做；与系统 splash 的衔接仅目测。
- 观感受 Compose Canvas 限制，若需要 3D 丝带级效果需专业动效与预渲染视频资产。
