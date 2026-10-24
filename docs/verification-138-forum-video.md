# 验证记录 — 138（论坛/视频加载提速 + HIG 收尾）— 2026-10-24

> 说明：`docs/verification.md` 顶部同时存在并行会话的 **Netgrey 品牌候选（137-netgrey）** 记录；本文件独立记录 138 代码优化，未合并进该文件以免覆盖并发工作。138 候选包由主工作树构建，**包含尚未提交的 Netgrey 品牌资源**。

## 改动

- 论坛：`ForumRepository.loadProgressively` 源级回调（先到先出）、首页快照（`forum_cache`，12h、40 条、逐条容错）、第 N+1 页后台预取（2 分钟单槽）、两个源改用共享 OkHttp 连接池 + `Jsoup.parse(stream, null, url)` charset 嗅探；**已移除启动期 HEAD 预热**（避免未打开论坛也向成人站点发请求）。
- 视频：`PlaybackResolver` 详情页抓取 60s 单航班缓存（`resolve` 与 `resolveEpisodes` 共用，进程级 companion）；ExoPlayer `DefaultLoadControl(15s/30s/800ms/1500ms)` 起播调优；字幕按钮/菜单/关闭（见 HIG 报告第三轮）。
- HIG 收尾：200% 搜索入口图标化、OEM 高对比候选键、搜索/离线缓存语义、无障碍测试扩到 6 项。详见 `docs/ui/hig-audit-2026-10-24.md`。
- 观测日志（不含内容）：`ForumRepository source=<id> page=<n> ms=<elapsed> posts=<count>`、`ForumViewModel first-screen ...`、`PlaybackResolver timing ...`、`NativeSitePlayer timing ready/first frame`。

## 实测（同一 Android 14 模拟器，Release 138；uiautomator 采样含约 0.5–1 s/次开销）

| 场景 | 137 基线 | 138 |
|---|---|---|
| 论坛：点开栏目 → 首条帖子（无快照冷启动） | 5.83 s / 6.55 s | **4.98 s** |
| 论坛：有 12h 快照再打开 | —（每次等网络） | **2.04 s**（快照先上屏） |
| 论坛源级：t906 / t66y | ~1.2 s（404）/ ~3.0 s | 1.51 s（0 帖）/ **2.73 s（100 帖）** |
| 论坛第 2 页预取 | 无 | **953 ms 后台完成**（首屏后） |
| 视频：详情页 GET 次数（resolve+episodes） | 2 | **1**（日志 `fetched=true` / `fetched=false` 单航班） |
| 视频：tap → source resolved | 3.18 s | **0.49 s**（含网络波动，非纯代码收益） |
| 视频：tap → READY | 7.1 s | **4.2 s**（HLS 分片/网络主导；LoadControl 主要利于 MP4/rebuffer，本条抽样无法单独归因） |

## 测试与构建

- JVM：**140 项 / 0 失败 / 1 跳过**（新增：论坛缓存 5、论坛渐进/合并 4、详情页缓存 5、OEM 取值 1）。
- 设备全量（`missavtest` AVD，单设备、空闲 host）：**32 / 0 失败 / 0 跳过**，含 `AuroraFlowTest` 10、`HigAccessibilityTest` 6、`PreReleaseSourceAuditTest` 5、直播/播放/论坛/漫画回归 11。
- Lint：**0 errors / 19 warnings / 3 hints**。
- Release：`dist/AuroraShelf-2.15.0-138-forum-video-fast.apk`，versionCode **138** / versionName 2.15.0；v2 签名 + `zipalign -P 16` 通过；SHA-256 `7f9a9b0c19c46c85e96d4f2e533c876ed0375c1fddd04792e8697187b66f173c`；安装并冷启动成功。
- 200% 字体：搜索入口节点 `contentDescription=搜索视频` 存在、无“搜…”截断文本。

## 未覆盖 / 风险

- 字幕菜单未取得真实有轨视频截图（抽样 4 条均无文本轨，按钮按设计隐藏）。
- 论坛快照+刷新失败时 UI 仍只显示旧列表（`ForumScreen` 仅空列表显示 error），建议后续加顶部错误条。
- `Jsoup.parse(stream, null, url)` 依赖页面 meta 声明编码；若站点仅靠响应头声明 GBK 且无 meta，可能乱码（当前两站未发现）。
- 论坛预取在移动数据下会额外拉 1 页（流量换延迟，可在设置中做成开关）。
- 视频 READY 提升受网络波动影响，不能把 4.2 s 全部归因于 LoadControl；结构性证据是详情页 GET 2→1 与解析 60s 缓存。
