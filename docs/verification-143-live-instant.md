# 验证记录 — 143（直播即点即播 / VOD 封面遮挡）— 2026-10-24

## 结论

直播点击已做到"即点即播"：**VOD 封面 0.05–0.1 s 出画，真实直播 WebView 0.3–0.5 s 就绪**（列表预热 15 s 以上时）；对比原 WebView 冷加载 **27.7 s**。该结论在 `emulator-5554`（test AVD，1080×2400）实测，APK SHA-256 `b1350a56…`。

## 基线（修复前）

- 环境：同一 AVD/网络，142 本地预览包。
- 方法：进入直播列表 → 点第一张卡片 → 以 uiautomator 轮询"静音/开启声音"控件出现作为"播放器就绪"。
- 结果：**27.7 s**。根因：点击后才创建 WebView，加载整页 Stripchat 房间 + 站点 JS 启动 + WebRTC 建连，再靠 JS 轮询发现播放器。

## 方案与实测

1. **原生 HLS 直连（否决）**：列表 API 返回 `hlsPlaylist`（`edge-hls.doppiocdn.media/.../*_480p.m3u8`）。可解码，但媒体播放列表为 `PLAYLIST-TYPE:VOD` + `#EXT-X-ENDLIST`，是约 20 s 的**快照而非直播**；冷启首帧 11.4 s（该网络 TLS 5.3 s + 分段 2.6 s）。播完即停，不能作为直播引擎。
2. **房间页预热 + 接管（已验证）**：直播列表出现即用 1 dp 隐藏 WebView 预加载首张卡片房间页并静音预播；点击后把已加载 WebView 直接移入播放页。实测日志 `LiveWeb: ready ms=295/530 prewarmed=true`，即 **0.3–0.5 s**；预热时间较短（约 8 s）时为 3.45 s。
3. **VOD 快照封面遮挡（已验证）**：按用户方案，预热 ExoPlayer 静音循环播放公开 HLS 快照，点击瞬间即可出画，直播页 ready 后撤掉封面切真实直播。
   - 实测：`cover taken=true` → `cover firstFrame` 相距 **0.05–0.1 s**；随后 `LiveWeb ready ms=295` 完成切换。
   - 快照开头是站点 bumper，已用 `seekTo(8 s)` 跳过；CDN 返回 304 的报错已用 `Cache-Control: no-cache` 请求头修复。
   - 封面失败/3.5 s 未出首帧会自动撤掉黑色遮罩，避免盖住直播页；120 s 空闲自动暂停预热流。

## 验收数据（最终包 b1350a56…）

| 项 | 结果 |
|---|---|
| VOD 封面出画 | 0.05–0.1 s（tap → `cover firstFrame`） |
| 直播就绪 | 0.3–0.5 s（预热 ≥15 s）；3.45 s（预热约 8 s） |
| 原冷加载基线 | 27.7 s |
| JVM 单测 | 154 / 0 失败 / 1 跳过 |
| 设备全量套件 | 32 / 0 失败（186 项含 JVM 合计全通过） |
| Lint | 0 errors / 19 warnings / 2 hints |
| 截图 | `docs/screenshots/live-instant-ready-2026-10-24.png`（4.3 s：控件/评论/房间信息已就绪） |

注：实时视频由 WebView 的 SurfaceView 渲染，`screencap` 不包含该层，所以截图播放区显示黑色；页面就绪、控件、评论与 ready 日志均正常，早前同链路也确认过 MediaCodec 解码活动。

## 已知限制

- 只预热首张卡片；点击其他卡片仍走常规加载（Cookie/HTTP 缓存已热，略快）。
- 列表停留期间 VOD（静音循环）+ WebView（静音预播）会各消耗带宽，均已限制：VOD 120 s 暂停；离开直播页全部释放。
- VOD 是快照，与真实直播存在时间偏差；切换后以直播为准。
- 仅模拟器验收，未上真机；低内存设备同时驻留 WebView+ExoPlayer 需真机复核。
- WebView 重挂载后首帧/Surface 重建时间受负载影响，数字为本次实测。
