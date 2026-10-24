# DSH — 直播即点即播（143）

Date: 2026-10-24 21:45 HKT

## Context

用户反馈直播点开加载很久，要求做到即点即播，并提出"先播 VOD 快照、遮挡直播页同步加载"。

## Findings

- 基线（142 本地预览，同一网络）：点击到播放器就绪 27.7 s；根因是点后才创建 WebView 加载整页房间 + JS 启动 + WebRTC 建连。
- 列表 API 公开的 `hlsPlaylist` 是约 20 s 的 VOD 快照（PLAYLIST-TYPE:VOD + ENDLIST），冷启首帧 11.4 s；不能当直播引擎，只能做封面预览。
- 房间页 WebView 预热后接管：实测 3.45 s（约 8×），评论功能保留。

## Delivered

- `LiveRoomPrewarm`：首卡房间页隐藏预热、点击接管、120 s 空闲暂停、离开释放。
- `LiveVodPreview`：静音循环 VOD 预热与遮罩层，直播 ready 后移除并切真实直播（待设备验收）。
- 仓库解析 `LiveItem.hlsUrl`（480p→240p→API 回退）；`onReadyChange` 切换；播放器加载期显示封面缩略图。
- 本地基线脚本 `scripts/local-intro.sh`；143 本地预览包。

## Verified / Unverified

- Verified: 方案 2 设备 3.45 s；VOD/ENDLIST 结论；JVM + Lint BUILD SUCCESSFUL。
- Unverified: 方案 3 整链路的设备截图/录屏；因 Pixel Launcher ANR 与系统服务异常中断，需重启模拟器后补验；方案 3 版本未重跑设备全量套件（方案 2 版本为 32/32）。
