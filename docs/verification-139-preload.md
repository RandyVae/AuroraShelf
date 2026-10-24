# 验证记录 — 139（封面全板块提速 + 视频预加载）— 2026-10-24

> 与 `docs/verification.md` 分离：该文件顶部有并行会话的 Netgrey 品牌记录，本轮不覆盖。139 候选由主工作树构建，**包含尚未提交的 Netgrey 品牌资源**。

## 改动

**封面（全板块）**
- 解码缓存：`MediaImage`（首页/搜索/相关推荐）与 `LiveCard` 之前把 `ByteArray` 直接交给 Coil，**没有稳定 memoryCacheKey**，滚回会重复解码；现在统一 `ImageRequest.data(bytes).memoryCacheKey(thumbnailUrl)`，同尺寸滚动复用已解码位图。
- Coil 缓存显式化（`AuroraApplication`）：内存 25% 可用堆（上限 64 MB）+ 磁盘 `cacheDir/image_cache` 128 MB；保留 OkHttp + `EhentaiDns` fetcher。实测磁盘缓存已写入（测试后 776 KB）。
- 预热（`CoverPrefetcher`，字节并发 4、解码入队、10 分钟去重、日志不含 URL）：漫画前 10、直播前 8、搜索前 8、播放器相关推荐前 6；首页沿用既有前 8 张字节预热（首页文件与并行品牌改动同文件，本轮未动）。
- 预热仅在网络非计费且用户"视频预加载"开关开启时执行（与视频预取共用策略）。

**视频预加载**
- `PlaybackResolver.prefetchDetail`：首页第一页成功后 1.5 s 预热前 2 条详情页 HTML（60 s 缓存，不请求媒体）。
- `PlaybackResolver.prefetchPlayback`：播放页剧集就绪后 2 s 预热下一集播放地址（写入 2 min 播放地址缓存）。
- 策略：`PrefetchPolicy`（开关 && 非计费网络，读不到网络信息按计费处理），延迟前后各查一次；进程内去重。
- 设置：我的 → 设置 → 体验 → "视频预加载" Switch（默认开，48dp，副标题"仅 Wi-Fi 下预取首页视频详情与下一集地址"），关闭后 `Prefetch: detail skipped`。

## 实测（Release 139，Android 14 模拟器）

| 项目 | 结果 |
|---|---|
| 首页详情预取 | 2 条，`detail hit=false ms=834` / `ms=498` |
| 点击 hero 后解析 | `source resolved ms=48`（无预取基线 493–3180 ms） |
| 漫画封面预热 | `kind=comics count=10 ms=0`（Coil 入队异步解码） |
| 直播封面预热 | `kind=live count=8 ms=1984`（含字节+解码入队） |
| 搜索封面预热 | `kind=videos count=8 ms=2081` |
| 相关推荐预热 | `kind=videos count=6 ms=4` |
| Coil 磁盘缓存 | `cache/image_cache` 已创建并写入（776 KB） |
| 关闭开关后 | `Prefetch: detail skipped`，无预取请求 |

说明：封面"提速"的确定收益是 **同尺寸滚动复用解码位图 + 预热隐藏网络/解码延迟**；没有帧级 profiler，未宣称具体 FPS/毫秒提升。视频 READY 依旧受 HLS 分片与网络限制（本次 READY 约 5 s），预取解决的是"点击后详情页解析"这一段（48 ms vs 493–3180 ms）。

## 测试与构建

- JVM：**150 项 / 0 失败 / 1 跳过**（新增 CoverPrefetchTest 6、PrefetchPolicyTest 3、PlaybackResolver isFresh 1）。
- 设备全量套件：**32 项 / 0 失败 / 0 跳过**（AuroraFlow 10、无障碍 6、来源审计 5、直播/播放/论坛/漫画回归 11）；Lint：**0 errors / 19 warnings / 3 hints**。
- Release：`dist/AuroraShelf-2.15.0-139-preload.apk`，versionCode 139 / 2.15.0，v2 签名 + 16 KB zipalign 通过；SHA-256 `2209ed231adef4896990f62334d05740d94b4dfa86495918376c811667c010c0`；安装并冷启动成功。

## 风险 / 未覆盖

- 预热流量：Wi-Fi 下首页 2 个详情 HTML（不含媒体）、下一集 resolve（HTML，可能含二级播放页，不含 HLS/MP4 字节）、封面 ≤ 10 张；受开关 + 非计费网络双重约束。
- 解码预热命中率受 Coil `Precision.EXACT` 尺寸校验影响：全宽 hero/直播卡大概率命中，半宽网格可能重新解码；确定收益是 URL keyed 内存缓存。
- URL 内容变更后已解码位图在淘汰前可能显示旧图（字节缓存 TTL 独立）。
- 128 MB Coil 磁盘缓存对低存储设备偏大，可后续按设备下调。
- 未做帧级性能分析；预热可能弱网下与视频起播抢带宽（延迟 1.5/2 s 后启动，必要时可调大）。
