# 极光视界 · Android 2.12.0

按用户选定的「方案 1」重新实现的 Android 客户端。采用 Kotlin、Jetpack Compose、Material 3、Haze 与 AndroidLiquidGlass/Backdrop；Material You 界面保留真实视频源能力，并以浮动玻璃导航、吸顶分类和弹性选中动效重构视觉层级，不是网页套壳首页。2.9.0 保留首个大幅推荐视频并将后续内容改为 YouTube 式双列信息层级；2.9.1 新增完整日间与夜间主题，默认跟随系统即时切换。

## S26 Ultra 默认设置适配

当前交付包可从 [GitHub Releases](https://github.com/RandyVae/AuroraShelf/releases/latest) 下载。与上一版同包名、同测试签名，版本号递增，可直接更新安装，无需卸载「极光视界」。原版应用仍不受影响。

针对默认字体/屏幕缩放的大屏使用场景，首页按实际窗口高度调整推荐封面，列表从浮动导航后方延伸，末尾仍保留足够滚动空间。横屏切换为 64 dp 侧边导航并限制内容宽度，避免底栏压缩主内容；横向安全区避开挖孔和侧边系统导航。

没有把某个屏幕 DPI 当成三星默认值写死：相同机型的地区、系统版本、分辨率和分屏状态可能改变应用可用空间。本次最终截图在 Android 14 模拟器以 1080 × 2340、density 440 验证，并补充约 384/411 dp 两种逻辑宽度测试；不是 S26 Ultra 真机或 One UI 仿真，未强制刷新率，也不保证 120 fps。详见 `docs/s26-adaptation.md`。

## 当前范围

### 在线漫画（2.12.0）

- 底栏“漫画”模块内置 Komiic、包子漫画、拷贝漫画、爱看漫、哔咔、E-Hentai 和禁漫天堂七个原生 Kotlin 适配器，支持独立分类、搜索、详情、章节和纵向连续阅读；默认使用已验证可读的 Komiic。
- 漫画列表接近底部自动加载下一页；阅读图片携带来源要求的 Referer，并交给 Coil 做内存与磁盘缓存，单页失败可独立重试。
- Komiic、E-Hentai 公共站和禁漫天堂已完成列表、详情、章节与图片线路的实时链路验证；包子漫画在 Android 14 上受当前证书链限制，拷贝漫画在当前网络环境 DNS 不可达，爱看漫返回 HTTP 403，界面会显示真实错误并允许切源。
- 哔咔只增加其内容访问必需的最小登录：密码仅用于本次授权，不会保存；访问令牌使用 Android Keystore 加密。会员、支付、评论、漫画下载和云同步仍不接入。
- E-Hentai 仅连接匿名公共画廊，不提供 ExHentai Cookie 或权限绕过。
- 设置页采用 Material 3 主页面与二级页面结构，视频源可搜索，漫画源使用底部选择面板；来源切换、详情与章节页面均支持日间、夜间和 Material You 动态色。

### 多视频源（2.6.0）

- 首页和设置页可选择默认源、黄果，以及 MISSPipe/PipePipeExtractor 注册的多个服务。
- MISSPipe 服务使用其各自的真实 Kiosk、联网搜索和播放解析器；不再把不同站点当成同一种 HTML。
- 2.6.1 为跨站封面请求补齐移动端 UA、Referer 和 Origin；解析失败会显示错误，不再静默变成空白列表。
- 2.6.2 修复黄果切换剧集后播放器画面未重新绑定的问题；选集面板改为按需展开并在选择后自动收起。
- 设置页将每个视频源拆为独立惰性列表项，长列表在小屏和无障碍操作下均可完整滚动。
- `third_party/pipepipe-extractor` 为 GPLv3 源码依赖，分发包含该解析器的构建产物时必须同时提供源码和许可证，见 `THIRD_PARTY_NOTICES.md`。

- 首页：五种站点分类、首个大幅推荐视频和后续双列视频网格；接近底部自动请求下一页并按 ID 去重，奇数末项不会被拉伸。
- 搜索：MISSPipe 服务执行站点联网搜索；默认源和黄果继续搜索已加载及本地保存内容。
- 收藏和观看历史：本地持久化；历史最多 50 条。未接入账号或云同步。
- 站点设置：读取原 APK 设置中确认的地址作为默认值，优先 HTTPS；可改地址并重新加载真实内容。已适配 `huangguoai.com` 的详情链接、分页和 HLS 播放数据。
- 播放页：解析站点页面公开的视频地址后使用 Media3 原生播放，提供返回、收藏、全屏承载和生命周期暂停；没有绕过登录、验证码或内容权限。
- 离线缓存：播放页按当前剧集缓存完整 MP4/HLS；设置页可查看进度与容量、重试、播放、单项删除或清空全部。内容保存在应用私有目录，不申请外部存储权限。
- 无障碍：原生点击控件、图标描述、缓存状态描述、弹层隐藏底层语义。动画使用 Compose 动画时钟，跟随系统动画时长设置。
- 主题：Android 12 及以上分别使用系统 Material You 日间/夜间动态色；旧系统使用经过对比度校准的浅色与深色回退方案。状态栏、导航栏图标和液态底栏会同步切换。

这是内部测试版：列表按站点分页并在接近底部时自动加载更多；除哔咔内容访问所需的最小授权外，不包含会员、支付、评论、关注和账号同步模块。网络、站点结构及媒体格式变化仍可能影响播放、阅读和缓存。

## 与原项目的关系

用户提供的源码目录、归档和所检索到的仓库只有说明与截图，没有可编译 Android 工程。本目录是独立重建工程。为核对视觉实现，本轮只检查参考 APK 的公开资源尺寸、布局名称与 DEX 字符串；没有解出、复制或复用其受保护的业务源码和品牌资源。

**原 APK 能加载真实列表。**已在模拟器中打开原 APK，并从其地址设置确认站点；此前将“没有编译源码”误当作“没有有效站点”的判断不准确。新解析器依据实际 HTML 的 `.video-title`、`.duration` 和视频链接结构适配。

新版包名 `com.aurorashelf.app`，原版包名不同，两者可以共存。不会覆盖原安装，也不会自动迁移原版的收藏或账号。Android 最低版本为 8.0/API 26（原 APK 的最低版本更低）。

## 构建

需要 JDK 21、Android SDK platform 37/build-tools 37.0.0。目标 API 为 36，Gradle Wrapper 为 9.6.0；工具版本固定以便复现，不自动追随更新。

1. 设置 `JAVA_HOME` 与 `ANDROID_HOME`，或在未提交的 `local.properties` 中配置 `sdk.dir`。
2. 如需构建哔咔和禁漫天堂线路，将 `source-secrets.properties.example` 复制为被 Git 忽略的 `source-secrets.properties`，填入兼容客户端的协议参数；也可使用同名环境变量。不要提交该文件。
3. 运行：

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
./gradlew assembleRelease
```

`assembleRelease` 输出经过 R8 和资源裁剪的 APK。为避免 macOS 文稿目录同步程序复制临时 class 文件，构建产物位于 `build.nosync/app/`。交付包使用本机 Android 开发测试证书签名，仅供安装验收，不是官方发行签名。

可选真实网络检查（不输出远端内容）：

```sh
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.aurorashelf.app.LiveFeedTest \
  -Pandroid.testInstrumentationRunnerArguments.live=true
```

默认 UI 测试不捆绑样例媒体；实时列表用例需要网络，避免把网络波动当成离线回归失败。

## 模块

| 路径 | 职责 |
| --- | --- |
| `app/src/main/java/com/aurorashelf/app/data` | 视频与漫画源适配、地址校验、本地收藏与历史 |
| `app/src/main/java/com/aurorashelf/app/model` | 视频、漫画、分类与目的页面模型 |
| `app/src/main/java/com/aurorashelf/app/ui` | 视频/漫画页面、阅读器、状态管理和 Material 3 控件 |
| `app/src/main/java/com/aurorashelf/app/ui/theme` | Material 3 颜色与字体 |
| `app/src/test`、`app/src/androidTest` | 解析/校验单元测试及模拟器流程测试 |

## 设计与性能取舍

UI 采用 Material 3 Expressive 的公开设计语言，并参考用户提供的 CoolApk 16.6.1 APK 的可观察视觉机制。APK 中保留的依赖地址指向 Apache-2.0 的 AndroidLiquidGlass，因此竖屏底栏采用同一 Backdrop 光学引擎：底层内容被实时采样，选中透镜提供折射、色散、亮边、阴影和速度形变。五个栏目保持统一的可点击与可拖动样式，透镜可以在五个位置间连续跟手；跨栏提供轻触反馈，松手后弹簧落位。分类栏在头部滚出后保持吸顶，完整卡片可从导航后方穿过并最终越过遮挡。横屏继续使用自适应侧边导航。

视觉参考、差距分析和实现映射见 `docs/design-audit/coolapk-16.6.1/audit.md`。本项目没有复制参考 APK 的代码、资源或品牌资产。

启动图标是无文字的单色圆角播放几何符号，使用 Android adaptive icon 前景、安全区和单色遮罩能力；不再使用复杂装饰或系统占位图标。

首页使用 LazyColumn、稳定行键和每行两个视频卡片；首个视频保持独立大卡，后续卡片以 16:10 封面、两行标题及单行来源信息组成。图片使用 Coil 缓存，HTML 读取在 IO 协程中执行，切分类取消旧请求，避免结果倒序覆盖。已实现代码层面的优化，但尚无帧率、电量、内存或原版对照性能基准；不宣称性能提升百分比。

## 隐私与安全

收藏与历史仅保存在应用私有目录，关闭系统备份/设备迁移。不索取相册、存储、通讯录权限。站点仍会收到网络请求及 WebView Cookie；“本地保存”不等于匿名访问。网页脚本为站点播放所需而启用；禁用文件与内容 URI 访问、HTTPS 混合内容以及非 HTTP(S) 外部跳转，不忽略证书错误。

为兼容用户主动设置的旧站点，应用允许 HTTP；HTTP 无传输加密，建议始终使用 HTTPS。源站地址和 HTML 结构可能变化；空列表或连接失败会显示错误，不会静默伪装成真实内容。

## 贡献与验收

修改保持聚焦，新增行为补充测试，提交使用 Conventional Commits，代码合并通过 PR。公开仓库与版本安装包统一发布在 [RandyVae/AuroraShelf](https://github.com/RandyVae/AuroraShelf)；提交前查看 `CHANGELOG.md`、`design-qa.md` 与 `docs/verification.md`，不要把构建成功等同于真机播放验收通过。

本工程没有复制原项目业务代码，也没有假定原项目授予代码再发布许可。AndroidLiquidGlass/Backdrop 的 Apache-2.0 归属说明见 `THIRD_PARTY_NOTICES.md`，其他第三方依赖及生成图标资产说明见 `docs/implementation-notes.md`；公开发布前仍需完成许可证与分发要求审查。
