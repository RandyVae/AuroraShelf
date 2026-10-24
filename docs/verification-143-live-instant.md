# 验证记录 — 143（直播即点即播 / VOD 封面遮挡）— 2026-10-24

## 目标

把"点开直播 → 出画面"从冷启动 WebView 加载（实测 27.7 s）压到接近即点即播。

## 基线

- 环境：`emulator-5556`，143 前置本地预览包（142 本地开屏包），同一网络。
- 方法：进入直播列表 → 点第一张卡片 → 以 uiautomator 轮询"静音/开启声音"控件出现为"播放器就绪"。
- 结果：**27.7 s**（点击到播放器就绪，含轮询开销约 +0.5 s）。根因：点击后才创建 WebView，加载整个 Stripchat 房间页 + 站点 JS 启动 + WebRTC 建连，再靠 JS 轮询找播放器。

## 方案演进

1. **原生 HLS 直连（否决）**：列表 API 返回 `hlsPlaylist`（`edge-hls.doppiocdn.media/.../*_480p.m3u8`）。实测可解码，但媒体播放列表为 `PLAYLIST-TYPE:VOD` + `#EXT-X-ENDLIST`，是约 20 s 的快照而非直播；且冷启首帧 11.4 s（该网络 TLS 握手 5.3 s + 媒体分段 2.6 s）。**不能作为直播引擎**，否则播完即停。
2. **WebView 页面预热（已设备验证）**：直播列表出现时，用 1 dp 隐藏 WebView 预加载首张卡片房间页并静音预播；点击后直接把已加载 WebView 移入播放页，页面不再重载。实测日志 `LiveWeb: ready ms=3450 prewarmed=true`，**约 3.5 s**（较 27.7 s 约 8 倍）。代价：网格停留期间后台持续拉流（已在 120 s 后自动暂停）。
3. **VOD 封面遮挡（本次新增，待设备验收）**：按用户思路，预热点播 VOD 快照（静音、循环），点击瞬间即可显示同一主播画面，遮住直播页加载；直播 WebView 报告 ready 后移除 VOD 层并切到真实直播。为控制双流带宽，WebView 预热改为"只加载页面、不自动播放"，VOD 是唯一预热的媒体流。

## 本次代码与验证状态

- 已实现：`LiveRoomPrewarm`（页面预热/接管/120 s 空闲暂停/离开释放）、`LiveVodPreview`（ExoPlayer 静音循环 VOD 封面、take/release）、`LiveScreen` 叠加层与 `onReadyChange` 切换、`LiveItem.hlsUrl` 与仓库解析（480p 优先、240p 回退、非法 streamName 回退 API 播放列表）。
- **已实测**：方案 2（3.5 s，见上）；VOD 冷启首帧 11.4 s、播放列表 VOD/ENDLIST 结论。
- **未完成**：方案 3 的整链路实机验收。测试时 `emulator-5556` 遭系统级 Pixel Launcher ANR（`Can't find service: activity`、点击被弹窗吞掉），无法取得"点击后 0.6 s VOD 已显示 / 直播 ready 后切换"的截图与日志。
- 静态验证：`testDebugUnitTest`、`lintDebug` 本轮 **BUILD SUCCESSFUL**；设备全量 32/32 是在方案 2 版本上取得的，方案 3 未重跑设备套件。

## 已知限制

- 只预热首张卡片；点击其他卡片仍是冷加载（但 Cookie/HTTP 缓存已热，略快）。
- 网格停留时 VOD 静音循环约 1 Mbps 预取流量，2 分钟后暂停；离开直播页立即释放播放器与 WebView。
- VOD 是快照画面，可能与真实直播有几秒偏差；切换时以直播为准。
- 旧 Android/低内存设备上"WebView + ExoPlayer + 页面预热"同时驻留需真机复核（模拟器可跑）。

## 下一步

1. 主机负载降低/重启窗口模拟器后，补做方案 3 的设备验收与录屏（点击后 VOD 首帧时间、切直播时间、评论/音轨是否正常）。
2. 方案 3 通过后重跑设备全量套件并更新本记录。
