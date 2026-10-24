# DSH — 直播 A+B 稳定提速（146）

Date: 2026-10-25 00:05 HKT

## Context

用户确认"先做 A+B，不碰 C"。145 已完全回退直播（无年龄窗、无 VOD 广告）。

## Delivered

- A：播放页加载期显示列表封面大图 + spinner（实测截图）。
- B：`LiveSessionCache` 保留最后打开房间的 WebView（静音暂停），返回列表再进同一房间直接接管；打开其他房间/离开直播页释放。
- 版本 146 本地包；未改站点协商逻辑、无年龄注入、无 VOD。

## Verified / Unverified

- Verified：A（0.5 s 截图显示封面 + 页面框架）。
- Unverified：B 端到端（模拟器上 Stripchat 当前返回访客页，无 video 可播）；需真机可播放房间测试。

## Next

真机验证 B；若仍被访客页拦住，再评估 C（手动确认一次后持久化 cookie + 开关预热）。
