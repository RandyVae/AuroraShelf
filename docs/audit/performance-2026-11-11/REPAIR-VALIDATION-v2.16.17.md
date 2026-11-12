# v2.16.17 / Build 243 修复验证记录

日期：2026-11-12

本轮在 v2.16.16 / Build 242 基础上修复性能、缓存、离线下载、启动加载、直播降级、TLS、游戏资源与无障碍问题，版本递增到 v2.16.17 / Build 243。当前只保留本地修改，尚未提交。

## 已落地的修复

- iOS 直播、HLS 下载和部分媒体请求复用 `permissiveShared` 会话，减少逐切片建连。
- iOS 封面加入下采样和 64 MB 内存成本上限。
- 黄果剧集缓存键不再把第二集映射到第一集。
- iOS HLS 下载校验 HTTP 状态与空切片，移除 360 片硬截断。
- iOS 漫画本地图片读取移出主线程，整本下载限制为两路并发。
- iOS 搜索结果按来源逐步显示，启动预热复用现有首屏和论坛结果，避免重复请求。
- Android 启动界面提前组合主页面，关闭无消费方的全局论坛/漫画预热。
- Android 直播播放器错误时自动切换备用视口。
- Android 漫画章节预取登记任务后再启动，减少同章重复请求。
- Android 游戏配置文件并行读取，Slots 复用已加载的配置。
- Android 直播网络恢复系统 TLS 校验并增加总调用超时。
- 清理 3 组重复游戏视频资源，统一引用到 CG 文件。
- Android 底部导航图标恢复语义化 contentDescription。
- Android 91 动漫分页末页 404 按空页处理。

## 构建与模拟器证据

- Android JVM：`testDebugUnitTest assembleRelease` 成功，241 项总测试中 234 通过、7 跳过、0 失败。
- Android Release 包：versionCode 243、versionName 2.16.17，安装到 API 34 `test_avd` 模拟器启动成功，首页截图：`captures/v243-release-android.png`。
- Android 全源审计：`PreReleaseSourceAuditTest` 使用 `sourceAudit=true` 返回 `OK (5 tests)`。日志覆盖视频列表/分页/搜索/播放解析、漫画章节、直播、论坛。哔咔按授权源记录 `NEEDS_ACCOUNT`。
- iPhone Release：构建成功，iPhone 17 Pro iOS 26.4 安装启动成功，截图：`captures/v243-release-iphone.png`。
- iPad Release：构建成功，iPad Pro 13-inch M5 iPadOS 26.4 安装启动成功，截图：`captures/v243-release-ipad.png`。
- iPhone XCTest：164 项总测试，157 通过、6 跳过、0 失败。
- iPad XCTest：164 项总测试，157 通过、6 跳过、0 失败。
- Swift 全源诊断：32/32 通过。
- Node 全源探针：34/34 可达。SpankBang 和 85po 在 Node 请求中返回反爬响应，但原生 Swift 诊断和 Android 源审计均通过。哔咔接口可达但匿名请求需要账号授权。

## 尚未达到提交条件的项目

Android `connectedDebugAndroidTest` 全套仍有 8 个 UI 测试失败。失败集中在测试之间共享目的地状态、首页网格等待、搜索结果等待和播放器详情等待。单独的基础导航测试可以通过，失败测试的现有断言依赖测试顺序或网络数据到达时间。本轮没有修改产品逻辑去迎合这些顺序依赖，也没有提交代码。

下一步应先把 Android UI 测试改成每项独立设置初始目的地和本地 fixture，再重跑完整 connected suite。完成前不应提交或发布。
