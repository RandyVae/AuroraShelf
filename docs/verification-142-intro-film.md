# 验证记录 — 142（原创电影短片开屏）— 2026-10-24

> 独立文件，避免覆盖并行 Netgrey 品牌会话在 `docs/verification.md` 的记录。

## 行为

- **Release 冷启动**：系统 splash → 全屏电影短片（6.6 s，隐藏系统栏）：光刃自上而下刻出 Didot 字形铂金 N → 变形宽银幕光条/镜面高光 → Didot 暖银宽字距字标逐字浮出 + 深红细线 → 收黑；音效与画面同起。
- **跳过**：点击任意位置 / 返回 → 220 ms 淡出，立即进入 App（过渡页覆盖首帧组合）。
- **Reduce Motion**：不播放视频，静态锁定帧 1.5 s 后淡出，无音效。
- **音效开关**：设置 → 体验 →「开屏音效」；静音模式/媒体音量 0 自动静音；「开屏动画」可整体关闭。
- **Debug**：跳过开场，自动化测试不受影响。
- **启动时序**：影片独占首帧；`AuroraViewModel` 在 onCreate 预热首页；播完后黑底品牌过渡页在 App 首帧后 140 ms 淡出。

## 资产

- `res/raw/intro_film.mp4`：1080×2400 / 30 fps / 6.6 s / H.264+AAC / 约 3.4 MB，原创离线渲染（Pillow/numpy 逐帧合成 + ffmpeg 编码）。
- `res/drawable-nodpi/intro_poster.jpg`：锁定帧海报（Reduce Motion）；`intro_film_start.jpg`：近黑起始帧兜底。
- 原创配乐：numpy 合成（drone + Dm add9 pad + whoosh + metallic shimmer + sub + reverb），无第三方音频。

## 证据

- `docs/screenshots/launch-film-2026-10-24.png`（影片画面）
- `docs/screenshots/launch-film-reduced-motion-2026-10-24.png`（Reduce Motion 海报）
- `captures/intro-142-film.mp4`（实机录屏，含系统启动）
- JVM：**154 / 0 失败 / 1 跳过**（`IntroAnimationTest` 4 项：片长/安全超时/Reduce Motion/跳过淡出常量）。
- 设备全量套件：**32 / 0 失败**；Lint：**0 errors / 19 warnings / 3 hints**。

## Release

`dist/AuroraShelf-2.15.0-142-intro-film.apk`，versionCode 142 / 2.15.0，v2 签名 + 16 KB zipalign 通过；SHA-256 `460b4e471f3c962468f006081a3bc94aeb16f62dd1434ceed71f072a31273b54`。

## 限制

- 2.5D 离线渲染，非真 3D；艺术水准仍受自制渲染管线限制。
- 真机亮度/色域未验证（模拟器 E3）；TalkBack 语音走查未做。
- 影片 3.4 MB，APK 体积相应增加。
