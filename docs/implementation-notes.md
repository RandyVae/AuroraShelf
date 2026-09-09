# 方案 1 实现记录

## 调研与选择（2026-09-04/05）

开发前检索了 GitHub 同类实现，优先参考维护活跃、超过 500 stars 的工程：

| 项目 | 调研时 stars（约）/维护情况 | 技术与许可证 | 本项目取舍 |
| --- | --- | --- | --- |
| [android/compose-samples](https://github.com/android/compose-samples) | 23k，近期维护 | Kotlin / Compose，Apache-2.0 | 参考状态提升、原生控件与列表方式，未复制完整应用 |
| [android/nowinandroid](https://github.com/android/nowinandroid) | 21k，近期维护 | Kotlin / Compose，多模块，Apache-2.0 | 参考 UI 状态/数据层边界；当前规模不引入其完整模块体系 |

较旧的 Java/MVP 同类客户端虽然 stars 超过 500，但长期未维护且授权不明确，未复用其代码。发布前重新核对许可证与依赖 NOTICE。

Material 3 Expressive 的公开组件和 motion/shape 设计原则用于视觉参考：主题色容器作为状态层，内容保持清晰。Android 继续使用 Material 3、Material 图标及 Android 系统中文字体。

## 架构决策

1. 原文件没有可编译工程，独立重建而非声称原地源码升级。
2. Android 原生 Compose 首页和列表，不用 Web 前端原型冒充 APK。
3. 页面状态集中于 ViewModel/StateFlow，数据抓取、校验和偏好存储独立。
4. 播放解析站点页面公开的视频地址并交给 Media3；不执行页面脚本，也不绕过登录、验证码或内容权限。
5. Release 构建启用 R8 与资源收缩；开发测试证书只用于候选 APK。

## 合并论坛调研（2026-09-09）

按 `t66y android`、`discuz android`、`forum android compose` 检索 GitHub，没有找到达到 500 stars 且适合直接复用的 Compose 论坛解析器。较相关的 [BigAppOS/BigApp_Discuz_Android](https://github.com/BigAppOS/BigApp_Discuz_Android) 为 73 stars、Java、Apache-2.0，最后更新于 2023 年；[Comsenz/Discuz-Android](https://github.com/Comsenz/Discuz-Android) 为 67 stars、Java、非标准许可证，2026 年仍有更新；唯一命中草榴地址的 Kotlin 工程没有明确许可证。三者均未复制。

本项目采用现有 Jsoup、协程和 StateFlow 实现两个小型公开页面适配器：来源并发、失败隔离、合并排序和分页由独立 Repository 负责，Compose 页面只消费统一模型；帖子正文保留站点原始排版并在限制权限的 WebView 中阅读。该取舍避免引入旧 Java 客户端架构或许可证不明代码。

## 素材

生产包不捆绑演示视频或演示封面，列表图片来自用户配置的真实站点。界面图标来自 Compose Material 图标库；启动图标采用 Android adaptive icon 的单色白色圆角播放几何符号和珊瑚色背景，遵循 Android 官方图层安全区规则。图标不含文字、人物或站点内容。

## 有意保留的限制

- 测试版以方案 1 的深色手机视图为准，暂未增加浅色/平板独立设计。
- 视觉风格是 Material 3 Expressive 方向的 Android 实现，不依赖 Material 3 内部实验 API。
- 首页按站点分页追加并去重；搜索限定已加载/保存内容；没有实现原版全部功能。
- 没有源站服务级别保证。网络、重定向、Cookie、视频格式变化可能使播放失败。
- 未取得原版签名，独立包安装，不尝试覆盖原版或读取其私有数据。
