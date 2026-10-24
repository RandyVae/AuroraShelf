# 验证记录 — 140（原创开屏动画）— 2026-10-24

> 独立文件，避免覆盖并行 Netgrey 品牌会话在 `docs/verification.md` 的记录。

## 行为

- **Release 冷启动**：Android 系统 splash → 原创开场（黑底、光带 N、光扫、字标、跳过提示）→ 淡出进入已加载的首页。
- **Debug 构建**：跳过开场（`BuildConfig.DEBUG`），保证 instrumented 测试不被全屏遮罩阻塞。
- **跳过**：点击任意位置或返回键 → 120 ms 淡出，立即进入 App；实测点击后 0.45 s 截图已是首页。
- **Reduce Motion**（动画倍率 0）：不做动画，静态显示完整标志与字标 0.7 s 后淡出。
- **加载不阻塞**：动画作为 overlay 覆盖在已组合的 App 上；首页/封面请求在动画播放期间并行进行，结束后直接是已加载内容。
- **时长**：时间线 1.9 s + 120 ms 淡出；素材 `captures/launch-intro-140.mp4`、`captures/launch-intro-140.gif`（本地）。

## 证据

- `docs/screenshots/launch-intro-2026-10-24.png`（动画中段）
- `docs/screenshots/launch-intro-reduced-motion-2026-10-24.png`（Reduce Motion 静态帧）
- JVM：`IntroAnimationTest` 6 项（时长上限、绘制阶段、光扫时序、字标时序、淡出、Reduce Motion 策略、毫秒换算）。
- 全量设备套件：**32 / 0 失败**；Lint：**0 errors / 19 warnings / 3 hints**。

## Release

`dist/AuroraShelf-2.15.0-140-intro.apk`，versionCode 140 / 2.15.0，v2 签名 + 16 KB zipalign 通过；SHA-256 `fa63feade1b16eaddc5973001d5db504257b513f7d8c0837b68c52409d5a08e3`。

## 未覆盖

- TalkBack 语音实际走查（标签已提供）。
- 与 Android 12+ 系统 splash 的衔接仅目测，未逐机型验证。
- 无音效（设计决定，见 HIG 报告）。
