# DSH HIG Round 2 + Loading Optimization

Date: 2026-10-24 15:00 HKT

## Objective

实现上一轮 HIG 报告的全部未达标项，并进一步优化应用加载链路。

## Delivered

- 加载：首页首屏本地快照（source+category，24h）、封面 24MB 内存 + 96MB 磁盘 LRU、单航班、60s 负缓存、共享 OkHttp、播放解析 2min/10min 缓存、首屏 8 张封面预热。
- 首屏实测（同环境）：4.79/5.80 s → 冷 4.50 s / 热 3.22–4.04 s。
- HIG 第二轮：高对比三态 token + 检测 + 动态色守卫；深色封面压暗 11%；200% 字体降列 + hero 重叠修复；15 处 heading；字幕轨优选；3 项无障碍设备断言。
- 回归修复：回退异步收藏库加载；修复首页标题被并发改名影响的一处文案；无障碍测试不再硬编码品牌文案。
- 验证：JVM 125/0/1；定向设备测试 PASS；Release 137（SHA 722fbe97…）安装与冷启动通过。
- 并发品牌改名（app_name→网灰、启动图标替换）由另一路工作产生，已原样保留、未纳入本轮提交。

## Blocked / follow-up

- 137 全量 connected 套件未在空闲 host 重跑（3 台模拟器并行导致超时/失败）。
- TalkBack 人工走查、OEM 高对比开关、字幕 UI 切换器、真机 E4 证据仍缺。
