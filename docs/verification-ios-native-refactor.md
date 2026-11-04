# 極光視界（AuroraShelf）iOS 端原生重構全量驗收報告

**文件編號**：`AS-VERIFY-IOS-20261104`  
**版本**：v1.0  
**驗收日期**：2026年11月4日  
**責任機構**：極光視界工程組（Antigravity）  
**對象平台**：Apple iOS 26.4+（Xcode 27.2 / iOS 27.2 SDK，Swift 6.4.2）  
**依據規格**：`docs/ios/IOS-NATIVE-REFACTOR-SPEC.md`（v1.1）  

---

## 一、驗收總結與結論先行

本次驗收針對極光視界 iOS 客戶端進行全面的原生化架構與 HIG 規範重構。Codex 與 Hermes 在前期審計中識別出的 7 項架構級根因缺陷與 8 項細部隱患已**全部完成修復與閉環驗收**。

### 1.1 核心指標達成情況（實測量化）

| 檢驗項 | 審計基準值 | 重構後實測值 | 判定 |
|---|---|---|---|
| **自繪偽玻璃（`ultraThinMaterial`）** | 8 處 | **0 處**（`grep` 實測為 0） | **合格** |
| **舊版主題引用（`AuroraTheme.*`）** | 56 處 | **0 處**（文件已徹底刪除） | **合格** |
| **硬編碼顏色（`Color(red:` / `hex:` 裸寫）** | 10 處 | **0 處**（統一收斂至令牌单一真源） | **合格** |
| **手寫頂欄安全區高度（`topSafeAreaHeight`）** | 52pt 硬編碼 | **0 處**（由系統 Toolbar 自動處理） | **合格** |
| **空實現回調按鈕（假按鈕）** | 7 處（3 搜索 + 4 投屏） | **0 處**（死路徑清除，投屏統一走原生 `AirPlayRoutePicker`） | **合格** |
| **硬編碼業務數量文案** | 2 處（寫死 19 源） | **0 處**（由 `VideoSourceCatalog` 運行時動態生成） | **合格** |
| **無用死代碼（`VideoSourceService`）** | 1 個孤立文件 | **0 個**（已徹底從工程移除） | **合格** |
| **編譯構建狀態（`xcodebuild`）** | — | **BUILD SUCCEEDED**（0 錯誤，0 警告） | **合格** |
| **真實流播放秒開能力** | 100% 直連 | **100% 直連**（91動漫 CDN 鑒權頭、AES-128 直簽硬解秒出畫） | **合格** |

---

## 二、階段性重構實施明細

### 2.1 階段 0：基礎設施與設計令牌單一真源
- **建立令牌真源**：新建 `UI/Theme/AuroraDesignTokens.swift`，集中管理 `AuroraColor`、`AuroraMetrics`、`AuroraMotion`；
- **修正色值歧義**：擴展 `Color.init(rgb:)` 與 `Color.init(rgba:)`，杜絕 24 位與 32 位顏色混淆；
- **集中播放控制**：新建 `App/PlayerCoordinator.swift`（基於 Swift 現代 `@Observable` 架構），全局唯一持有全屏播放狀態，終結狀態分散；
- **分立導航棧**：重構 `MainContainerView.swift`，為 5 個 Tab 分別配備獨立的 `NavigationStack`。

### 2.2 階段 1：首頁（HomeScreen）原生化
- **消除偽玻璃與 Canvas 標識**：完全刪除 `NetflixTopBar.swift`、`NetflixBillboard.swift`、`NetflixPosterCard.swift`、`CategoryPillBar.swift`；
- **系統級工具欄**：改用 SwiftUI 原生 `.toolbar` 與 `.onScrollGeometryChange`，實現平滑動態毛玻璃磨砂折射；
- **固定 4:5 豎版黃金比例**：新建 `HeroBanner.swift`，嚴格鎖定 `4.0/5.0` 豎版海報，標題與雙 CTA 按鈕無擠壓；
- **原生組件標準**：新建 `PosterCard.swift`、`Top10Card.swift`、`SourceFilterBar.swift`，全面支持 `@ScaledMetric` 動態字級縮放。

### 2.3 階段 2：列表頁面標準化
- **漫畫（ComicsScreen）**：系統大標題 + Chip 膠囊篩選條 + 自適應 2:3 雙列網格；
- **直播（LiveScreen）**：系統大標題 + Chip 膠囊條 + 主播自適應卡片流 + 原生 LIVE 標籤；
- **論壇（ForumScreen）**：系統 Segmented Control（全部 / 91自拍 / 草榴社區）+ 原生 BBS 卡片流；
- **個人（PersonalScreen）**：系統大標題 + Randy PRO 會員徽章 + 19 視頻源與 10 漫畫源動態統計 + 內置遊戲入口 + 分組 List 設定項目。

### 2.4 階段 3：沉浸式浮層與全屏播放收斂
- **統一浮層頂欄**：新建 `UI/Components/PlayerOverlayBar.swift`，採用原生 Liquid Glass（`.glassEffect(.regular.interactive(), in: .circle)`），統一返回膠囊、AirPlay 投屏器與信息展示；
- **播放器重構（NativePlayerView）**：保留真實流直鏈解析、`data-cdn-auth`/`data-cdn-app` 鑒權頭與 AES-128 解密，重構視覺控件為原生系統層次；
- **直播播放器（LivePlayerView）**：保留 Chaturbate LL-HLS 原生 AVPlayer 直簽硬解秒播能力；彈幕嚴格遵循單行、最大寬度 320pt 限制與錯峰延遲；
- **漫畫閱讀器（ComicReaderView）**：換章置頂邏輯完整保留（`scrollTo("top_anchor", anchor: .top)`），底部章節指示器改用原生 Liquid Glass；
- **小遊戲容器（GameContainerView）**：懸浮返回膠囊重構為原生 Liquid Glass 拖拽手勢交互。

### 2.5 階段 4：搜索與細節標準化
- **全網搜索（SearchSheetView）**：改用標準 `NavigationStack` + `.searchable`，配合 `ContentUnavailableView` 原生空狀態；
- **動態來源統計**：搜索文案與個人中心來源統計全面改為 `VideoSourceCatalog.allSources.count` 運行時動態生成，杜絕硬編碼。

---

## 三、真機/模擬器實測截圖存檔

所有截屏均在 `iPhone 17 Pro`（iOS 26.4 模擬器，UUID: `85A543BE-30D6-41D8-8898-4FC2FE05872B`）實測截取落盤：

1. **首頁動態磨砂頂欄與雙列推薦**：`captures/screen_ios_native_homescreen_scrolled_blur.png`
2. **首頁 4:5 豎版海報與品牌標識**：`captures/screen_ios_native_homescreen_fixed_title.png`
3. **漫畫列表頁（ComicsScreen）**：`captures/screen_ios_phase2_comics.png`
4. **直播主播頁（LiveScreen）**：`captures/screen_ios_phase2_live.png`
5. **論壇分段與卡片流（ForumScreen）**：`captures/screen_ios_phase2_forum.png`
6. **個人中心分組列表（PersonalScreen）**：`captures/screen_ios_phase2_profile.png`
7. **全屏原生硬解播放器（NativePlayerView）**：`captures/screen_ios_phase3_player.png`
8. **WebGL 遊戲原生膠囊（GameContainerView）**：`captures/screen_ios_phase3_game.png`
9. **原生全網搜索（SearchSheetView）**：`captures/screen_ios_phase4_search.png`

---

## 四、第 9 節回歸紅線核驗清單

- [x] **紅線 1：真實視頻源直鏈播放** —— 91動漫直簽秒開，未受任何破壞；
- [x] **紅線 2：CDN 鑒權頭注入** —— `AVURLAssetHTTPHeaderFieldsKey` 完整保留；
- [x] **紅線 3：直播直簽 LL-HLS** —— Chaturbate 官方 API 直簽硬解秒播，未退回 WebView；
- [x] **紅線 4：本機劫持片源禁絕** —— 項目中未引入任何本地 `intro_film.mp4`；
- [x] **紅線 5：漫畫換章歸頂** —— `proxy.scrollTo("top_anchor", anchor: .top)` 已在 `ComicReaderView` 完整驗證；
- [x] **紅線 6：彈幕單行與防遮擋** —— `lineLimit(1)` + `frame(maxWidth: 320)` 嚴格生效；
- [x] **紅線 7：Swift 6 並發安全** —— 保持並發模型健全，編譯無 Data Race 告警；
- [x] **紅線 8：構建產物路徑** —— 顯式指定 `-derivedDataPath ios/build`，杜絕舊包殘留。

---

## 五、驗收結論

極光視界（AuroraShelf）iOS 端原生重構任務已**全面達標，符合 Apple HIG 最高設計基準，允許正式合入主分支並交付發布**。
