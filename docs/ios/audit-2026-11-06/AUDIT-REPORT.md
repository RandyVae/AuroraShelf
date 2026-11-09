# iOS 2.16.0 缺陷審計報告

**審計對象**：`ios/AuroraShelf`（Netgrey / AuroraShelf iOS 原生 SwiftUI 客戶端；Swift 6、deployment target iOS 26.4、Xcode 27.2）
**審計日期**：2026年11月6日 15:35–15:52 HKT
**審計性質**：只讀審計。未修改任何源碼、未提交任何改動；唯一寫入為本目錄下的報告與證據。
**基線**：工作樹正在另一會話手中變動，全部結論按時刻＋指紋綁定（見第 2.3 節）。最終驗證於 **15:47:46 建置成功、15:49 全量測試 106 項**（指紋 `9f648b9d`）。
**參與**：Lead（建置／測試／運行時／交付包／跨層）＋ A 線（資料層 20 項）＋ B 線（UI 層 30 項）＋ C 線（服務／配置／測試 27 項）

---

## 0. 摘要

**總判斷：功能面已相當完整，審計期間被本地化改動打斷過一次建置（已於 15:47:46 修復並通過全量測試），但以下三件事仍未解決——播放器三條主路徑有 P1 缺陷、測試完全不在版本控制內、交付包與源碼不同源。在完成第 6 節前 3 項之前，不建議出包。**

| # | 結論 | 嚴重度 | 等級 |
|---|---|---|---|
| 1 | **審計期間工作樹兩度不可建置**（15:41、15:47），根因：本地化層 `@MainActor` 與 nonisolated 全域宣告衝突（6 個 Swift 6 併發錯誤）＋新檔未入 target。**已於 15:47:46 修復**（改 NSLock＋`nonisolated func localized`，`xcodegen` 重生成），15:49 全量測試通過 | P0（已解除） | E1／E2 |
| 2 | **播放器解碼失敗時錯誤／重試界面被永久遮蔽**（`if let player` 在前），用戶只見黑屏、無出路；直播同源 | **P1** | E3 |
| 3 | **全工程 0 處 `isIdleTimerDisabled`**：播放與直播期間螢幕自動鎖定，播到一半黑屏 | **P1** | E1 |
| 4 | **分頁請求失敗即永久鎖死**：`canLoadMore=false` 且不寫錯誤，UI 顯示「已加載全部內容」，一次網絡抖動令該源永久只剩第一頁 | **P1** | E3 |
| 5 | **協議憑據隨 App bundle／IPA 分發**（5 個金鑰明文，實測在每份產物內），且無輪換機制 | **P1** | E1 |
| 6 | **全部 9 個測試檔與 29 個源碼檔未入 Git**：任何人 clone 都拿不到測試，「全綠」不可被他人重現 | **P1** | E1 |
| 7 | 交付包與源碼不同源：`dist/` IPA 為 build 173、不含憑據與 12 套在地化資源；`PROJECT_STATE.md` 記載的 IPA 雜湊與實物不符 | P2 | E1 |

**發現總量**：三線原始 77 項，去重後 76 項，加 Lead 直接發現 2 項，**合計 78 項**——P0 × 1（已修復）、P1 × 13、P2 × 47、P3 × 17；扣除審計期間已修復 2 項（C-25 建置、C-26 專案漂移），**現存待處理 76 項（P1 × 12、P2 × 47、P3 × 17）**；另 1 項 P1 流程風險（並行寫入）。

**已驗證的正面前提**（不是只報壞消息）：
- 15:49 全量測試 **106 項：101 passed／5 skipped／0 failed**（當前樹）。
- 15:47:46 Debug 建置成功、0 error；Release 配置亦可建置。
- App 於 iPhone 17 Pro（iOS 26.4）模擬器啟動成功，首頁渲染真實內容（`evidence/smoke-launch.png`）。
- 12 語系資源 92 鍵 × 12 套**鍵集完全一致、無缺鍵、無 value==key**，且重新建置後**已打包進 bundle（實測 12 個 `.lproj`）**。
- `git diff --check` 乾淨；`@MainActor`／`actor`／任務去重／複合唯一鍵等 9 條修復聲明屬實（第 5 節）。

---

## 1. 項目進展快照

### 1.1 版本與規模（15:47 實測）

| 項目 | 值 | 依據 |
|---|---|---|
| 市場版本／建置號 | 2.16.0 / 174 | `ios/project.yml:23-24` |
| Swift 檔數量 | 63（審計開始 62，期間 +1） | `find` |
| 程式碼行數 | 13,905（含測試） | `wc -l` |
| 視頻源 | 19（華視、黃果、91視頻、91動漫、Beeg、XVideos、MissAV、KissJAV、85po、Pornhub、JAV-NONI、JAVSB、TokyoMotion、SpankBang、xHamster、EPORNER、MRDOUGA、OHentai、XNXX） | `VideoSourceCatalog.swift` |
| 漫畫源 | 10 | `ComicSourceCatalog.swift` |
| 直播源 | 3 | `LiveSourceCatalog.swift` |
| 論壇板塊 | 6（91 系 5 ＋ 草榴 1） | `ForumSourceCatalog.swift:62-67` |
| 在地化 | 12 套 `.lproj` × 92 鍵；23 個檔引用 `.localized`／`L10n`；仍有 45 處 `Text("中文")`、28 處 `Button("中文")` 未遷移 | 實測 |
| 未提交改動 | 已改 38 檔（+5,891／−1,671）；未追蹤 20 個 `.swift`（含全部測試）、12 套 `.lproj`；合計 74 項 | `git status` |

### 1.2 三條並行工作線

1. **19 視頻源全量接入**：`VideoRepository` 1,298 行；HTML 爬蟲、REST API、HMAC/XOR 簽名、AES-128 解密、華視兩段式取流（`/Player/next` → `/Player/resolveUrl`）。
2. **缺陷排查修復（已聲稱閉環）**：`@MainActor` 化倉儲、進度防抖落盤、圖片載入器 `actor`＋任務去重、輪詢 Task 生命週期、複合唯一鍵、正則預編譯。逐條核實見第 5 節（15 條中 8 條屬實、3 條部分屬實、4 條不屬實）。
3. **多語言在地化（審計期間落地）**：`LocalizationManager`（13 語言、12 資源）、`String.localized`／`L10n`、語言選擇器。**目前只遷移了一半**（見 R-11／B-27）。

### 1.3 交付物

| 交付物 | 實測 | 與源碼一致性 |
|---|---|---|
| `dist/AuroraShelf-2.16.0-173-arm64-LOCAL.ipa` | 171,596,136 bytes；SHA-256 `2d905a9b…f799`；`CFBundleVersion 173` | **不同源**：無 `SourceSecrets.plist`、無 `.lproj`、無 AppIcon；版本落後工作樹 |
| 文檔記載 | `PROJECT_STATE.md:76,80` 寫 `7baa8512…`（同段重複兩次） | **不符**：實測 `2d905a9b…`（與 `dist/SHA256SUMS.txt` 一致） |
| App bundle | 219 MB，其中 `Resources/www` 203 MB（1,260 檔，佔 93%） | — |

---

## 2. 方法、證據與基線

### 2.1 分工

| 線 | 範圍 | 產出 | 數量 |
|---|---|---|---|
| Lead | 建置、測試、Release、模擬器運行時、交付包、跨層與流程 | 本報告＋`evidence/` | 2 項 + 1 流程風險 |
| A | `Data/**`（13 檔、5,058 行）、`Services/HTMLVideoSourceAdapter.swift` | [part-a-data.md](part-a-data.md) | 20（P1×2、P2×14、P3×4） |
| B | `UI/**`（33 檔、6,005 行） | [part-b-ui.md](part-b-ui.md) | 30（P1×4、P2×19、P3×7） |
| C | 其餘 `Services/**`、`App/**`、`Models/**`、`Info.plist`、`project.yml`、`Tests/**` | [part-c-infra.md](part-c-infra.md) | 27（P0×1、P1×7、P2×13、P3×6） |

### 2.2 證據等級

- **E1 實測**：本機真實執行過的命令、建置、測試、運行時日誌、檔案雜湊、截圖。
- **E2 編譯器／測試**：由編譯器診斷或測試結果直接得出。
- **E3 靜態代碼**：逐行閱讀並引用原文；行為結論需運行才能確認者標「需運行驗證」。
- **E4 推斷**：由以上組合推理，未直接觀測。

**B 線的對比度數值（B-10/11/12）與實機量測項（B-16/17）由 B 線計算，Lead 未逐項複核**；其餘 P1 條目均由 Lead 直接驗證代碼。

### 2.3 基線漂移（必須連同結論引用）

審計期間另一會話持續改同一工作樹：

| 時刻 | 事件 | 對本報告的影響 |
|---|---|---|
| 15:36 | 建置＋101 項測試通過 | 對應「本地化改動前」的樹，非現狀 |
| 15:37 | 12 套 `.lproj` 落地 | 15:41 曾記錄只有 8 套，15:43 已補齊 → **該缺陷作廢，不列入清單** |
| 15:39 | `LocalizationManager` 新建、`TabItem` 改 `.localized` | 引入 P0 |
| 15:41 | 建置失敗（檔未入 target ＋ 6 併發錯誤） | P0 首證 |
| 15:45 | 重新 `xcodegen`，`pbxproj` 收錄新檔與 12 個 `.lproj` | C-26 自行修復 |
| 15:47:46 | **建置成功**（改 NSLock＋`nonisolated`） | P0 解除 |
| 15:49 | **全量測試 106 項：101 passed／5 skipped／0 failed** | 最終基線 |

**含意**：① 任何「已修復」只在對應時刻成立；② 發布前必須在**凍結樹**（commit 或 worktree）上重跑第 8.2 節命令；③ 兩方同時寫同一批檔案，後寫者可能覆蓋前者（本項目 10 月已發生整段首頁代碼被 `git checkout` 抹除，見 `PROJECT_STATE.md:157`）。

---

## 3. 主審實測結果

### 3.1 建置與測試

| 時刻 | 對象 | 結果 |
|---|---|---|
| 15:36 | `xcodebuild test`（本地化前） | **PASS**：101 項，96 passed／5 skipped／0 failed（`evidence/test-summary.json`） |
| 15:37 | 乾淨 `build-for-testing` | **PASS**：0 error；app-target 25 條警告、test-target 8 條（`evidence/compile-diagnostics-debug.txt`） |
| 15:38 | `build -configuration Release` | **PASS**：`BUILD SUCCEEDED`，0 error、48 條 app-target 警告 |
| 15:41 | 本地化落地後 | **FAIL**：exit 65、10 error |
| 15:42 | 凍結快照＋補跑 `xcodegen` | **FAIL**：exit 65、6 error（證明非單一 target 成員問題） |
| 15:47 | 同一失效模式再現 | **FAIL**：exit 65（`evidence/build-failure-1541.txt`） |
| **15:47:46** | **當前樹（修復後）** | **PASS**：`BUILD SUCCEEDED`，0 error（`evidence/build-success-1547.txt`） |
| **15:49** | **當前樹全量測試** | **PASS**：106 項，101 passed／5 skipped／0 failed（`evidence/test-summary-1549.json`） |

修復後果：`LocalizationManager` 不再標 `@MainActor`，改用 `NSLock`＋`locked*` 私有狀態，`localized(_:)` 標為 `nonisolated`；`pbxproj` 由 `xcodegen` 重新生成，12 個 `.lproj` 已進入 Resources phase。

### 3.2 模擬器運行時（E1）

- 15:39:40，iPhone 17 Pro（iOS 26.4，PID 75226）安裝並啟動 15:36 建置版：**成功**，首頁呈現真實內容、19 源頻道架、Tab 列正常（`evidence/smoke-launch.png`）。
- 系統日誌檢出 **2 個不存在的 SF Symbol**，`bolt.horizontal.tv.fill` 由 SwiftUI 直接報 fault（4 次，`evidence/runtime-symbol-faults.txt`）。
- 未檢出 `Publishing changes from background threads`／`Modifying state during view update`，無新增崩潰報告。
- **未完成**：卡片點擊→播放器出畫的完整 UI E2E（GUI 自動化本機 bootstrap 超時）；播放、翻頁、聊天室注入仍屬「需運行驗證」（B 線列 15 項 V-1～V-15）。

### 3.3 交付包（E1）

```
size   171,596,136 bytes
sha256 2d905a9b8bf3786789dbb1e574b9be061de08de20a505e13faab98502646f799
Info.plist  CFBundleShortVersionString 2.16.0 / CFBundleVersion 173
lproj 0 項；SourceSecrets.plist 0 項；AppIcon 0 項
```

對照：15:47:46 由當前源碼重新建置的產物含 **12 個 `.lproj`** 與 `SourceSecrets.plist` → 交付包確實落後於源碼。

---

## 4. 缺陷清單

### 4.1 P0（1 項，已修復）

#### R-01 工作樹不可建置（＝C-25／L-01／L-02）【E1／E2｜**15:47:46 已修復**】

**症狀**：15:41 與 15:47 兩次 `xcodebuild` exit 65；失敗時 0 error 之外共 10／6 個編譯錯誤，App 無法產出。

**證據**：`Services/LocalizationManager.swift` 原以 `@MainActor` 標註，但 `L10n` 全域函式與 `String.localized` 擴展皆為 nonisolated，同步讀取 `LocalizationManager.shared` → 編譯器報 `main actor-isolated static property 'shared' can not be referenced from a nonisolated context`（3 處）與 `call to main actor-isolated instance method 'localized' in a synchronous nonisolated context`（3 處）。呼叫點擴散至 21 檔（含 `Data/**`）。

**根因**：把「全域可用的字串查表」建在 `@MainActor` 單例上；`String` 擴展無法隔離。

**已採用的修法（實測有效）**：`LocalizationManager` 去 `@MainActor` → `final class ... ObservableObject, @unchecked Sendable`，狀態以 `NSLock` 保護，查表函式標 `nonisolated`。

**殘留風險（列為流程項 R-16）**：此失效在半小時內發生兩次，且修復未在凍結樹上完成；`@unchecked Sendable` 的正確性依賴日後所有寫入都走鎖——建議補一條「非主線程呼叫 `localized`」的單元測試釘住。

### 4.2 P1（13 項，其中 C-26 已自行修復）

| ID | 原始 | 位置 | 症狀 | 等級 |
|---|---|---|---|---|
| R-02 | B-02 | `NativePlayerView.swift:57,94,459-462`；`LivePlayerView.swift:41,78,289-292` | 解碼失敗只設 `resolveError`，但錯誤／重試界面在 `else if` 分支；`player` 非 nil 時永不顯示 → 黑屏無出路 | E3 |
| R-03 | B-03 | 全工程（grep 0 命中） | 無 `isIdleTimerDisabled`：自訂 AVPlayerLayer 不延長點亮，30 秒後鎖屏中斷播放 | E1 |
| R-04 | B-01 | `NativePlayerView.swift:48-50,320-332,595-602` | 手動 90° 旋轉（`rotationEffect`）與已宣告的原生橫屏並存；設備已橫放時再按「橫屏沉浸」即雙重旋轉、畫面側躺 | E3（視覺需 V-1） |
| R-05 | B-04 | `GameContainerView.swift:58-91` | 遊戲唯一返回膠囊可拖出屏幕（`offset` 無夾取）且 3 秒自動淡出；全屏無其他退出 → 用戶被困 | E3 |
| R-06 | A-01 | `VideoRepository.swift:166-169`；`ComicRepository.swift:144-147` | 下一頁失敗即 `canLoadMore=false` 且不寫錯誤；UI（`HomeScreen:188`、`ComicsScreen:290`）顯示「已加載全部內容」 | E3 |
| R-07 | A-02 | `ForumSourceCatalog.swift:115,388-392` | 標題含內嵌標籤即整條丟棄；全數丟棄時 `posts=[]`、`errorMessage=nil` → 空白列表無錯誤 | E3 |
| R-08 | C-04 | `NativePlayerView.swift:445-446`；`LiveVideoSourceAuditTests.swift:119-156` | 生產路徑零直鏈校驗；`text/html`＋`#EXTM3U` 檢查只存在於預設被 skip 的測試 → HTML 錯誤頁被當 m3u8 | E3 |
| R-09 | C-01 | `project.yml:11-17`；`Resources/SourceSecrets.plist` | 5 個協議金鑰明文隨 bundle／IPA 分發（實測每份產物皆含），無輪換、無按用戶派生；且測試 C-14 反向固化此設計 | E1 |
| R-10 | C-03 | `git ls-files` | 62→63 個 `.swift` 中 20+ 個未追蹤，**9 個測試檔全部未追蹤**：clone 後零測試 | E1 |
| R-11 | L-04／B-27 | `LocalizationManager.swift:116-133`；14 個 UI 檔；`PersonalScreen.swift:406` | 本地化只鋪一半：45 處 `Text("中文")`＋28 處 `Button("中文")` 未遷移；14 個讀 `.localized` 的檔不訂閱語言管理器 → 切換語言僅部分生效 | E3 |
| R-12 | L-05／B-29 | `ChannelsShelf.swift:118`；`ComicsScreen.swift:161` | 無效 SF Symbol：`bolt.horizontal.tv.fill`（運行時 fault，Beeg 圖標空白）、`person.crop.circle.badge.key`（目錄查無） | E1／E2 |
| R-13 | C-02 | `Info.plist:40-44` | `NSAllowsArbitraryLoads=true` 無域名白名單：所有明文 HTTP 放行 | E1 |
| R-14 | C-26 | `project.yml` vs `pbxproj` | 專案檔與 spec 漂移（**.lproj 從未打包、新檔未入 target**）；**15:45 已由並行會話修復**，重建後 12 個 `.lproj` 已進 bundle | E1 |

### 4.3 P2（47 項，摘要）

| 領域 | 條目 |
|---|---|
| 資料層（A） | A-03 解析全在主線程（`nonisolated` 不切執行緒）／A-04 正則每次重編譯／A-05≡C-11 播放紀錄無上限＋主線程全量編碼／A-06 單筆解碼失敗清空全部歷史／A-07 `StreamCache` 無上限無 TTL／A-08 伺服器可控 `Task.sleep`／A-09 HTML 源每頁截斷 24／A-10 草榴截斷 20（本輪新增）／A-11 `LiveRepository` `@unchecked Sendable`／A-12 單 tag 失敗丟整批／A-13 密鑰明文＋iOS 不可達回退分支／A-14 封面以全頁索引對齊→錯位／A-15 JM 空 series 偽造 albumId／A-16 Keychain 失敗被忽略 |
| UI（B） | B-05 音訊會話只設不還原／B-06 0.5s 觀察者驅動整樹重建＋進度每 3 秒寫 2 次／B-07 Slider 每次值變更即 seek／B-08 單擊與雙擊手勢衝突／B-09 sheet→fullScreenCover 同輪切換／B-10 品牌色當文字 3.15:1／B-11 `brand` 文字 2.92:1／B-12 `textTertiary` 4.38:1 用於 caption／B-13 11 處固定 pt 無視動態字級（最低 10pt）／B-14 硬編碼「4K HDR／Dolby 5.1」失實規格／B-15 `UIScreen.main.bounds` 判佈局（iPad 分屏錯誤）／B-16 Top10 大數字溢出／B-17 46/12/42pt 寫死而非 `safeAreaInsets`／B-18 `LiveChatSheet` Timer 從不 invalidate／B-19 遊戲 www 載入失敗無提示／**B-28 片單數量計兩次，顯示「3 3 部」**／B-30 未支援的系統語言一律回退簡體中文 |
| 服務／配置／測試（C） | C-05 bundle 219 MB（203 MB 遊戲資源）／C-06 `UniversalVideoEngine` 名不符實／C-07 封面 actor 內同步 I/O／C-08 磁碟快取無界無 TTL 非 atomic／C-09 兩載入器 AES padding 假設不一致／C-10 漫畫頁零磁碟快取／C-12 預設測試即外呼真實站點／C-13 測試內重寫生產邏輯後斷言副本／C-14 把「憑據必須存在」寫成期望／C-15 測試改全域單例／C-16「引擎測試」39 個只有 4 個測引擎／C-17 載入器去重零行為測試／C-27 無 `PrivacyInfo.xcprivacy` |
| Lead | L-08 編譯警告未清零（Debug 25／Release 48，`NativePlayerView` 佔一半）／L-12 狀態文檔 IPA 雜湊與實物不符（同段重複兩次） |

### 4.4 P3（17 項，摘要）

A-17 寫入校驗恆真／A-18 e-hentai 雙重截斷／A-19 強制解包口徑不符（實測 3 處）／A-20 進度更新重置手動標記（產品語義待定）；
B-20 三處圖示鍵與 Slider 缺 `accessibilityLabel`／B-21 兩處 `withAnimation` 未判斷 `reduceMotion`／B-22 8 個 `Task {}` 無取消路徑／B-23 三處 WKWebView 無 `dismantle`／B-24 `ForEach(id: \.self)` 重複鍵／B-25 `--auto-play` 收起後重播／B-26 片單寫入失敗靜默吞掉；
C-18 `--video-source` 解析兩次／C-19 `CODE_SIGNING_ALLOWED=NO` 全配置／**C-20 無 `*.xcassets`、`AppIcon` 指向不存在的 asset → 主畫面無圖示（產物實測：無 icon 檔、`Info.plist` 無 `CFBundleIcons`）**／C-21 `CFBundleDevelopmentRegion=zh_CN` 與 12 語系並存／C-22 憑據回退至 cwd 相對路徑／C-23 快取鍵不含 Referer／C-24 AES 金鑰硬編碼、憑據存放方式不一致。

### 4.5 流程風險（1 項）

**R-16 並行會話同時寫同一工作樹（P1）**：15 分鐘內整樹指紋變化 4 次（`046271a7`→`20beb3f0`→`1153b951`→`9c653b40`→`9f648b9d`）；`.lproj` 8→12、`pbxproj` 15:45 被重生成、`TabItem` 15:39 被改寫、`PersonalScreen` 由 368 增至 441 行。後果：審計與測試結論保鮮期以分鐘計，且存在互相覆蓋風險。

**建議**：出包與審計一律在凍結樹（commit 或 `git worktree`）上進行；同一時間僅一個會話可寫 `ios/`；把「`xcodegen generate` 後 `git diff --exit-code project.pbxproj`」加入出包前檢查。

### 4.6 分佈統計

| 嚴重度 | 總數 | 期間已修復 | 現存 |
|---|---|---|---|
| P0 | 1 | 1（C-25） | 0 |
| P1 | 13 | 1（C-26） | 12 |
| P2 | 47 | 0 | 47 |
| P3 | 17 | 0 | 17 |
| **合計** | **78** | **2** | **76** |

另計 1 項流程風險（R-16）。

---

## 5. 對「本輪已修復 14／12 項」聲明的核實

| # | 聲明 | 判定 | 證據 |
|---|---|---|---|
| 1 | `NativePlayerView` seek 回調切回主線程 | 部分屬實 | `DispatchQueue.main.async` 包覆屬實（`:451,:470`）；但時間觀察者閉包直接改 `@State`（`:477-494`）仍是跨隔離 |
| 2 | `VideoRepository` 升 `@MainActor`、拔脫軌 Task、純解析 `nonisolated` | 部分屬實 | class 已標（`:38`）、方法已標；但 `nonisolated` 不切執行緒，解析仍主線程（A-03），`LiveRepository` 仍 `@unchecked Sendable`（A-11） |
| 3 | `PlaybackProgressStore` 5s 防抖＋10s 節流＋flush 兜底 | 屬實（存在性） | `:159-173`、`:151-157`；「杜絕阻塞主線程」誇大：`persistToDisk` 仍主線程全量編碼 |
| 4 | 各源硬編碼截斷清零 | **不屬實** | `ForumSourceCatalog:181` `.prefix(20)`（本輪新增）、`HTMLVideoSourceAdapter:261` `== 24`、`ComicRepository:543` `prefix(1_000)` |
| 5 | 兩個圖片載入器升 actor＋任務去重 | 屬實 | `CoverImageLoader:6`／`ComicPageImageLoader:5`；`inFlightTasks` 各 4 處，失敗有 `defer` 清理 |
| 6 | `MainContainerView` 輪詢 Task 生命週期 | 屬實 | `:117-158`：`onAppear` 先 cancel、`onDisappear` 取消置 nil |
| 7 | `SearchSheetView` 改 `@ObservedObject` | 屬實 | `:7` |
| 8 | `ForumScreen` 複合唯一鍵 | 屬實 | `:89`＋`ForumSourceCatalog:20` |
| 9 | 正則全部靜態預編譯 | **不屬實** | 執行期 `try? NSRegularExpression` 共 50 處；`static let` 僅 3 個 |
| 10 | 全量 96 項測試通過 | 屬實（當時） | 15:36：96/5/0；**現況已增至 106 項、101 passed** |
| 11 | `git diff --check` 0 警告 | 屬實 | exit 0 |
| 12 | 模擬器截屏存盤 | 屬實 | `captures/screen_ios_tab_home_after_bugfix.png`（1.98 MB） |
| 13 | 「BUILD SUCCEEDED，0 錯誤，0 警告」 | **不屬實（現狀）** | 25（Debug）／48（Release）條 app-target 警告；15:41–15:47 曾失敗 |
| 14 | 交付包 SHA-256 | **不屬實** | 文檔 `7baa8512…` vs 實測 `2d905a9b…` |
| 15 | 橫屏「完美充滿、無黑邊」 | **不成立（功能層面）** | 旋轉與留白為硬編碼常數（B-17），且與系統旋轉衝突（R-04） |

**小結**：15 條中 8 條屬實、3 條部分屬實、4 條不屬實。不屬實項集中在「建置狀態」「截斷」「交付包」「橫屏效果」——正是發布與對外聲明最依賴的四項。

---

## 6. 處置順序（按依賴關係）

### 先做（今日）
1. **R-10**：把 20+ 個未追蹤 `.swift`（含 9 個測試檔）與 12 套 `.lproj` 納入 Git，取一個固定提交作基準。
2. **R-16**：凍結工作樹再出包；同一時間僅一個會話寫 `ios/`；出包前跑 `xcodegen generate` 並核對 `pbxproj` 無 diff。
3. 在凍結樹上重跑第 8.2 節命令，把「建置成功＋106 項測試」寫成發布前置證據。

### 本輪（用戶可見）
4. **R-02**：錯誤／重試層移出 `if let player`；`failed` 時 `player = nil` 強制走錯誤層（兩處播放器）。
5. **R-03**：進入播放器設 `UIApplication.shared.isIdleTimerDisabled = true`，退出成對還原。
6. **R-06／R-07**：分頁失敗保留 `canLoadMore=true` 並顯示錯誤＋重試；解析全數失敗時報錯而非空白。
7. **R-12**：修正兩個 SF Symbol；加 `UIImage(systemName:)` 存在性測試覆蓋 8 個頻道映射。
8. **R-04／R-05**：旋轉改單一來源（刪手動旋轉或鎖定方向）；返回膠囊加邊界夾取＋常駐退出。
9. **B-28**：`PersonalScreen:170` 改為 `"watchlist.count".localized(count)`（一行）。

### 短期
10. **R-08**：`AVURLAsset` 前加 Content-Type／`#EXTM3U` 校驗，並從被 skip 的測試搬進生產路徑。
11. **R-09**：明示「憑據隨包分發」為例外並規劃輪換；同步修正 C-14（測試反向固化）。
12. **R-11**：語言以 `@Environment`／根視圖 `id(language)` 全 App 生效；補完 45＋28 處硬編碼字串。
13. **L-08**：`NativePlayerView:477-494` 改主線程安全寫法，目標 app-target 警告 ≤ 5。
14. **C-20**：補 `Assets.xcassets`（AppIcon 缺失＝主畫面空白圖示）。

### 發布前
15. 重建交付包至 build 174，核對 `SHA256SUMS.txt` 與 `PROJECT_STATE.md`（L-12）。
16. 決定分發渠道：側載 → 在 `DECISIONS.md` 記錄 ATS／隱私清單例外；上架 → 補 `PrivacyInfo.xcprivacy`、收窄 ATS（R-13／C-27）。
17. 評估 203 MB 遊戲資源改按需下載（C-05）。
18. 補跑逐源實測（`RUN_IOS_LIVE_SOURCE_AUDIT=1`）並存檔——19 源可用性目前無自動化證據。

---

## 7. 侷限

1. **無真機證據**：全部運行時觀測來自 iPhone 17 Pro（iOS 26.4）模擬器。
2. **無端到端播放走查**：GUI 自動化 bootstrap 超時；播放器出畫、漫畫翻頁、直播聊天室注入、旋轉雙重效果（V-1）均屬「需運行驗證」。
3. **無在線逐源實測**：5 項逐源測試預設 skip；本輪受建置反覆阻塞未補跑。
4. **無效能剖析**：記憶體峰值、主線程卡頓、啟動耗時未以 Instruments 量測。
5. **無無障礙人工走查**：VoiceOver／Switch Control／200% 字級僅靜態檢查。
6. **行號會漂移**：見 2.3 節。
7. **成人內容分發與第三方站點條款**不在技術審計範圍。

---

## 8. 附錄

### 8.1 檔案

| 檔案 | 內容 |
|---|---|
| [AUDIT-REPORT.md](AUDIT-REPORT.md) | 本報告 |
| [part-a-data.md](part-a-data.md) | A 線：資料層與網絡適配器（926 行，20 項） |
| [part-b-ui.md](part-b-ui.md) | B 線：UI 層與播放器生命週期（30 項） |
| [part-c-infra.md](part-c-infra.md) | C 線：服務／配置／安全／測試（1,129 行，27 項） |
| `evidence/test-summary.json` | 15:36：101 項（96/5/0） |
| `evidence/test-summary-1549.json` | 15:49：106 項（101/5/0） |
| `evidence/compile-diagnostics-debug.txt` | 33 條編譯診斷（25 app＋8 test） |
| `evidence/build-failure-1541.txt` | 建置失敗原文 |
| `evidence/build-success-1547.txt` | 15:47:46 建置成功 |
| `evidence/runtime-symbol-faults.txt` | 運行時 SF Symbol fault |
| `evidence/smoke-launch.png` | 模擬器啟動截圖 |

### 8.2 重跑命令

```bash
cd /Users/itou/Documents/ChatGPT/91pron

# 0) 先凍結基準
git rev-parse HEAD; git status --porcelain | wc -l

# 1) 專案檔必須與 spec 同步（否則新檔／語系不參與建置）
cd ios && xcodegen generate && cd ..
git diff --exit-code ios/AuroraShelf.xcodeproj/project.pbxproj   # 應無差異

# 2) 建置與測試
xcodebuild test -project ios/AuroraShelf.xcodeproj -scheme AuroraShelf \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -derivedDataPath ios/build.nosync/audit-derived \
  -resultBundlePath ios/build.nosync/audit-recheck.xcresult
xcrun xcresulttool get test-results summary --path ios/build.nosync/audit-recheck.xcresult

# 3) 警告計數（app target）
xcodebuild build -project ios/AuroraShelf.xcodeproj -scheme AuroraShelf \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -derivedDataPath /tmp/recheck 2>&1 \
  | grep -cE 'ios/AuroraShelf/[^:]+:[0-9]+:[0-9]+: warning:'

# 4) 逐源實測（需可建置）
RUN_IOS_LIVE_SOURCE_AUDIT=1 xcodebuild test -project ios/AuroraShelf.xcodeproj \
  -scheme AuroraShelf -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -only-testing:AuroraShelfTests/LiveVideoSourceAuditTests

# 5) 交付包核驗
shasum -a 256 dist/*.ipa
unzip -l dist/<ipa> | grep -c lproj
```

### 8.3 判定口徑

- **P0** 崩潰／不可建置／資料損毀／安全漏洞／阻斷交付；**P1** 用戶可見功能缺陷或證據鏈斷裂；**P2** 健壯性、效能、可維護性、測試真實性；**P3** 打磨與一致性。
- 本報告只報「會壞、會慢、會洩漏、會困住用戶、會令證據失效」的問題，不報風格偏好。
- 每項發現均可回溯到 `part-*.md` 的完整論證（含代碼引用與反例）。

---

**報告完結。**
