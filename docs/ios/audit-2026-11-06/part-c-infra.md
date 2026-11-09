# C 線審計：服務層、配置、安全與測試覆蓋

審計對象：iOS 原生 App（`ios/` 工作樹，未固定提交）
審計日期：2026年11月6日
審計方式：純靜態源碼與配置閱讀；未執行 `xcodebuild`、未啟動模擬器、未運行測試
審計員：C 線（audit-infra）

## 0. 審計基準（工作樹持續變動，結論以此為界）

本次為**非凍結工作樹上的滾動審計**。審計期間另一會話持續寫入源碼，故必須固定基準：

| 項目 | 值 |
|---|---|
| 主要讀取時間窗 | 2026-11-06 15:37:25 – 15:43:37（UTC+8） |
| 基準時刻（本報告數值採樣終點） | **2026-11-06 15:43:37 +0800** |
| `git rev-parse HEAD` | `78e7d13572fc265f17602690e1732703f6a20ce2` |
| `git write-tree`（索引樹指紋） | `6d22b384a5b2…`（注意：此指紋**只含已追蹤檔案**，29 個未追蹤 Swift 不在其中，故對工作樹漂移的表達力有限） |
| Lead 另一時點記錄之樹指紋 | `046271a7…`（15:36／15:41 兩次建置取樣時點；與本報告採樣終點不同，不可互換引用） |
| `git status --short` 變動項數 | 74 |
| `ios/` 內 Swift 檔 | 62（已追蹤 33、未追蹤 29） |

**審計期間已觀測到的樹變動**（本人親自量測，非傳聞）：

1. `Services/LocalizationManager.swift` 於本輪首次列檔（15:37）時**並不存在**，15:38 後出現，現為未追蹤檔（`git status` 顯示 `??`）。
2. `Models/TabItem.swift` 於本輪初次閱讀（約 15:38）時 `title` 為硬編碼中文字串（`"主页"` 等）；採樣終點（15:43）已改為 `"tab.home".localized` 形式（現行 `TabItem.swift:12-20`）。**§6 第 12 項之「無缺陷」判定僅適用於 15:38 版本，不適用於現行樹。**
3. `Resources/*.lproj` 於 15:37 已存在 12 個目錄；採樣終點仍為 12 個（未增未減）。

**建置結論由 Lead 提供、本人未執行**：15:36 版 BUILD OK、96 tests passed／5 skipped；**15:41 版 BUILD FAILED**（原因見 C-25、C-26）。凡引用此二結果，文中均標明「Lead 實測」。

---

## 1. 範圍與方法（逐檔覆蓋清單）

### 1.1 逐檔全讀（本人完整閱讀，非抽樣）

| # | 檔案 | 行數 | 狀態 |
|---|---|---|---|
| 1 | `ios/AuroraShelf/Services/CoverImageLoader.swift` | 227 | 全讀 |
| 2 | `ios/AuroraShelf/Services/ComicPageImageLoader.swift` | 176 | 全讀 |
| 3 | `ios/AuroraShelf/Services/UniversalVideoEngine.swift` | 206 | 全讀 |
| 4 | `ios/AuroraShelf/App/AuroraShelfApp.swift` | 20 | 全讀 |
| 5 | `ios/AuroraShelf/App/PlayerCoordinator.swift` | 21 | 全讀 |
| 6 | `ios/AuroraShelf/Models/TabItem.swift` | 41 | 全讀 |
| 7 | `ios/AuroraShelf/Resources/Info.plist` | 46 | 全讀 |
| 8 | `ios/project.yml` | 43 | 全讀 |
| 9 | `.gitignore` | 16 | 全讀 |
| 10 | `ios/SourceSecrets.plist.example` | 16 | 全讀 |
| 11 | `ios/AuroraShelfTests/ComicRepositoryTests.swift` | 555 | 全讀 |
| 12 | `ios/AuroraShelfTests/UniversalVideoEngineTests.swift` | 590 | 全讀（分段） |
| 13 | `ios/AuroraShelfTests/CoverImageLoaderTests.swift` | 31 | 全讀 |
| 14 | `ios/AuroraShelfTests/PlaybackProgressStoreTests.swift` | 94 | 全讀 |
| 15 | `ios/AuroraShelfTests/WatchlistStoreTests.swift` | 31 | 全讀 |
| 16 | `ios/AuroraShelfTests/LiveVideoSourceAuditTests.swift` | 156 | 全讀（重點段） |
| 17 | `ios/AuroraShelfTests/ForumRepositoryTests.swift` | 279 | 全讀（重點段） |
| 18 | `ios/AuroraShelfTests/LiveSourceAdapterTests.swift` | 64 | 全讀（清單＋閘門段） |
| 19 | `ios/AuroraShelfTests/DeviceLayoutTests.swift` | 80 | 全讀（清單） |

涉及但只作取證式閱讀（非本人主責範圍，僅為判定跨檔行為）：`Data/Video/VideoRepository.swift`（`@MainActor` 與啟動段）、`Data/Video/WatchlistStore.swift`、`Data/Video/PlaybackProgressStore.swift`、`Data/Comic/ComicProtocolSecrets.swift`、`Data/Comic/ComicSourceCatalog.swift`（`ComicPage` 識別鍵）、`UI/Screens/NativePlayerView.swift`（播放器生命週期與直鏈交付點）、`UI/Forum/ForumPostDetailView.swift`、`UI/Components/AuroraCoverImage.swift`（載入器呼叫端）。

### 1.2 方法與工具

- `read`：逐檔全文閱讀，引述行號以工作樹現行版本為準。
- `grep`：掃 `@preconcurrency`、`nonisolated(unsafe)`、`@unchecked Sendable`、`Data(contentsOf:)`、`write(to:)`、`evaluateJavaScript`、`XCTSkip`、`print(`、`NSRegularExpression(pattern:` 等。
- `python3`／`plutil` 等價手段：以 `plistlib` 讀取 `SourceSecrets.plist` 鍵名與**值長度**（不輸出任何密文值），驗證建置產物內是否含該檔；以 `SDKSettings.plist` 讀取 SDK 版本與部署目標下限。
- `git ls-files` / `git status`：核實檔案納入版本控制情況。
- `du`：量測 bundle 與資源體積。

### 1.3 邊界聲明

- `ios/AuroraShelf/Services/HTMLVideoSourceAdapter.swift`（493 行）依分工屬 A 線，本人未審其內部邏輯；僅在「被 C 線檔案呼叫／被 C 線測試覆蓋」時引用其行號作交叉證據，並在文中標明。
- `WatchlistStore`、`PlaybackProgressStore`、`ComicRepository`、`ForumSourceCatalog`、`LiveSourceCatalog` 的業務正確性屬 A 線；本人只就題目指定之**持久化原子性與主線程 I/O** 取證。
- 全部結論皆為靜態推論。凡需實機或運行方能確認者，已標「需運行驗證」並集中於第 7 節。
- **範圍追加**：`ios/AuroraShelf/Services/LocalizationManager.swift`（審計期間新落地，非原定 C 線範圍）因直接造成建置阻斷，已納入並以 C-25 處理；其業務正確性（語系解析優先序等）未全面審。

---

## 2. 發現總覽表

共 27 項：**P0 × 1、P1 × 7、P2 × 13、P3 × 6**。

> C-25～C-27 為採樣終點前後新落地之變更所引出的問題（本地化改造），屬「當下正在發生的破口」，優先級按實際阻斷面判定。

| ID | 嚴重度 | 檔案:行 | 症狀 |
|---|---|---|---|
| C-01 | **P0** | `ios/project.yml:11-17`；`ios/AuroraShelf.xcodeproj/project.pbxproj:63,484`；`ios/AuroraShelf/Resources/SourceSecrets.plist` | 真實憑據檔被打包進 App bundle，隨產物分發 |
| C-02 | P1 | `ios/AuroraShelf/Resources/Info.plist:40-44` | ATS 全面關閉（`NSAllowsArbitraryLoads=true`），無任何域名白名單 |
| C-03 | P1 | `git ls-files` 結果；`ios/AuroraShelfTests/*` | 62 個 Swift 中 29 個未納入 git，**全部 9 個測試檔皆未追蹤** |
| C-04 | P1 | `ios/AuroraShelf/UI/Screens/NativePlayerView.swift:445-446`；`ios/AuroraShelfTests/LiveVideoSourceAuditTests.swift:119-156` | 生產路徑無直鏈內容校驗；唯一校驗只存在於預設被跳過的測試 |
| C-05 | P1 | `ios/AuroraShelf/Resources/www`（symlink）；`ios/project.yml:15-17` | Bundle 219 MB，其中 203 MB 為 symlink 指入 Android 資產樹 |
| C-06 | P1 | `ios/AuroraShelf/Services/UniversalVideoEngine.swift:28-206` | 檔名所指之「播放解析／超時／重試／直鏈校驗」在此檔完全不存在；網絡錯誤未分類 |
| C-07 | P2 | `CoverImageLoader.swift:48,95-103` | actor 內同步 `Data(contentsOf:)` 與解碼，全域封面載入序列化 |
| C-08 | P2 | `CoverImageLoader.swift:27-28,88` | 磁碟快取無界、無 TTL/LRU、與 192 MB `URLCache` 重複、寫入非 atomic、快取策略永不重驗 |
| C-09 | P2 | `CoverImageLoader.swift:211` vs `ComicPageImageLoader.swift:135` | 同一 AES 密文，兩載入器 padding 假設不一致（一處可能解不開） |
| C-10 | P2 | `ComicPageImageLoader.swift:13-18,90-104` | 漫畫頁完全無磁碟快取；記憶體上限 32 張／80 MB，記憶體警告後滾動即重下 |
| C-11 | P2 | `PlaybackProgressStore.swift:92-115,159-180` | 記錄陣列無上限；每次落盤在主線程整份 JSON 編碼；5 秒防抖窗口內進程終止即丟失進度 |
| C-12 | P2 | `ComicRepositoryTests.swift:371-373,403-431` | 預設測試會發真實網絡請求（Baozi 無任何閘門；JM 僅以憑據存在與否為閘門） |
| C-13 | P2 | `ComicRepositoryTests.swift:337-358` | 測試內自行重寫生產解析邏輯後斷言副本，未呼叫被測代碼 |
| C-14 | P2 | `ComicRepositoryTests.swift:299-303` | 把「憑據必須存在且非空」寫成測試期望，反向固化 C-01 |
| C-15 | P2 | `UniversalVideoEngineTests.swift:552-569`；`ComicRepositoryTests.swift:492-509` | 測試直接改動全域單例並觸發真實網絡，污染同進程其他測試 |
| C-16 | P2 | `UniversalVideoEngineTests.swift:1-590` | 39 個 case 中僅 4 個針對 `UniversalVideoEngine`，其餘測 A 線 `HTMLVideoSourceAdapter`／`VideoRepository` |
| C-17 | P2 | `CoverImageLoaderTests.swift:1-31` | 4 個 case 全為 `static` 純函數；去重、失敗清理、磁碟快取、解密路徑零覆蓋 |
| C-18 | P3 | `AuroraShelfApp.swift:5-13`；`VideoRepository.swift:70-76` | `--video-source` 被解析兩次，啟動時二次 `switchSource` 並重發一次網絡請求 |
| C-19 | P3 | `project.yml:25-28`；`pbxproj:740-742` | `CODE_SIGNING_ALLOWED=NO` 套用於所有配置，產物只能跑模擬器 |
| C-20 | P3 | `pbxproj:723`（Release 之 `ASSETCATALOG_COMPILER_APPICON_NAME`）；`ios/AuroraShelf` 無 `*.xcassets` | 無 App 圖示資產目錄，主畫面圖示為空白 |
| C-21 | P3 | `Info.plist:5-6`；`ios/AuroraShelf/Resources/*.lproj` | 開發區語言 `zh_CN` 與 12 個在地化目錄（含 `zh-Hant`）並存且未宣告 |
| C-22 | P3 | `ComicProtocolSecrets.swift:23-55` | 憑據載入回退至行程工作目錄相對路徑，屬開發期殘留 |
| C-23 | P3 | `CoverImageLoader.swift:39,141-144` | 去重／快取鍵不含 `Referer`，同 URL 不同 referer 互相覆蓋 |
| C-24 | P3 | `CoverImageLoader.swift:16-17`；`ComicPageImageLoader.swift:133-134`；`ComicProtocolSecrets.swift:5-57` | AES 金鑰硬編碼於源碼；JM 憑據只從 bundle/env 讀，與 `PicacgAuthStore` 使用 Keychain 不一致 |
| **C-25** | **P1** | `Services/LocalizationManager.swift:92-93,137-151`；`Models/TabItem.swift:12-20` 等 21 個檔 | `@MainActor` 單例被 21 個檔的非隔離全域函數呼叫 → Swift 6 併發錯誤，15:41 建置失敗 |
| **C-26** | **P1** | `pbxproj`（`LocalizationManager` 0 命中、`Localizable` 0 命中）vs `project.yml:11-14` | 專案檔與「真源」漂移：新檔與 12 個 lproj 全未入 target，「XcodeGen 為真源」實務上已失效 |
| **C-27** | P2 | 全倉庫無 `PrivacyInfo.xcprivacy`；`WatchlistStore.swift:11-13`、`PlaybackProgressStore.swift:64-68`、`LocalizationManager.swift:103,118` | 使用 UserDefaults（4 處）但無 Required Reason API 宣告 → App Store 提交阻斷 |

---

## 3. 逐項發現

### C-01　真實憑據檔隨 App bundle 分發（P0）

**症狀**
`ios/AuroraShelf/Resources/SourceSecrets.plist` 為真實填值檔（5 個鍵全部非空，長度分別為 16／17／29／70 字元），且已被納入 App target 的 Resources build phase，最終出現在建置產物 `AuroraShelf.app/` 內。任何人取得該 `.app`／IPA 即可直接讀出全部金鑰。

**證據**
1. 檔案確實存在且已填值。以 `plistlib` 讀取鍵名與值長度（不輸出值）：
   - `ios/AuroraShelf/Resources/SourceSecrets.plist`，585 bytes，`JM_DATA_SECRET` len=16、`JM_TOKEN_SECRET` len=16、`JM_TOKEN_SECRET_2` len=17、`PICACG_API_KEY` len=29、`PICACG_SIGNATURE_SECRET` len=70。
2. `ios/project.yml:11-17` 以整個目錄作來源，僅排除 `Resources/www`，未排除憑據檔：
   ```yaml
   sources:
     - path: AuroraShelf
       excludes:
         - "Resources/www"
   ```
3. `ios/AuroraShelf.xcodeproj/project.pbxproj:63` 將該檔登記為 `PBXBuildFile … in Resources`，`:484` 列入 Resources build phase 的 `files` 清單。
4. 建置產物實測存在該檔（5 份 derived 目錄全部命中），例如：
   `ios/build.nosync/audit-derived/Build/Products/Debug-iphonesimulator/AuroraShelf.app/SourceSecrets.plist`（309 bytes，鍵值雜湊前綴與源檔逐鍵一致）。
5. `.gitignore:5` 已忽略該檔、`git log -- ios/AuroraShelf/Resources/SourceSecrets.plist` 無任何紀錄 —— 憑據未進 git 歷史，但**進了產品**。

**根因**
以目錄 glob 產生 XcodeGen 來源，`Resources/` 內所有非編譯檔預設成為資源；`.gitignore` 只防範版本控制外洩，不防範打包外洩。此為「防錯方向錯了」的典型：只堵了 repo，沒堵 bundle。

**影響**
- 分發層面：任何取得 IPA／`.app` 者可解出 JM 站點簽章密鑰與 Picacg API 金鑰／簽章密鑰，可冒用 App 身分呼叫上游、偽造簽章、繞過配額或觸發對上游的濫用封禁（連帶癱瘓 App 本身）。
- 現階段暴露面的限制：`project.yml:25-28` 停用簽名（見 C-19），目前只有模擬器未簽名產物，尚未構成已發佈外洩。**一旦改為正常簽名出包，即為實質憑據外洩**，因此按 P0 計。

**建議修法**
1. 立即於 `project.yml` 加入明確排除，並改為獨立 resource 條目：
   ```yaml
   sources:
     - path: AuroraShelf
       excludes:
         - "Resources/www"
         - "Resources/SourceSecrets.plist"
   ```
2. 憑據改由建置期注入：XcodeGen `settings` 帶入 `$(JM_TOKEN_SECRET)` 等 build setting，或 `.xcconfig`（不進 git），執行期以 `Bundle.main.object(forInfoDictionaryKey:)` 或編譯期 `#if` 注入；徹底不再以檔案形式入包。
3. 若必須保留檔案路徑，改放 `Application Support/`，由首次啟動從 Keychain 或使用者輸入取得。
4. 憑據輪換：假定現行 5 個值已在多台開發機 derived 目錄中流通，應視為已洩漏並向上游申請更換。

**驗證方式**
`xcodegen generate` 後檢查 `pbxproj` Resources phase 不再含該檔；建置後 `find AuroraShelf.app -name "SourceSecrets*"` 應為空；以 `plutil -p AuroraShelf.app/Info.plist` 確認無密文鍵。

---

### C-02　ATS 全面關閉且無白名單（P1）

**症狀**
`ios/AuroraShelf/Resources/Info.plist:40-44`：
```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <true/>
</dict>
```
單一總開關關閉全部 ATS，未使用 `NSExceptionDomains`、亦未使用 `NSAllowsArbitraryLoadsInWebContent` 分離 WebView 與原生網路。整個 App（含原生 `URLSession`、`AVURLAsset`、`WKWebView`）皆可載入任意明文 HTTP。

**證據**
- 上述 plist 段落為唯一 ATS 宣告，全檔再無任何 `NSExceptionDomains`。
- 明文來源遍布原生請求：`CoverImageLoader.swift:148`、`VideoRepository.swift:66`、`ComicPageImageLoader.swift:97` 的 UA 偽裝顯示對方為傳統站點；`Info.plist` 未限制 scheme。
- WebView 承載第三方頁：`UI/Live/StripchatLiveWebView.swift:53`、`UI/Live/LiveChatSheet.swift:107` 以 `WKUserScript` 於 `forMainFrameOnly: false` 注入腳本；`UI/Game/GameContainerView.swift` 承載 `www` 資產。
- 專案內無任何 `NSExceptionDomains` 或 `NSAllowsLocalNetworking` 設定可作為收斂依據。

**根因**
為相容成人站點普遍存在的 http／自簽／非標準 TLS 而一次性關閉總開關，未按域名收斂。

**影響**
1. 傳輸層可被同網段或上游中間人改寫：封面、漫畫頁、論壇內容、播放清單均可被替換；`AVURLAsset` 對 http 直鏈尤其無任何完整性保護。
2. WebView 側：注入腳本以 `forMainFrameOnly: false` 套用到所有子框架，若主文件可被替換，注入腳本將在被攻擊者控制的頁面上執行，形成內容注入／腳本注入面。
3. 屬 App Store 審核必拒項，亦使任何未來 ATS 稽核無法據此判斷「哪些域名真的需要例外」。

**建議修法**
1. 以域名白名單取代總開關：先以 `grep` 建立實際主機清單（`CoverImageLoader.swift:152-182` 已內建參考網域對照表，可直接作為清單來源），逐域設定 `NSExceptionAllowsInsecureHTTPLoads`；其餘保持 ATS 預設。
2. WebView 專用域另設 `NSAllowsArbitraryLoadsInWebContent = true`，避免影響原生請求。
3. 於 CI 加一道檢查：`plutil -extract NSAppTransportSecurity.NSAllowsArbitraryLoads raw Info.plist` 必須不存在或 `false`。

**驗證方式**
需運行驗證：以 `grep` 產生之主機清單與實際 `URLSession` 請求日誌比對，確認白名單無漏；建置後 `plutil -p Info.plist` 檢視 ATS 段。

---

### C-03　測試與近半源碼未納入版本控制（P1）

**症狀**
工作樹上 `ios/` 內共 62 個 Swift 檔，`git ls-files ios | grep -c '\.swift'` 只有 33 個；**29 個未追蹤**，其中包含全部 9 個測試檔，以及本輪聲稱修復的 `Services/ComicPageImageLoader.swift`。

**證據**
```
tracked: 33
on disk: 62
```
未追蹤清單（節錄，完整 29 項）：
```
?? ios/AuroraShelf/Services/ComicPageImageLoader.swift
?? ios/AuroraShelf/Services/HTMLVideoSourceAdapter.swift
?? ios/AuroraShelf/Data/Video/PlaybackProgressStore.swift
?? ios/AuroraShelf/Data/Video/WatchlistStore.swift
?? ios/AuroraShelfTests/ComicRepositoryTests.swift
?? ios/AuroraShelfTests/UniversalVideoEngineTests.swift
?? ios/AuroraShelfTests/CoverImageLoaderTests.swift
?? …（其餘 6 個測試檔同為未追蹤）
```
另 `git status --short` 共 73 項變動，且 12 個 `Resources/*.lproj/Localizable.strings` 亦未追蹤。

**根因**
長期在同一工作樹上迭代、未提交；審計與交付皆對著「未固定提交的工作樹」進行。

**影響**
1. 「全量 96 項單元測試 100% SUCCEEDED」在任何乾淨 clone 上**無法重現**——測試檔本身不存在於版本庫。此為驗證鏈斷點，比任何單一程式缺陷都更影響結論可信度。
2. 無法以「固定提交」重跑審計（對照 `docs/ios/design-audit-2026-11-04.md` 已明確要求以固定提交重驗，該要求目前無法滿足）。
3. 並行多代理改同一工作樹時，任何一次誤刪都不可回復。

**建議修法**
1. 立刻分批提交：先提交 `AuroraShelfTests/**`（語料價值最高），再提交 `Services/**`、`Data/**`、`Resources/*.lproj`。
2. 每輪審計以 `git rev-parse HEAD` 開始、寫入報告首行，並要求工作樹乾淨（`git status --porcelain` 為空）。
3. 加 `.gitignore` 條目：`.playwright-cli/`（現有 3 個未追蹤 yml 屬雜訊）。

**驗證方式**
`git ls-files ios | grep -c '\.swift'` 應等於 62；`git status --porcelain -- ios` 應為空。

---

### C-04　生產路徑無直鏈內容校驗，校驗只存在於被跳過的測試（P1）

**症狀**
播放直鏈（含 `.m3u8`）解析完成後**直接交給播放器**，生產代碼未檢查 `Content-Type` 是否為 `text/html`、未檢查 HLS 前綴是否含 `#EXTM3U`。因此上游回傳的 HTML 錯誤頁／反爬頁／登入頁會被當成媒體交給 `AVPlayer`。

**證據**
1. 交付點無校驗：`UI/Screens/NativePlayerView.swift:445-446`
   ```swift
   let asset = AVURLAsset(url: result.url, options: options)
   let playerItem = AVPlayerItem(asset: asset)
   ```
   失敗時僅得泛化訊息：`:461`（`item.error?.localizedDescription ?? "视频解码失败，请重试"`），無法區分「HTML 錯誤頁」「HTTP 403」「TLS 失敗」「真的解碼失敗」。
2. 反證：團隊其實知道此風險，但校驗只寫在**預設被跳過**的測試：
   `ios/AuroraShelfTests/LiveVideoSourceAuditTests.swift:119-156`（`probeMediaResponse`）明確檢查
   ```swift
   guard !mimeType.contains("text/html") else { throw … "Resolved media URL returned HTML instead of media" }
   if … hasSuffix(".m3u8") || mimeType.contains("mpegurl") { guard prefix.contains("#EXTM3U") else { throw … } }
   ```
   而該檔兩個 case 皆以 `RUN_IOS_LIVE_SOURCE_AUDIT != "1"` 開頭 `XCTSkip`（`:13-15`、`:83-85`），即日常測試永不執行此校驗。
3. `UniversalVideoEngine.swift` 作為「引擎」不含任何解析後驗證（見 C-06）。

**根因**
校驗被實作在「審計腳本」層而非「產品路徑」層；產品靠 AVPlayer 的失敗狀態兜底。

**影響**
用戶可見：上游改版或反爬時，表現為黑屏、無限轉圈或「视频解码失败」，無重試、無替代源、無提示；而 A 線的 fallback 列表（`testJAVSBParserKeepsAllSameSitePlayerFallbacksInOrder` 所示之多候選播放框架）在此情境下不會被觸發，因為錯誤發生在「已選定候選之後」。

**建議修法**
在 `PlaybackSourceResult` 交付 `AVURLAsset` 之前加入輕量預檢（HEAD 或 `Range: bytes=0-255` 的 GET，5 秒超時）：
1. 非 2xx → 歸類 `.httpFailure(status)`；
2. `Content-Type` 含 `text/html` → 歸類 `.htmlInsteadOfMedia`，觸發下一候選；
3. 路徑／型別為 playlist → 取前 512 bytes 檢查 `#EXTM3U`；
4. 分類結果回饋 `PlayerCoordinator` 以顯示可操作文案，而非泛化錯誤。

**驗證方式**
需運行驗證：以固定 HTML 檔假扮 m3u8 端點（本地 `python3 -m http.server`）確認新預檢攔截且回退至下一候選。

---

### C-05　Bundle 219 MB，其中 203 MB 由 symlink 指入 Android 資產樹（P1）

**症狀**
`ios/AuroraShelf/Resources/www` 是一個**符號連結**，指向 `../../../app/src/main/assets/www`（Android 的 assets），被 `project.yml:15-17` 宣告為 folder reference 資源，整體複製進 App bundle。

**證據**
1. `ls -la ios/AuroraShelf/Resources/www` →
   `www -> ../../../app/src/main/assets/www`
2. `project.yml:11-17`：
   ```yaml
   - path: AuroraShelf
     excludes:
       - "Resources/www"
   - path: AuroraShelf/Resources/www
     type: folder
     buildPhase: resources
   ```
3. `du -sh app/src/main/assets/www` = **203 MB**，1260 個檔案。
4. 建置產物實測：`AuroraShelf.app/www` = 203 MB，`AuroraShelf.app` 合計 **219 MB**。
5. `pbxproj` Resources phase 第三項即 `www in Resources`。

**根因**
iOS 端直接複用 Android 的網頁遊戲資產，以 symlink 打通兩個平台的目錄，未做平台裁剪或按需下載。

**影響**
1. 交付體積：219 MB 的 iOS App，下載、安裝、更新成本與失敗率顯著上升；亦使 TestFlight／企業分發門檻提高。
2. 建置耦合：iOS 建置成果取決於 Android 源碼樹的存在與內容；Android 端資產一旦搬移或清理，iOS 建置即斷。
3. 資產來源不可審計：Xcode 專案內看不到內容，`git ls-files ios` 亦不含這些檔案（見 C-03）。

**建議修法**
1. 短期：以 `pbxproj` 精確列出必要子目錄，或改用 `buildPhase: resources` 前的裁剪腳本只複製執行所需資產。
2. 中期：把遊戲外殼改為首次啟動按需下載（`URLSession` 下載 zip 至 Caches，驗簽後解壓），bundle 內只留 loader 與 manifest。
3. 不論何種方案，將 `www` 由 symlink 改為實體目錄或 git submodule，使 iOS 建置可獨立重現。

**驗證方式**
建置後 `du -sh AuroraShelf.app` 應顯著小於現值；`find AuroraShelf.app/www -type f | wc -l` 與清單比對。

---

### C-06　`UniversalVideoEngine` 不含播放解析，且網絡錯誤未分類（P1；錯誤分類子項為 P2）

**症狀**
檔名與既有聲明暗示此檔負責「播放引擎」，但實測全檔只做**兩個來源的列表抓取**（XVideos HTML、EPORNER JSON API），**完全沒有**：播放位址解析、HLS 處理、超時控制、重試、直鏈校驗。同時 `session.data(for:)` 的傳輸層錯誤直接以 `URLError` 外洩，未包裝成 `VideoFeedError`。

**證據**
1. 全檔僅兩個公開入口：`fetchXVideosFeed`（`:28-67`）、`fetchEPORNERFeed`（`:138-156`）。無任何 `AVAsset`／`m3u8`／`playlist`／`retry`／`timeout` 字樣。
2. 錯誤分類只覆蓋 HTTP 狀態與 JSON 結構：`decodeXVideosFeed`（`:69-78`）與 `decodeEPORNERFeed`（`:158-167`）分別拋 `.httpStatus`、`.malformedResponse`。
3. 但傳輸層錯誤未被包裝：`:58` 與 `:151` 皆為裸 `try await session.data(for: request)`，因此 DNS 失敗、逾時、TLS 失敗會以 `URLError` 直接穿透到 `VideoRepository.loadFeed` 的 `catch`，經 `error.localizedDescription` 顯示給用戶。
4. 超時完全依賴呼叫端注入的 session：`VideoRepository.swift:63-66`（`timeoutIntervalForRequest = 15`），引擎自身沒有超時契約。
5. 檔內無重試；`page` 參數僅用於組路徑。

**根因**
檔案命名與實際職責脫節；播放解析實際落在 A 線 `HTMLVideoSourceAdapter` 與 `UI/Screens/NativePlayerView`。

**影響**
1. 審計與交接的可追溯性受損：本輪任務書要求「UniversalVideoEngine 的錯誤分類、超時、重試、直鏈校驗」在此檔無標的，若照檔名分配審計項，會漏掉真正的位置（`NativePlayerView`／`HTMLVideoSourceAdapter`）。
2. 用戶可見：XVideos／EPORNER 的網絡例外與 HTTP 例外呈現為兩種不同語氣的英文系統訊息（`URLError`）與中文訊息混雜。

**建議修法**
1. 於 `fetch*Feed` 以 `do/catch` 包裝 `URLError`，轉為 `VideoFeedError.transport(source:underlyingCode:)` 並提供統一中文文案。
2. 在引擎內顯式宣告超時與一次有限重試（例：`URLSession` 每次請求 12 秒，重試 1 次、指數退避 300 ms），不再隱式依賴呼叫端 session。
3. 更名或補齊：若「引擎」要名副其實，應把 A 線的解析與直鏈校驗上移至本檔，形成單一職責邊界。

**驗證方式**
需運行驗證：以 `URLProtocol` stub 注入 `URLError(.timedOut)` 與 `URLError(.notConnectedToInternet)`，斷言皆轉為 `VideoFeedError` 且文案一致。

---

### C-07　`CoverImageLoader` 在 actor 內同步讀磁碟並解碼，形成全域序列化瓶頸（P2）

**症狀**
`loadImage` 在 actor 隔離域內直接執行同步磁碟 IO 與 `UIImage(data:)` 解碼。`CoverImageLoader` 是**全 App 單例**（`:7`），因此所有封面載入的磁碟讀取與解碼在該 actor 上嚴格排隊。

**證據**
`CoverImageLoader.swift:46-51`：
```swift
let diskPath = cacheDirectory.appendingPathComponent(cacheKey)
if let diskData = try? Data(contentsOf: diskPath), let diskImage = UIImage(data: diskData) {
    memoryCache.setObject(diskImage, forKey: cacheKey as NSString)
    return diskImage
}
```
`Data(contentsOf:)` 為同步阻塞呼叫；`UIImage(data:)` 於首次繪製時亦在呼叫執行緒解碼。全檔僅此一處 `Data(contentsOf:)`（`grep` 結果確認）。

**根因**
把「快取查詢」與「磁碟 IO」放在同一 actor 方法內，未以 `nonisolated`／`Task.detached` 把阻塞工作移出 actor。

**影響**
首頁或漫畫列表一次顯示 20–40 張封面，其中快取命中者仍需依序完成磁碟讀取；冷啟動或記憶體警告清空 `NSCache` 後（`countLimit = 120`，`CoverImageLoader.swift:32`）滾動會明顯掉幀與延遲。屬可量測的效能問題，非致命。

**建議修法**
1. 把磁碟讀取與解碼抽成 `nonisolated` 或 `Task.detached(priority: .userInitiated)`，僅在 actor 內做鍵查詢與字典登記。
2. 或改用 `URLCache` 單一層（見 C-08），以 `session.data(for:)` 的非阻塞路徑取代自建磁碟層。

**驗證方式**
需運行驗證：以 40 張冷快取封面量測首幀時間；修改前後對比。

---

### C-08　磁碟快取無界、無 TTL、與 `URLCache` 重複、寫入非 atomic（P2）

**症狀**
自建 `AuroraCovers` 目錄無限增長，無容量上限、無 LRU 淘汰、無 TTL；同時又設定了 192 MB 的 `URLCache`，形成兩套快取；寫入使用 `try? finalData.write(to:)` 非 atomic；快取策略為 `.returnCacheDataElseLoad`，等同永不重驗。

**證據**
- `CoverImageLoader.swift:20-22`：建立 `AuroraCovers`，全檔無任何清理／淘汰邏輯（`grep "removeItem\|dateExpiration\|sizeLimit"` 無命中）。
- `:27-28`：
  ```swift
  config.urlCache = URLCache(memoryCapacity: 32 * 1024 * 1024, diskCapacity: 192 * 1024 * 1024)
  config.requestCachePolicy = .returnCacheDataElseLoad
  ```
- `:88`：`try? finalData.write(to: diskPath)` —— 無 `.atomic`，亦忽略寫入失敗。
- `:141-144`：`cacheKey` 為 URL 的 SHA-256，檔名固定長度，無版本前綴（改加密參數後舊檔不會失效）。

**根因**
兩層快取同時存在但職責不清；未定義快取治理策略（上限／過期／版本）。

**影響**
1. 磁碟佔用無上限：每個不同封面 URL 一個檔，長期使用可累積數百 MB；與 `URLCache` 的 192 MB 疊加。
2. `.returnCacheDataElseLoad` 忽略伺服器 `Cache-Control`，帶時效 token 的封面 URL 一旦被快取即長期回舊圖。
3. 非 atomic 寫入在崩潰或磁碟滿時產生截斷檔；該檔在下次載入會解碼失敗並回退網絡（可自癒），但殘檔不會被刪除。

**建議修法**
1. 擇一保留：建議只留 `URLCache`（系統代管淘汰），刪除自建目錄；若保留自建層則加 `totalFileSizeLimit`（例 128 MB）＋最近使用時間淘汰＋`write(to:options:.atomic)`。
2. `requestCachePolicy` 改 `.useProtocolCachePolicy`，讓上游 header 決定新鮮度。
3. `cacheKey` 加入快取版本前綴（如 `cover_v2_`），使演算法變更時自然失效。

**驗證方式**
需運行驗證：連續載入 N 個不同 URL 後 `du -sh Caches/AuroraCovers` 應收斂於上限；`file` 檢查截斷檔能自癒。

---

### C-09　兩個載入器對同一 AES 密文的 padding 假設不一致（P2）

**症狀**
兩個載入器使用**完全相同**的金鑰／IV（`f5d965df75336270` / `97b60394abc2fbe1`），但 padding 假設不同：`CoverImageLoader` 只嘗試無 padding（raw），`ComicPageImageLoader` 先試無 padding、再試 PKCS7。

**證據**
- `CoverImageLoader.swift:204-221`，`CCCrypt(..., 0, /* 无 Padding 或手工 Padding */, ...)` —— 單一選項。
- `ComicPageImageLoader.swift:135-136`：
  ```swift
  let options = [CCOptions(0), CCOptions(kCCOptionPKCS7Padding)]
  for option in options { … }
  ```
- 金鑰硬編碼位置：`CoverImageLoader.swift:16-17`、`ComicPageImageLoader.swift:133-134`。

**根因**
同一協議的兩個實作各自演化，未共用一個解密器。

**影響**
若 91動漫／黃果的某類資源實際為 PKCS7 填充，`CoverImageLoader` 會解出尾部多餘位元組；PNG/JPEG 通常容忍尾隨資料，但一旦上游換成嚴格格式或 WebP，表現為「封面永遠空白」且無錯誤提示（`:84-86` 直接 `return nil`）。同時兩處重複實作增加維護風險。

**建議修法**
抽出單一共用解密器（如 `AESImageDecryptor` 於 `Services/`），統一嘗試 `[0, PKCS7]` 兩種填充並以 `UIImage(data:)` 成功與否作為判據；兩載入器改為呼叫同一函式。

**驗證方式**
需運行驗證：以 PKCS7 填充的樣本密文分別餵入兩個載入器，斷言皆解碼成功。

---

### C-10　漫畫頁完全無磁碟快取，記憶體上限偏低（P2）

**症狀**
`ComicPageImageLoader` 只有 `NSCache`（32 張／80 MB），無任何磁碟層；一旦觸發記憶體警告即全清，之後使用者往回滾動會重新下載整頁。

**證據**
- `ComicPageImageLoader.swift:17-18`：`cache.countLimit = 32`、`cache.totalCostLimit = 80 * 1024 * 1024`。
- `:21-27`：`didReceiveMemoryWarningNotification` → `memCache.removeAllObjects()`。
- 全檔無 `cacheDirectory`／`FileManager`／`write(to:)`（`grep` 結果：整個 `Services/` 只有 `CoverImageLoader` 有磁碟層）。
- 對照：`CoverImageLoader` 有磁碟層（`:20-22, 88`）。

**根因**
圖片快取策略在兩個載入器之間不對稱；漫畫頁被當作「大而不需持久」處理，但閱讀器行為是反覆回看。

**影響**
1. 資料用量：一本 40 頁漫畫反覆翻閱可重複下載數次；行動網路下屬用戶成本。
2. 依 `:90-92`，每頁需 `UIImage(data:)` 後再視 `verticalSegments` 重繪（`unscramble`），無法避免重工。

**建議修法**
1. 為漫畫頁增加與封面同構的磁碟快取（建議上限 256 MB、以 `page.id` 雜湊為鍵、`.atomic` 寫入、TTL 7 日）。
2. 或改為章節級預取＋落盤，配合 `ComicReaderView` 的滾動方向預讀。
3. `totalCostLimit` 依實測頁面像素量調整（若單頁解碼 8 MB，32 張已超 80 MB 上限，等於持續抖動淘汰）。

**驗證方式**
需運行驗證：模擬記憶體警告後回滾，量測是否重發 HTTP 請求（Charles／`URLProtocol` 記錄）。

---

### C-11　播放進度：無上限、主線程整份編碼、防抖窗口有丟失風險（P2）

**症狀**
`PlaybackProgressStore` 的 `records` 陣列無容量上限，每次落盤在主線程對**整個**陣列做 `JSONEncoder().encode`；落盤受 5 秒防抖＋10 秒硬節流約束，若進程在此窗口內被終止，最新進度丟失。

**證據**
- `PlaybackProgressStore.swift:92-115`：每次 `updateProgress` 皆 `var list = records` 複製陣列後回寫，無裁剪。
- `:159-173`：防抖邏輯（10 秒硬節流、5 秒 `Task.sleep` 防抖）。
- `:175-180`：
  ```swift
  private func persistToDisk(_ newRecords: [PlaybackRecord]) {
      lastDiskPersistTime = Date()
      if let data = try? JSONEncoder().encode(newRecords) { defaults.set(data, forKey: Self.storageKey) }
  }
  ```
  `PlaybackRecord` 內嵌完整 `VideoItem`（`:9`），單筆記錄即含標題／封面／播放位址等；整份編碼成本隨記錄數線性上升，且此方法由 `@MainActor` 類別呼叫。
- 兜底只在「離開播放器」時：`NativePlayerView.swift:505` `PlaybackProgressStore.shared.flushPendingPersist()`；全專案無 `scenePhase`／`didEnterBackground` 監聽（`grep "scenePhase\|willResignActive\|didEnterBackground"` 於 `ios/AuroraShelf` 零命中）。

**根因**
以單一 JSON blob 作儲存格式、以時間節流而非「變更即持久化＋背景沖刷」；缺少 App 進入背景的強制沖刷點。

**影響**
1. 用戶可見：播放中按 Home 鍵、或系統在背景終止 App，最近最多 10 秒（節流路徑）至 5 秒（防抖路徑）進度丟失；此類丟失在「繼續觀看」情境下屬高感知缺陷。
2. 長期使用後 `records` 累積至數千筆，每次落盤在主線程編碼數 MB JSON，造成可感知卡頓（0.5 秒一次的進度回呼路徑上）。

**建議修法**
1. 加 `scenePhase` 監聽（`.onChange(of: scenePhase)` 於 `AuroraShelfApp` 或 `MainContainerView`），`background` 時 `flushPendingPersist()`。
2. `records` 設上限（例 200 筆，按 `lastUpdated` 淘汰）或以 `isAutoCompleted && isManuallyDismissed` 清理。
3. 落盤移至 `nonisolated` 背景佇列，只傳值陣列；主執行緒不做編碼。

**驗證方式**
需運行驗證：播放 10 秒後立即上滑回主畫面再進入，檢查 Up Next 進度是否保留；`Instruments` 觀察主線程編碼耗時。

---

### C-12　預設測試會發真實網絡請求（P2）

**症狀**
測試套件並非 hermetic：其中至少 2 個測試在**無需任何環境變數**的情況下就會連上真實站點。

**證據**
1. **完全無閘門**：`ComicRepositoryTests.swift:402-431` `testBaoziLiveFetchComicsChaptersAndPages` —— 方法本體直接 `await repo.fetchComics(for: ComicSourceCatalog.baozi, …)`、`CoverImageLoader.shared.loadImage`、`fetchChapterPages`、`ComicPageImageLoader.shared.load`。無 `XCTSkip`、無 env 檢查。
2. **只以憑據存在為閘門**：`ComicRepositoryTests.swift:370-374`
   ```swift
   guard ComicProtocolSecrets.hasJMConfiguration else { throw XCTSkip("JM configuration not available") }
   ```
   由於 `SourceSecrets.plist` 已入 bundle（C-01）且測試宿主為 App（`project.yml:38` `TEST_HOST` 指向 `AuroraShelf.app`），`hasJMConfiguration` 恆為 true，故此測試**在預設執行中必然發出真實請求**（`fetchComics` → 封面解碼 → `fetchComicDetails` → `fetchChapterPages`，共 3–4 次外呼）。
3. 其餘 4 個 live 測試確實有 env 閘門（`ComicRepositoryTests:7`、`ForumRepositoryTests:6`、`LiveSourceAdapterTests:7`、`LiveVideoSourceAuditTests:13,84`）。

**根因**
live 稽核測試與單元測試混在同一 target，且閘門設計不一致（env 變數 vs 憑據存在）。

**影響**
1. 「96 項 100% SUCCEEDED」的綠色包含 2 個依賴外網與第三方站點可用性的測試：站點改版、CDN 封鎖、CI 無外網皆會轉紅；反之站點可達時的綠燈**不能**證明本地邏輯正確（它證明的是「此刻上游行為如預期」）。
2. 測試時間不穩定（`5.034 秒` 之宣稱無法穩定重現）。

**建議修法**
1. 把 live 測試移入獨立 target（例 `AuroraShelfLiveTests`）或以統一的 `RUN_IOS_LIVE_SOURCE_AUDIT` 閘門包住**全部** live case，包括 Baozi。
2. 單元測試改用 `URLProtocol` stub 或注入 `fetcher`（`ForumRepositoryTests:186` 已示範可注入 fetcher 的良好做法，可直接沿用）。
3. 在 CI 明示兩段式：`unit`（hermetic）與 `live-audit`（opt-in）。

**驗證方式**
`xcodebuild test -only-testing:AuroraShelfTests` 於斷網環境應全綠（現況會紅）。

---

### C-13　測試內重寫生產邏輯後斷言副本（P2）

**症狀**
`testJMNumericIDsAndAuthorDecoding` 沒有呼叫任何生產解碼器；它在測試方法內**重新實作**一份 JM JSON 解析邏輯，再對自己寫的副本斷言。

**證據**
`ComicRepositoryTests.swift:330-358`：
```swift
guard let data = jsonString.data(using: .utf8),
      let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
      let items = root["content"] as? [[String: Any]] else { XCTFail("Invalid fixture"); return }

let parsed = items.compactMap { raw -> ComicItem? in
    let idString: String
    if let str = raw["id"] as? String { idString = str }
    else if let num = raw["id"] as? NSNumber { idString = num.stringValue } else { return nil }
    … // 完整重寫：author 陣列/字串分支、cover 拼接
    return ComicItem(id: idString, sourceId: "jm", title: title, author: author, coverUrl: cover, tags: tags)
}
XCTAssertEqual(parsed[0].id, "1488548")
```
`parsed` 完全由測試內代碼產生；生產端對應解碼器未被觸及。

**根因**
測試作者需要的生產解碼函式當時未暴露為可測介面，遂在測試內複製邏輯求綠。

**影響**
此測試**永遠不會**因生產解碼器回歸而變紅。若有人改壞真實 JM 解碼路徑（例如 author 陣列拼接、`NSNumber` id 處理），測試套件仍 100% 綠。屬典型假測試。

**建議修法**
1. 將 JM 解碼抽為 `nonisolated static func decodeJMFeed(_ data: Data) throws -> [ComicItem]`（比照 `ComicSourceAdapter.decodeCopyComicFeed` 既有慣例），測試直接呼叫它。
2. 刪除測試內的解析副本，只保留 fixture 字串與期望值。

**驗證方式**
刪除生產解碼器的一行（例 author 拼接分隔符）後該測試應轉紅——現況不會。

---

### C-14　測試把「憑據必須存在」寫成期望，反向固化 C-01（P2）

**症狀**
`testComicProtocolSecretsLoadsJMConfigurationFromLocalFilesOrBundle` 直接斷言憑據非空。這使得「移除 bundle 內憑據檔」這個正確的安全修法會讓測試轉紅。

**證據**
`ComicRepositoryTests.swift:299-303`：
```swift
func testComicProtocolSecretsLoadsJMConfigurationFromLocalFilesOrBundle() {
    XCTAssertTrue(ComicProtocolSecrets.hasJMConfiguration)
    XCTAssertFalse(ComicProtocolSecrets.value("JM_TOKEN_SECRET").isEmpty)
    XCTAssertFalse(ComicProtocolSecrets.value("JM_DATA_SECRET").isEmpty)
}
```
`ComicProtocolSecrets.values` 的來源為 bundle → 測試宿主 bundle（`ComicProtocolSecrets.swift:6-15`）。

**根因**
把「環境具備能力」與「代碼行為正確」混為一談。

**影響**
1. 修 C-01 時此測試必紅，形成「安全修復 vs 測試綠燈」的直接衝突，實務上會誘導團隊保留憑據打包。
2. 在乾淨 clone（無 `SourceSecrets.plist`）上此測試直接失敗，使「全量綠」不可重現——與 C-03 疊加。

**建議修法**
1. 改為純邏輯測試：注入 `values` 字典（把 `values` 改為可注入的 `init(secrets:)` 或 `static func load(from:)`），斷言「有值時 `hasJMConfiguration == true`、空字典時為 false」，不依賴真實憑據。
2. 真實憑據的存在性檢查移入 live-audit 段並加 env 閘門。

**驗證方式**
於無 `SourceSecrets.plist` 的乾淨工作樹執行該測試應為綠或 skip，而非 fail。

---

### C-15　測試改動全域單例並觸發真實網絡（P2）

**症狀**
兩個「分頁狀態重置」測試直接操作 `VideoRepository.shared` / `ComicRepository.shared` 這兩個全域單例，且 `switchSource`／`switchCategory` 會啟動真實網絡請求；測試結束後未還原狀態。

**證據**
- `UniversalVideoEngineTests.swift:552-569`：`let repo = VideoRepository.shared` 後設 `currentPage`/`canLoadMore`/`isLoadingNextPage`，再 `repo.switchCategory(to: "连载番剧")` 與 `repo.switchSource(to: VideoSourceCatalog.huavod)`。而 `VideoRepository.swift:93-100`（`switchSource`）與 `:80-83`（`switchCategory`）皆會呼叫 `loadFeed` → 發真實請求。
- `ComicRepositoryTests.swift:492-509`：同構，操作 `ComicRepository.shared` 並 `switchSource(to: ComicSourceCatalog.copycomic)`。
- 兩者皆無 `tearDown` 還原，單例狀態延續至後續測試。

**根因**
缺少可注入的儲存／連線抽象，只能拿單例來測狀態機。

**影響**
1. 測試順序敏感（XCTest 不保證順序），任一測試讀取 `VideoRepository.shared.isLoading` / `feedVideos` 都可能被前一測試留下的請求結果污染。
2. 每次跑測試都對上游發 2 次請求，加劇 C-12。

**建議修法**
1. `VideoRepository`／`ComicRepository` 改為可注入 `session` 與 `fetcher`（`ForumRepository` 已具備此模式，`ForumRepositoryTests:186` 示範），測試以 stub 取代網絡。
2. 加 `tearDown` 重置單例狀態，或改為測試專用實例（把 `private init` 改為 `internal init(session:)`）。

**驗證方式**
以 `-only-testing` 單獨執行與全量執行，比較 `feedVideos` 狀態一致性。

---

### C-16　測試檔名與實際覆蓋不符（P2）

**症狀**
`UniversalVideoEngineTests.swift` 有 39 個 case，但其中只有 4 個真正呼叫 `UniversalVideoEngine`；其餘 35 個測的是 A 線的 `HTMLVideoSourceAdapter`、`VideoRepository`、`VideoSourceCatalog`。

**證據**
實際命中 `UniversalVideoEngine.` 的 case：
- `:6` `testXVideosHTTPFailurePropagatesInsteadOfReturningCuratedItems`
- `:24` `testXVideosEmptyMarkupDoesNotCreateSyntheticFeedItems`
- `:34` `testXVideosFeedUsesOnlyParsedUpstreamMetadata`
- `:305` `testEPORNERFeedDecoderUsesLiveAPIFields`

其餘（節錄）：`:53,67,77,93,102,111,118,128,142,152,162,172,182,190,217,226,235,244` 皆為 `HTMLVideoSourceAdapter.*`；`:376,545` 為 `VideoRepository.*`；`:317,340,385,400,422,433` 為 catalog/路由表測試。

**根因**
測試按「功能域」而非「被測檔案」組織，卻以檔案名命名。

**影響**
審計與交接會誤判覆蓋率：看到 `UniversalVideoEngineTests` 590 行／39 case 會以為引擎有充分覆蓋，實際引擎只有 4 個純解析 case，且**無**任何超時、重試、錯誤分類、直鏈校驗測試。

**建議修法**
拆檔為 `HTMLVideoSourceAdapterTests`、`VideoSourceCatalogTests`、`UniversalVideoEngineTests`（後者僅留 4 個 case 並補齊錯誤分類／超時／重試）。

**驗證方式**
`grep -c "UniversalVideoEngine\." UniversalVideoEngineTests.swift` 應等於該檔 case 數。

---

### C-17　圖片載入器零行為測試（P2）

**症狀**
`CoverImageLoaderTests.swift` 只有 4 個 case，且全部是 `static` 純函數（URL 正規化、referer 注入）；`ComicPageImageLoader` 完全沒有專屬測試。兩者聲稱的核心能力（`actor` ＋ `inFlightTasks` 去重）**無任何測試**。

**證據**
- `CoverImageLoaderTests.swift:1-31`：4 個 case 分別測 `normalizeUrl`（`:7-8`、`:13-14`、`:20-22`）與 `imageRequest` 的 Referer（`:29`）。無 `async`（`grep async` 為 0）、不觸及 `loadImage`。
- 全專案 `grep ComicPageImageLoader` 於 `AuroraShelfTests/` 僅出現在 live 測試內（`ComicRepositoryTests.swift:53,428`）。
- 未被測的關鍵路徑：`inFlightTasks` 去重（`CoverImageLoader.swift:54-56,95-96`）、失敗後條目清理、`AES` 解密分支（`:76-82`）、磁碟寫入與截斷恢復（`:48,88`）、`ComicPageImageLoader.performLoad` 的 bzcdn→static-tw 回退（`:76-89`）、`unscramble` 分段演算法（`:106-128`）。

**根因**
去重邏輯被認為「actor 保證了正確性」，因而未以測試固定其行為契約。

**影響**
「已升級為 actor 並引入 inFlightTasks 去重」這條聲明只能靠代碼閱讀成立，一旦後人改動（例如把 `defer` 移到錯誤分支、或在 `inFlightTasks` 上做負快取），無任何測試會轉紅。此為本輪聲明中最缺乏證據的一項。

**建議修法**
補 5 個 hermetic 測試（以 `URLProtocol` stub 計數請求次數）：
1. 同 URL 併發 10 次 → stub 僅收到 1 次請求；
2. 失敗後再次呼叫 → 應重試（stub 收到 2 次），證明無永久失敗快取；
3. 成功後第二次呼叫 → 記憶體命中，stub 仍為 1 次；
4. 記憶體清空後 → 磁碟命中，stub 仍為 1 次；
5. `unscramble` 對 100px/3 段的像素還原正確性。

**驗證方式**
上述測試對應實作改動後應可分別轉紅。

---

### C-18　啟動時 `--video-source` 被解析兩次（P3）

**症狀**
`AuroraShelfApp.init` 與 `VideoRepository.init` 各自解析同一命令列參數並各呼叫一次 `switchSource`，導致啟動時第二次 `switchSource` 取消並重發第一次的列表請求。

**證據**
- `AuroraShelfApp.swift:5-13`：
  ```swift
  if let idx = args.firstIndex(of: "--video-source"), idx + 1 < args.count {
      let srcId = args[idx + 1]
      let src = VideoSourceCatalog.find(by: srcId)
      VideoRepository.shared.switchSource(to: src)
  }
  ```
- `VideoRepository.swift:70-76`（同一段邏輯，已內建）：
  ```swift
  if let idx = args.firstIndex(of: "--video-source"), idx + 1 < args.count { … self.switchSource(to: src) }
  else { self.switchSource(to: VideoSourceCatalog.dongman91) }
  ```
- `switchSource` → `loadFeed` 首行即 `currentLoadTask?.cancel()`（`:93-100`、`:106`），故第二次呼叫會取消第一次。

**根因**
`VideoRepository` 內建參數處理後，`App.init` 的舊副本未移除。

**影響**
啟動多一次被立即取消的請求；`isLoading` 翻轉兩次；除錯時日誌出現重複的來源切換。屬輕微但明確的冗餘。

**建議修法**
刪除 `AuroraShelfApp.init` 內的參數解析整段（保留 `VideoRepository.init` 的單一來源），或反向把參數處理集中到 App 層並自 `VideoRepository` 移除。另建議 `VideoSourceCatalog.find` 對未知 id 回傳 `nil` 並在 UI 提示，而非靜默 fallback 至 `huavod`（`VideoSourceCatalog.swift:273-275`）。

**驗證方式**
以 `--video-source huavod` 啟動，於 `loadFeed` 入口埋點計數應為 1。

---

### C-19　`CODE_SIGNING_ALLOWED=NO` 套用於全部配置（P3）

**症狀**
App target 的 Debug 與 Release 皆停用簽名，且 `DEVELOPMENT_TEAM` 為空。產物只能在模擬器執行，無法安裝於真機、無法 TestFlight／企業分發、無法上架。

**證據**
- `project.yml:25-28`：`DEVELOPMENT_TEAM: ""`、`CODE_SIGNING_REQUIRED: "NO"`、`CODE_SIGNING_ALLOWED: "NO"`、`CODE_SIGN_IDENTITY: ""`。
- `pbxproj:740-742` 為 App target 其一配置，另一配置（Release）同樣含三項停用設定。

**根因**
為繞過無開發者憑證環境的模擬器建置而全域停用。

**影響**
1. 目前所有「建置成功」的結論只覆蓋模擬器情境；真機行為（記憶體壓力、ATS 實際攔截、後台終止、AVPlayer 硬體解碼）皆未被驗證。
2. 若 iOS 端要交付，此設定必須改動，屆時簽名、entitlement、真機驗證全部需重做——屬延後風險而非當前缺陷，故列 P3。

**建議修法**
保留 Debug 停用；Release 改回正常簽名設定（`CODE_SIGNING_ALLOWED=YES` ＋ `DEVELOPMENT_TEAM`），或明確標記「本專案僅交付模擬器版」並在 `README`／`PROJECT_STATE` 寫明，避免誤解為可分發。

**驗證方式**
`xcodebuild -showBuildSettings -configuration Release | grep CODE_SIGNING_ALLOWED` 應為 YES（若決定交付真機）。

---

### C-20　無資產目錄，App 圖示為空白（P3）

**症狀**
專案內不存在任何 `*.xcassets`／`AppIcon.appiconset`，但 Release 設定仍指定 `ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon`。

**證據**
- `find ios/AuroraShelf -name "*.xcassets" -o -name "AppIcon*"` → 無結果。
- `pbxproj` Release App 配置含 `ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon`。
- `Info.plist` 內無 `CFBundleIconName`／`CFBundleIcons`。

**根因**
資產目錄從未建立。

**影響**
安裝後主畫面圖示為系統預設灰白格，且 `Info.plist` 無圖示宣告。屬交付外觀缺失，不影響功能。

**建議修法**
新增 `ios/AuroraShelf/Resources/Assets.xcassets`（含 `AppIcon` 全套尺寸與 `AccentColor`），由 `project.yml` 自動納入。

**驗證方式**
建置後 `plutil -p AuroraShelf.app/Info.plist | grep -i icon` 應有圖示鍵。

---

### C-21　開發區語言與在地化目錄不一致（P3）

**症狀**
`CFBundleDevelopmentRegion = zh_CN`（簡體中國），但磁碟上存在 12 個 `.lproj`（含 `zh-Hant`），且 `Info.plist` 未宣告 `CFBundleLocalizations`；同時 12 個 `Localizable.strings` 全部未納入 git（C-03）、亦未進入 `pbxproj`（C-26）。

**證據**
- `Info.plist:5-6`：`<key>CFBundleDevelopmentRegion</key><string>zh_CN</string>`。
- `ls -d ios/AuroraShelf/Resources/*.lproj`（15:43:37 實測）→ `de, en, es, fr, id, ja, ko, ms, th, vi, zh-Hans, zh-Hant`，**共 12 個**。
- 12 個 `Localizable.strings` 皆有實質內容（各 111 行；位元組數 3,576–5,690），非空殼佔位。
- 全 12 個 `.lproj` 皆在 `git status` 的未追蹤清單內。

**對「`AppLanguage` 13 選項 vs 僅 8 個 lproj（缺 es/ms/id/vi）」一說的核實**
**不成立**（以採樣終點實測為準）。`Services/LocalizationManager.swift:4-17` 的 `AppLanguage` 共 13 個 case = `system` ＋ 12 個語言；12 個語言與 12 個 lproj 目錄**逐一對應，無缺項**：
`zh-Hant/zh-Hans/en/ja/ko/th/de/fr/es/ms/id/vi` 皆有對應目錄且 `Localizable.strings` 非空（`es` 3,800 bytes、`ms` 3,700 bytes、`id` 3,679 bytes、`vi` 4,177 bytes，均為 111 行）。
另已核實 `zh-Hans` 與 `zh-Hant` **內容不同且繁體為正確轉換**：兩檔 SHA-1 分別為 `59c2d10d…`／`d556cbf0…`，`diff` 顯示 `"tab.home"` 為 `主页` vs `主頁`、`"tab.comics"` 為 `漫画` vs `漫畫`、`"tab.forum"` 為 `社区` vs `社區`、`"tab.library"` 為 `资料库` vs `資料庫` —— 字形轉換正確（符合香港繁體 `為/說/溫` 一系正字要求），非未轉換副本。

**根因**
先以簡體作開發語言，後補多語系但未回頭調整開發區宣告。

**影響**
香港／台灣用戶在「系統語言未匹配任何 lproj」時會落到 `zh_CN` 簡體；`zh-Hant` 目錄存在但未追蹤，乾淨 clone 建置會少 12 個語系。屬本地化交付缺口。

**建議修法**
`CFBundleDevelopmentRegion` 改 `zh-Hant`（或 `en`），並補 `CFBundleLocalizations` 明確列出支援語系；提交全部 lproj。

**驗證方式**
`plutil -p Info.plist` 檢視兩鍵；建置產物 `find AuroraShelf.app -name "*.lproj"` 應為 12。

---

### C-22　憑據載入回退至行程工作目錄相對路徑（P3）

**症狀**
`ComicProtocolSecrets.values` 在 bundle 與環境變數皆不可用時，會以 `FileManager.currentDirectoryPath` 拼相對路徑讀取 `SourceSecrets.plist` 與 `source-secrets.properties`（含 `../` 上跳路徑）。此為開發期便利邏輯，產品中屬多餘讀取面。

**證據**
`ComicProtocolSecrets.swift:23-55`：
```swift
let cwd = fm.currentDirectoryPath
let candidatePaths = ["ios/AuroraShelf/Resources/SourceSecrets.plist",
                      "AuroraShelf/Resources/SourceSecrets.plist",
                      "../ios/AuroraShelf/Resources/SourceSecrets.plist",
                      "SourceSecrets.plist"]
…
let propPaths = ["source-secrets.properties", "../source-secrets.properties"]
```
並使用已折舊的 `NSDictionary(contentsOfFile:)`（`:34`、`:42` `String(contentsOfFile:)`）。

**根因**
為在測試與命令列環境免設定而加入多重回退，未以 `#if DEBUG` 隔離。

**影響**
在裝置上 `cwd` 為 App 沙箱根，實際不可利用；但（a）擴大了「何處可放憑據」的隱性契約，（b）產品二進位仍含檔案系統探測邏輯，（c）`../` 路徑在未來沙箱化或 extension 場景可能越界。

**建議修法**
以 `#if DEBUG` 包住檔系統回退段；Release 僅保留 bundle 注入與環境變數（且環境變數亦應 `#if DEBUG`）。

**驗證方式**
Release 建置後於 `ComicProtocolSecrets` 加暫時斷點／日誌確認未走檔案回退（需刪除日誌前完成）。

---

### C-23　去重與快取鍵不含 `Referer`（P3）

**症狀**
`CoverImageLoader` 的去重鍵與快取鍵皆只由正規化 URL 之 SHA-256 決定，未含 `requestReferer`。同一 URL 若被不同 referer 的呼叫端請求，先到者決定結果，後到者可能拿到 403／錯圖。

**證據**
- `CoverImageLoader.swift:39`：`let cacheKey = Self.cacheKey(for: normalized)`。
- `:141-144`：`cacheKey` = `SHA256(Data(url.utf8))`，`requestReferer`（`:35` 參數）未參與。
- 呼叫端存在差異：`UI/Forum/ForumPostDetailView.swift:211-214` 傳 `requestReferer: referer`；`UI/Components/AuroraCoverImage.swift:57` 只傳 `baseURL`（走 `imageRequest` 的預設 referer 對照表，`CoverImageLoader.swift:150-182`）。

**根因**
以 URL 唯一性假設取代「URL ＋ 請求上下文」唯一性。

**影響**
目前論壇圖與封面圖的 URL 空間幾乎不重疊，故為潛在風險而非現行缺陷；一旦兩處共用 CDN 位址（例如同一圖床），後載入者會拿到前者以不同 referer 取得的結果（甚至 403 後不寫快取但 `inFlightTasks` 已共用）。

**建議修法**
`cacheKey` 加入 referer 的可選參與：`SHA256(url + "|" + (referer ?? ""))`；或明確規定圖片一律使用 host 對照表決定的 referer，移除 `requestReferer` 參數。

**驗證方式**
需運行驗證：以同一 URL、兩個不同 referer 併發載入，確認兩者結果互不干擾。

---

### C-24　金鑰硬編碼且憑據儲存機制不一致（P3）

**症狀**
AES 金鑰與 IV 以字串常值硬編碼於兩個服務檔；JM 協議憑據只從 bundle／環境變數／工作目錄讀取，而同一 App 的 Picacg token 卻存於 Keychain，形成兩套憑據治理。

**證據**
- `CoverImageLoader.swift:16-17`、`ComicPageImageLoader.swift:133-134` 皆為 `"f5d965df75336270"` / `"97b60394abc2fbe1"` 字面值。
- `ComicProtocolSecrets.swift:5-57` 的來源鏈：bundle plist → 其他 bundle → 環境變數 → 工作目錄檔案 → properties 檔。全程無 Keychain。
- 對照：`Data/Comic/PicacgAuthStore.swift:16-40` 使用 `kSecClassGenericPassword`／`SecItemCopyMatching`／`SecItemUpdate`（Keychain 正確實作）。

**根因**
圖片協議固定金鑰本質上必須內嵌（上游協議決定），但未以混淆或伺服器簽發替代；JM 憑據則沿用開發期檔案方案未升級。

**影響**
（a）硬編碼金鑰可直接以 `strings` 自二進位取得 —— 屬協議既定事實，改進空間有限，但仍應記錄；（b）JM 憑據與 Picacg token 的保護等級不一致，前者可被任何人自 bundle 讀出（同時是 C-01 的放大器）。

**建議修法**
1. 圖片 AES 金鑰改由首次啟動時向上游換取短期金鑰，或至少以分段混淆＋執行期組裝降低 `strings` 直取成功率。
2. JM 憑據改存 Keychain，與 `PicacgAuthStore` 統一（並移除 C-01／C-22 的檔案來源）。

**驗證方式**
`strings AuroraShelf.app/AuroraShelf | grep -c f5d965df75336270` 目前應 ≥1；改動後應為 0。

---

### C-25　`@MainActor` 本地化單例被非隔離全域函數呼叫，建置阻斷（P1）

**症狀**
`LocalizationManager` 宣告為 `@MainActor`，但其便捷入口 `L10n(_:_:)` 與 `String.localized` 擴展皆為**非隔離的全域宣告**，內部直接讀取 `LocalizationManager.shared`。Swift 6 語言模式下這屬編譯期錯誤。實際後果：**15:41 版建置失敗**（Lead 實測：6 個 Swift 6 併發錯誤）。

**證據**
1. `Services/LocalizationManager.swift:92-93`：
   ```swift
   @MainActor
   public final class LocalizationManager: ObservableObject {
   ```
2. `:137-141`（非隔離全域函數）與 `:143-151`（非隔離擴展）：
   ```swift
   public func L10n(_ key: String, _ arguments: CVarArg...) -> String {
       LocalizationManager.shared.localized(key, arguments)      // ← 跨隔離存取 @MainActor 單例
   }

   extension String {
       public var localized: String {
           LocalizationManager.shared.localized(self)            // ← 同上
       }
   ```
   兩處皆無 `@MainActor`、`nonisolated` 修飾，亦無 `await`。
3. 呼叫面已擴散至 **21 個檔**（`grep -rl "\.localized\|L10n(" ios/AuroraShelf`），且不限於 UI：
   `Data/Video/VideoRepository.swift`、`Data/Video/WatchlistStore.swift`、`Data/Comic/ComicRepository.swift`、`Data/Forum/ForumSourceCatalog.swift`、`Data/Live/LiveSourceCatalog.swift`、`UI/Theme/DeviceLayoutProfile.swift`。
   其中 Data 層與 `DeviceLayoutProfile`（`Sendable` 純值型別）本身就是非隔離上下文，屬必然違規點。
4. `Models/TabItem.swift:12-20` 現行版本：`case .home: return "tab.home".localized`，`MainTab.title` 為非隔離計算屬性。
5. 該檔**未加入** `pbxproj`（見 C-26），故 15:36 版建置（檔未存在）能通過、15:41 版失敗，與 Lead 兩次取樣結果一致。

**根因**
本地化改造以「全域便利函數＋屬性擴展」形式引入，卻把狀態持有者標為 `@MainActor`；兩者隔離域不匹配。等於把 UI 隔離的他責任意下放到資料層。

**影響**
1. **建置阻斷（已發生）**：目前工作樹無法產出可執行檔，較任何執行期缺陷優先。
2. 修法若走錯方向（在 21 個呼叫點逐一加 `@MainActor`）會把主執行緒約束污染到 Data 層與解析器，製造新的「主線程阻塞」風險。
3. 即使加 `@MainActor` 強行通過，`WatchlistStore`／`PlaybackProgressStore` 的 `init` 與落盤路徑都在 `@MainActor`，屆時字串本地化會疊加在同一執行緒上。

**建議修法**（建議方向：讓 `LocalizationManager` 不再是隔離型別）
1. 把本地化狀態改為 **`Sendable` 不可變快照**：以 `nonisolated(unsafe) static var current: LocalizationSnapshot` 或 `OSAllocatedUnfairLock` 保護的字串表，讓 `L10n`／`String.localized` 在任意上下文安全讀取；`activeBundle` 與 `activeLocale` 打包進快照，語言切換時整體替換。
2. 若必須保留 `ObservableObject` 以供 SwiftUI 觀察，則把「觀察用外殼」與「查表核心」分離：外殼 `@MainActor @Observable`，核心 `Sendable`；`L10n` 只呼叫核心。
3. 移除 `String.localized` 對 `@MainActor` 的依賴後，`Data/**` 與 `DeviceLayoutProfile` 的呼叫點即自然合規，無須逐一改標註。
4. 短期可先用 `MainActor.assumeIsolated` 止血，但**不建議**作最終方案（在非主執行緒呼叫會 trap）。

**驗證方式**
`xcodebuild build -scheme AuroraShelf -destination 'generic/platform=iOS Simulator' 2>&1 | grep -c "error:"` 應為 0；確認 `Data/**` 與 `DeviceLayoutProfile` 未被加上 `@MainActor`。

---

### C-26　`pbxproj` 與 `project.yml` 漂移，「真源」聲明失效（P1）

**症狀**
`project.yml` 被聲明為專案真源，但現行 `pbxproj` 完全不含本地化改造的任何成員：`LocalizationManager` 0 命中、`Localizable`／`lproj` 0 命中。即「真源描述的內容」與「實際編譯的內容」不一致，且是導致 C-25 建置失敗的直接原因之一（新檔未被編譯，引用端找不到符號）。

**證據**
（本人實測，採樣終點 15:43:37）
```
grep -c "LocalizationManager" ios/AuroraShelf.xcodeproj/project.pbxproj   → 0
grep -c "Localizable"          ios/AuroraShelf.xcodeproj/project.pbxproj   → 0
```
`project.yml:11-14` 卻以整目錄 glob：
```yaml
sources:
  - path: AuroraShelf
    excludes:
      - "Resources/www"
```
依 XcodeGen 語意，該 glob 會納入 `Services/LocalizationManager.swift` 與 12 個 `*.lproj`（後者作為 variant group 進入 Resources）。故 `xcodegen generate` 後 `pbxproj` 必然新增 LocalizationManager 的 `PBXBuildFile`／`PBXFileReference`／Sources 成員，以及 12 個 lproj 資源成員——與**現行**檔案不符。
（Lead 另經實測指出 `xcodegen generate` 後會「多 4 個 LocalizationManager 引用與多個 lproj 檔成員」，與本推論一致。）

**根因**
以「手動追加檔案到 Xcode 專案」或「先改源碼、後補 generate」的順序工作，`pbxproj` 因而落後於 `project.yml` 與工作樹。此為 C-03（大量未追蹤檔）的同源症狀。

**影響**
1. 建置成敗取決於「上一次 generate 的時點」，而非源碼現況——任何人 clone 後的行為與本機不一致。
2. 審計與交接無法用「`project.yml` 即事實」推理；本報告因此改以 `pbxproj` 實測為準。
3. 12 個在地化字串（每個 111 行，內容齊備）目前**完全不參與建置**，即 `AppLanguage` 的切換在現行產物中無效——即使 C-25 修好，仍需重新 generate 方能生效。

**建議修法**
1. 建立硬性流程：`xcodegen generate` 為建置前置步驟（Makefile／`xcodebuild` 前置 script），並在 CI 檢查 `git diff --exit-code ios/AuroraShelf.xcodeproj`（若 pbxproj 仍要追蹤）。
2. 更徹底：`pbxproj` 移出版本控制並加入 `.gitignore`，改由 generate 產出（XcodeGen 官方建議做法），徹底消除漂移。
3. 提交前檢查清單加一項：`grep -c "LocalizationManager" pbxproj` 必須 ≥1，且 lproj 成員數等於目錄數。

**驗證方式**
`xcodegen generate && git diff --stat ios/AuroraShelf.xcodeproj` 應為空；`grep -c "\.lproj" pbxproj` 應等於 12（或以 variant group 計）。

---

### C-27　無 `PrivacyInfo.xcprivacy` 而使用 Required Reason API（P2）

**症狀**
全倉庫不存在任何 `PrivacyInfo.xcprivacy`，但 App target 使用 UserDefaults（Apple 列為 Required Reason API，需宣告 `NSPrivacyAccessedAPICategoryUserDefaults`）。

**證據**
1. `find . -name "PrivacyInfo.xcprivacy"` → **0 個**（排除 `build*` 目錄）。
2. UserDefaults 實際使用 4 處：
   - `Data/Video/WatchlistStore.swift:11,13`（`private let defaults: UserDefaults` / `init(defaults: UserDefaults = .standard)`）
   - `Data/Video/PlaybackProgressStore.swift:64,68`（同構）
   - `Services/LocalizationManager.swift:103`（`UserDefaults.standard.string(forKey:)`）
   - `Services/LocalizationManager.swift:118`（`UserDefaults.standard.set(_:forKey:)`）
3. `project.yml` 內無任何 `PrivacyInfo` 相關資源條目。

**根因**
本地化與持久化改造期間未同步補隱私清單。

**影響**
1. **App Store 提交阻斷**：缺少 Required Reason API 宣告會被 App Store Connect 的隱私檢查擋下（ITMS-91053 類）。現階段因停用簽名（C-19）只能跑模擬器，屬延後風險，但與「要交付」的目標直接衝突。
2. 側載／企業分發不受此強制，故列 P2 而非 P1。

**建議修法**
新增 `ios/AuroraShelf/Resources/PrivacyInfo.xcprivacy`：
```xml
<key>NSPrivacyAccessedAPITypes</key>
<array>
  <dict>
    <key>NSPrivacyAccessedAPIType</key>
    <string>NSPrivacyAccessedAPICategoryUserDefaults</string>
    <key>NSPrivacyAccessedAPITypeReasons</key>
    <array><string>CA92.1</string></array>
  </dict>
</array>
```
（`CA92.1` = App 自身存取自有偏好設定；若日後加入第三方 SDK 需逐一補。）並確認 XcodeGen 會把它作為資源納入（`project.yml` 的 `Resources` glob 已涵蓋，generate 後檢查）。

**驗證方式**
建置後 `find AuroraShelf.app -name "PrivacyInfo.xcprivacy"` 應為 1；`plutil -p` 檢視鍵值。

---

## 4. 對既有「已修復／已驗證」聲明的核實結果

依據 `.ai/PROJECT_STATE.md:3-11`（2026年11月6日「12 項…全量修復閉環」段落）逐條核實。

| # | 聲明原文（節錄） | 判定 | 證據 |
|---|---|---|---|
| 1 | 「`CoverImageLoader` 與 `ComicPageImageLoader` 全面升級為 Swift 6 原生 `actor`，並引入 `inFlightTasks` 任務字典去重，杜絕同 URL 並發請求擊穿網絡與磁盤寫競爭」 | **部分屬實** | `actor` 與字典確實存在：`CoverImageLoader.swift:6`（`public actor CoverImageLoader`）、`:13`（`private var inFlightTasks: [String: Task<UIImage?, Never>] = [:]`）；`ComicPageImageLoader.swift:5`、`:10`。去重的競態亦確實被 actor 隔離消除（登記前無 `await`，見 `:54-56` 與 `:95`）。但：（a）「杜絕並發請求擊穿」無任何測試佐證（C-17）；（b）磁盤寫競爭確實消除，`disk write` 卻變成 actor 內同步阻塞（C-07）；（c）磁碟快取治理缺失（C-08）；（d）`ComicPageImageLoader` 仍無磁碟層（C-10）；（e）兩者 padding 假設不一致（C-09）。「全面」與「杜絕」屬過度表述。 |
| 2 | 「全量 96 項單元測試 100% SUCCEEDED（0 failures, 5 skipped，耗時 5.034 秒）」 | **數字自洽但語義誤導** | 實測 9 個測試檔共 **101** 個 `func test`（`python3` 正則計數：28+4+5+14+5+2+3+39+1）。5 個 env 閘門 skip 站點（`ComicRepositoryTests:7`、`ForumRepositoryTests:6`、`LiveSourceAdapterTests:7`、`LiveVideoSourceAuditTests:13,84`）→ 執行 96 項，數字吻合。但「全量」應為 101；（b）96 項之中至少 2 項（`ComicRepositoryTests:371` JM、`:403` Baozi）依賴真實外網（C-12），故綠色非 hermetic；（c）**全部 9 個測試檔未納入 git**（C-03），任何乾淨 clone 無法重現此結果；（d）耗時 5.034 秒無法驗證（本人未執行測試）。 |
| 3 | 「XcodeGen 管理專案（`ios/project.yml` 為真源）」 | **屬實** | `ios/project.yml` 完整定義兩個 target（App／`bundle.unit-test`）、`INFOPLIST_FILE`、`SWIFT_VERSION`、`CODE_SIGNING_*`、sources glob；`pbxproj` 中 24 位十六進位隨機 UUID 與自動 glob 的 61 個 `in Sources`（52 app + 9 test）與 `project.yml` 完全對應；`pbxproj` 的 `CURRENT_PROJECT_VERSION = 174`、`MARKETING_VERSION = 2.16.0`、`IPHONEOS_DEPLOYMENT_TARGET = 26.4` 與 yml 一致；`Resources/www` 以 `type: folder` 現於 Resources phase。注意：`pbxproj` 為產物但仍被 git 追蹤，且目標層 `SWIFT_VERSION = 6.0` 覆蓋專案層預設 `5.0`（`pbxproj:592` 與 `:657`）—— 屬正常覆蓋關係，非衝突。 |
| 4 | 「正則表達式全部靜態預編譯優化」（同段第 5 點一部分） | **不屬實** | `ComicSourceAdapter.swift` 內 14 處 `NSRegularExpression(pattern:` 為每次呼叫即時編譯，`static let … NSRegularExpression` 為 0 處；`HTMLVideoSourceAdapter.swift` 為 10 處即時編譯 vs 3 處靜態。僅 `UniversalVideoEngine.swift:80-83` 與 `CoverImageLoader` 相關路徑確為靜態。 |
| 5 | 「各源截斷魔數清零…`UniversalVideoEngine`（XVideos 24部）」 | **屬實（限此檔）** | `UniversalVideoEngine.parseXVideosHtml`（`:85-136`）逐 block append，無 `prefix(24)`／`dropLast`；`decodeEPORNERFeed`（`:169-204`）為 `compactMap` 全量映射。注意此聲明僅涵蓋 feed 截斷，與「播放解析」無關（C-06）。 |
| 6 | 「`PlaybackProgressStore` 增加 5 秒防抖窗口與 10 秒硬節流閾值…降頻 >85%」 | **屬實（實作存在）／覆蓋不足** | `PlaybackProgressStore.swift:159-173` 確有 10 秒硬節流與 5 秒 `Task.sleep` 防抖；`flushPendingPersist` 存在（`:151-157`）且於 `NativePlayerView.swift:505` 呼叫。但「降頻 >85%」無量測證據；且缺少背景沖刷點導致進度丟失（C-11），節流行為亦無測試（§5）。 |
| 7 | 「`VideoRepository` 升級為 `@MainActor public final class`…純解析方法標記 `nonisolated`」 | **屬實** | `VideoRepository.swift:38` `@MainActor`、`:40` `public final class`、`:256,414,515,648,831` 共 5 個 `nonisolated func parse*`。 |
| 8 | 「`NativePlayerView` 內 `seek` 完成回調切回主線程修改 `@State`」 | **屬實（抽樣）** | `NativePlayerView.swift` 的 `status`/`duration` 觀察回呼皆以 `DispatchQueue.main.async` 包裹；時間觀察器以 `queue: .main` 註冊。此項非本人主責範圍，僅抽樣核對，不作完整結論。 |
| 9 | （建置與測試「已驗證」之整體狀態） | **不成立（依 Lead 實測，且已被本報告 C-25 獨立印證）** | Lead 實測：15:36 版 BUILD OK ＋ 96 tests passed／5 skipped；**15:41 版 BUILD FAILED**，6 個 Swift 6 併發錯誤。本人未執行 `xcodebuild`，但已靜態確認失敗根因存在且充分：`LocalizationManager` 為 `@MainActor`（`:92-93`），而其非隔離入口 `L10n`（`:137`）與 `String.localized`（`:143-151`）直接跨隔離存取單例，並被 21 個檔呼叫（C-25）；加上該檔與 12 個 lproj 均未入 `pbxproj`（C-26）。故「已修復／已驗證」之聲明**在採樣終點時刻對應的樹上不成立**。 |

**額外事實性修正**（`PROJECT_STATE` 未提及但影響結論）：`ios/` 下 62 個 Swift 中 29 個未追蹤（C-03）；`Resources/www` 為指向 Android 樹的 symlink 且佔 bundle 203 MB（C-05）；`pbxproj` 與 `project.yml` 已漂移、12 個 lproj 未參與建置（C-26）；採樣終點的樹**無法建置**（C-25）；缺 `PrivacyInfo.xcprivacy`（C-27）。

**對「全量綠」證據鏈的整體判斷**：`PROJECT_STATE` 的測試聲明與建置聲明**各自對應不同時刻的樹**——96 項測試對應 15:36 版（本地化改造前），而採樣終點（15:43）的樹已因本地化改造無法編譯。故「已修復／已驗證」不能以單一時間點的綠色覆蓋現行工作樹；任何後續結論必須綁定具體提交或樹指紋（見第 0 節）。

---

## 5. 測試真實性評估

> 重要前提：本節對「case 數／斷言數」的統計對象是**採樣終點的 `AuroraShelfTests/` 檔案內容**（9 檔、101 個 `func test`），屬靜態計數，本人未執行測試。至於「96 項通過」之**執行結果**，依 Lead 實測屬 15:36 版之樹；採樣終點的樹因 C-25 已無法建置，故該結果不能直接套用於現行樹。兩者不可混用。

### 5.1 量化總覽

| 測試檔 | 行數 | case | 斷言 | `XCTSkip` 站點 | 網絡依賴 | 判斷 |
|---|---|---|---|---|---|---|
| `ComicRepositoryTests.swift` | 555 | 28（2 個 class） | 117 | 2 | **有（2 個默認執行）** | 混合：多數為 fixture 解析測試，質量尚可；但含 1 個假測試（C-13）、1 個無閘門 live（C-12）、1 個憑據期望（C-14） |
| `UniversalVideoEngineTests.swift` | 590 | 39 | 133 | 0 | 間接（`:552` 觸發真實請求） | 斷言質量高（大量精確字串比對），但 **35/39 測錯檔案**（C-16），且無引擎錯誤路徑測試 |
| `ForumRepositoryTests.swift` | 279 | 14 | 46 | 1 | 1 個 env 閘門 live | **質量最好**：可注入 `fetcher` 的並發／失敗／晚到響應測試（`:185-254`）為全專案模範 |
| `LiveVideoSourceAuditTests.swift` | 156 | 2 | 10 | 2 | 全部 live | **默認 100% skip**：其中 `probeMediaResponse`（`:119-156`）是唯一實作直鏈內容校驗的代碼（C-04 核心證據） |
| `LiveSourceAdapterTests.swift` | 64 | 5 | 15 | 1 | 1 個 env 閘門 live | 4 個純解析測試質量可接受 |
| `PlaybackProgressStoreTests.swift` | 94 | 3 | 15 | 0 | 無 | 真實行為測試（`:32-93` 驗 5 秒門檻、90% 完結、跨實例持久化）；缺節流／flush 覆蓋 |
| `DeviceLayoutTests.swift` | 80 | 5 | 28 | 0 | 無 | 純函數數學，斷言密度高，無風險 |
| `CoverImageLoaderTests.swift` | 31 | 4 | 5 | 0 | 無 | 僅靜態函數；核心能力零覆蓋（C-17） |
| `WatchlistStoreTests.swift` | 31 | 1 | 3 | 0 | 無 | 單一 happy-path；`setBookmarked` 的讀回驗證分支（`WatchlistStore.swift:36-38`）未測 |
| **合計** | 1,860 | **101** | **372** | **6** | 默認執行含 2 個真實網絡 | — |

### 5.2 斷言質量

- **`XCTAssertNotNil` 佔比僅 3.2%**（12/372），其餘為 `XCTAssertEqual` 精確比對與 `XCTAssertThrowsError` ＋ pattern match（如 `UniversalVideoEngineTests.swift:7-22`）。**這是本專案測試的最大優點**：斷言並非靠 `!= nil` 撐場面，`testXVideosFeedUsesOnlyParsedUpstreamMetadata`（`:34-51`）逐欄比對 7 個屬性、`testEPORNERFeedDecoderUsesLiveAPIFields`（`:305-315`）驗證時長換算與評分舍入，屬實質斷言。
- **`XCTSkip` 並非大面積跳過**：6 個站點分佈於 4 個檔，其中 5 個是「需真實網絡」的稽核用例，屬合理設計；未發現以 skip 掩蓋失敗的模式。
- **真正的問題不在斷言，而在被測對象的真實性**：大量用例以「作者自撰的 HTML fixture」測「作者自撰的正則」，兩者同源，因此結構性風險是「fixture 與真實上游同步腐化」而非「斷言太弱」。例：`testHTMLSourceAdapterParsesKissJavCardWithLazyThumbnail`（`:53-65`）的 `<div class="thumb thumb_rel item">` 若上游改為 `thumb item`，測試仍綠，但實際解析全空。

### 5.3 假綠（false green）風險清單

| 風險 | 位置 | 說明 |
|---|---|---|
| **R1 默認執行即外呼** | `ComicRepositoryTests.swift:403-431`（Baozi，無閘門）、`:371-400`（JM，僅憑據閘門） | 綠燈包含外部站點可用性；反之站點封鎖即紅。CI 無外網時必紅。 |
| **R2 測試內重建生產邏輯** | `ComicRepositoryTests.swift:337-358` | 測副本，永不因生產回歸轉紅（C-13）。 |
| **R3 fixture 與解析器同源自撰** | `UniversalVideoEngineTests.swift:53-200,457-531`、`ComicRepositoryTests.swift:74-266` | 只能證明「解析器符合作者的 HTML 想像」，不能證明符合當前上游。 |
| **R4 憑據存在性被寫成期望** | `ComicRepositoryTests.swift:299-303` | 使安全修復轉紅（C-14）。 |
| **R5 全域單例污染** | `UniversalVideoEngineTests.swift:552-569`、`ComicRepositoryTests.swift:492-509` | 順序敏感，且隱含真實請求（C-15）。 |
| **R6 核心能力無測試** | `CoverImageLoaderTests.swift:1-31`；`ComicPageImageLoader` 無專屬檔 | 去重、失敗清理、磁碟快取、解密、`unscramble` 全無（C-17）。 |
| **R7 唯一有效校驗被 skip** | `LiveVideoSourceAuditTests.swift:119-156` | 直鏈 HTML 誤判的防線只存在於 opt-in 測試（C-04）。 |

### 5.4 與本輪聲稱修復項目的覆蓋對應

| 本輪聲稱修復 | 有對應測試？ | 說明 |
|---|---|---|
| 圖片載入器 actor ＋ 去重 | **否** | 無任何 async 測試（C-17）；聲明 4 完全無測試背書 |
| 播放進度 5 秒防抖／10 秒節流 | **否** | `PlaybackProgressStoreTests.swift` 只測狀態機與跨實例持久化，未測節流窗口與 `flushPendingPersist` |
| 截斷魔數清零 | **部分** | 僅 `testHuavodParserDoesNotTruncateFeedAtTwentyVideos`（`UniversalVideoEngineTests.swift:533-549`）以 25 筆 fixture 驗一源；XVideos／Comic／Forum／Live 的截斷無專門測試 |
| `VideoRepository` `@MainActor` 化 | **否** | 無並發測試；`testVideoRepositoryPaginationStateReset…` 只驗狀態重置 |
| `MainContainerView` 輪詢 Task 生命週期 | **否** | 無任何 UI 生命週期測試 |
| 正則靜態預編譯 | **否** | 且聲明本身不屬實（§4 第 4 項） |
| 論壇 `compositeID` 去重鍵 | **間接** | `testT66yParserUsesActualThreadLinkAndDoesNotDuplicateRows`（`ForumRepositoryTests.swift:118`）驗證不重複，可接受 |

---

## 6. 已核查未發現問題的點

以下項目經針對性核查，**未發現缺陷**；列出以縮小後續審計重複面。

1. **`PlayerCoordinator` 無殘留狀態設計缺陷。** `App/PlayerCoordinator.swift:5-21` 全檔僅 21 行：`activeVideo` 單一屬性、`play` 賦值、`dismiss` 置 nil，`@Observable @MainActor`。真正的播放器狀態不在協調器內，而在 `NativePlayerView` 的 `cleanupPlayer()`，該函式（`NativePlayerView.swift:496-520` 區段）確實逐一 `invalidate()` 兩個 KVO observer、`removeTimeObserver`、取消 `hideControlsTask`、清空字幕／音軌群組，且在 `onDisappear`（`:351`）與關閉回呼（`:314-316`）雙路徑呼叫。故「全局狀態機殘留」不成立。
2. **`inFlightTasks` 失敗後確有清理，不存在永久失敗快取。** `CoverImageLoader.swift:95-96` 與 `ComicPageImageLoader.swift:44-45` 皆以 `defer { inFlightTasks[key] = nil }` 於函式退出（含失敗）清除；`CoverImageLoader` 內層 `Task<UIImage?, Never>` 失敗回 `nil`、外層 `defer` 仍執行；`ComicPageImageLoader` 拋錯時 `defer` 同樣執行。故第二次呼叫會重試網絡。此為對「失敗快取」假設的**否定**。
3. **去重鍵不含解密參數並不會導致錯圖。** 因為去重／快取鍵即正規化 URL（`CoverImageLoader.swift:39,141-144`），而解密與否由該 URL 的 host 決定（`:191-194`、`ComicPageImageLoader.swift:130-132`），一名一密文，故不存在「不同密文共用一 key」。真正的鍵缺陷是缺 `Referer`（C-23），而非缺解密參數。
4. **`unscramble` 分段還原演算法正確。** 以 `height=100, segments=3` 驗算 `ComicPageImageLoader.swift:106-128`：`segmentHeight=33, remainder=1`，三段來源區間 `66..100 / 33..66 / 0..33` 與目的區間 `0..34 / 34..67 / 67..100` 恰好無縫覆蓋 100 像素，無重疊無遺漏。
5. **`ComicPage.id` 具唯一性，不構成跨漫畫碰撞。** `Data/Comic/ComicSourceCatalog.swift:30`：`self.id = resolutionUrl ?? imageUrl`，兩個欄位皆為具體頁面／圖片位址，非章節級複用值。
6. **`WatchlistStore` 持久化具備寫入後驗證，且無同步檔案 I/O。** `Data/Video/WatchlistStore.swift:34-38` 寫入 `UserDefaults` 後即讀回比對，不符即拋 `persistenceFailed`；全檔無 `Data(contentsOf:)`／`write(to:)`。`UserDefaults` 由系統以原子替換方式落地，故不適用 `.atomic` 要求。
7. **全專案僅一處檔案 I/O，且無日誌洩漏憑據。** `grep "Data(contentsOf:"` 於 `ios/AuroraShelf` 僅命中 `CoverImageLoader.swift:48`；`grep "\.write(to:"` 僅命中 `:88`。`grep "print(|NSLog|os_log|Logger("` 於 `Services/`、`Data/`、`App/`、`Models/` **零命中**，即無任何憑據或 token 被寫入日誌／錯誤訊息（錯誤訊息皆為靜態中文文案，如 `ComicPageImageLoader.swift:167-175`）。
8. **`PicacgAuthStore` 憑據儲存正確使用 Keychain。** `Data/Comic/PicacgAuthStore.swift:16-40` 以 `kSecClassGenericPassword` ＋ `SecItemCopyMatching`／`SecItemAdd`／`SecItemUpdate` 管理 `picacg-token`，未用 `UserDefaults`。此為專案內的正確示範（與 C-24 的 JM 憑據形成對比）。
9. **Release 未使用 `-Onone`；亦無 `@preconcurrency`／`nonisolated(unsafe)` 掩蓋併發問題。** `pbxproj:711-712` Release 為 `SWIFT_COMPILATION_MODE = wholemodule` ＋ `SWIFT_OPTIMIZATION_LEVEL = "-O"`，Debug 為 `-Onone`（`:656`），屬正常配置。全樹 `grep "@preconcurrency"` 與 `grep "nonisolated(unsafe)"` **零命中**；`@unchecked Sendable` 僅 1 處（`Data/Live/LiveSourceCatalog.swift:55` 的 `ObservableObject` 包裝類，屬常見且必要的最小讓步）。故「大量逃生艙口掩蓋併發錯誤」不成立。
10. **`SWIFT_VERSION` 專案層 5.0 與目標層 6.0 並非衝突。** `pbxproj:657`（專案層 Debug/Release 為 5.0）被 target 層 `:592`（App）、`:584`（Tests）的 `6.0` 覆蓋，Xcode 語意上目標層優先，實際編譯為 Swift 6 語言模式（隱含 `SWIFT_STRICT_CONCURRENCY = complete`）。
11. **deployment target 26.4 並非「只支援未來系統」。** 環境工具鏈為 Xcode 27.2／`iPhoneOS27.2.sdk`（`SDKSettings.plist` 讀出 `Version = 27.2`、`DefaultDeploymentTarget = 27.2`、`MinimumDeploymentTarget = None`），即 26.4 屬**上一個大版本系列**而非未來版本，工具鏈亦不禁止該下限。真正影響是被排除的裝置（iOS 26.0–26.3 及全部 iOS 26 以下）——此屬產品決策，列入第 7 節待確認，不列為缺陷。
12. ~~**`TabItem.swift` 無缺陷。**~~ **本項撤回。** 審計期間該檔被改動：15:38 版 `title` 為硬編碼中文字串（`"主页"`／`"漫画"`／`"直播"`／`"社区"`／`"资料库"`），該版本確無缺陷；採樣終點（15:43）已改為 `"tab.home".localized` 形式（現行 `Models/TabItem.swift:12-20`），因而納入 C-25 的隔離違規範圍（`title` 為非隔離計算屬性，經 `String.localized` 跨隔離讀 `@MainActor` 單例）。枚舉結構本身（5 個 tab、`iconName`／`selectedIconName` 一一對應、`CaseIterable`＋`Identifiable`）仍正確。
13. **測試未以 `XCTSkip` 大面積掩蓋失敗。** 6 個 skip 站點皆有明確理由（需 live 網絡／需本地憑據），符合 opt-in 稽核設計；`grep XCTSkip` 未發現任何「暫時跳過以維持綠燈」的註解或 TODO。

---

## 7. 未能確認、需運行驗證的假設

| # | 假設／問題 | 為何靜態無法判定 | 建議驗證動作 | 責任 |
|---|---|---|---|---|
| U-1 | 96 項測試是否真的全綠、0 warnings | 本人依規則未執行 `xcodebuild`／測試 | `xcodebuild test -scheme AuroraShelf -destination 'platform=iOS Simulator,name=iPhone 17 Pro' 2>&1 \| tee /tmp/c-test.log`；核對 `Executed 101 tests, with 5 tests skipped` 與 `0 failures` | Lead |
| U-2 | 斷網時測試是否轉紅（確認 C-12） | 需在無外網環境執行 | 關閉網路後重跑，觀察 `testBaoziLiveFetch…`、`testJMLiveFetch…` 是否失敗 | Lead |
| U-3 | 移除 `SourceSecrets.plist` 後 `testComicProtocolSecretsLoads…` 是否失敗（確認 C-14） | 需改動工作樹 | 暫以 `xcodegen` 排除該檔後重跑該 case（可於臨時 worktree 進行） | Lead／A 線 |
| U-4 | 冷啟動 40 張封面的首幀時間（量化 C-07） | 需 `Instruments`／實機量測 | Time Profiler 觀察 `CoverImageLoader` actor 上的 `Data(contentsOf:)` 堆疊佔比 | Lead |
| U-5 | 91動漫／黃果密文是否為 PKCS7 填充（決定 C-09 是否已實際踩中） | 需真實回應樣本 | 以 Charles／mitmproxy 取得一份原始密文，分別以 `[0]` 與 `[PKCS7]` 解，比對 `UIImage` 是否成功 | Lead／A 線 |
| U-6 | `URLCache` 192 MB 與自建目錄是否真的雙重佔用（C-08 量級） | 需長時間使用取樣 | 使用 1 週後 `du -sh` 兩處；或 `xcrun simctl get_app_container` 檢查 sizes | Lead |
| U-7 | 上游 HTML 結構是否仍與 fixture 一致（量化 R3） | 需即時抓取上游 | `RUN_IOS_LIVE_SOURCE_AUDIT=1 IOS_LIVE_SOURCE_IDS=<id> xcodebuild test -only-testing:…` | Lead |
| U-8 | deployment target 26.4 是否為有意取捨 | 屬產品決策，非代碼可判 | 確認目標裝置覆蓋範圍；若需支援 iOS 26.0–26.3 應下調至 26.0 | Lead／產品 |
| U-9 | `www` symlink 在真機建置（非模擬器）是否同樣被完整複製 | 僅能由模擬器產物推斷 | 真機 archive 後 `du -sh AuroraShelf.app/www` 比對 203 MB | Lead |
| U-10 | ATS 白名單改造後是否有遺漏主機（C-02 修法風險） | 需實際請求清單 | Release 建置後以 `URLProtocol`／網路日誌彙整所有實際 host，逐域納入 `NSExceptionDomains` | Lead／A 線 |
| U-11 | `PlayerCoordinator` 與 `NativePlayerView` 在「切換下一片」時是否有殘留播放器物件 | 靜態可見 `cleanupPlayer` 齊備，但 `AVPlayer` 釋放時機無法靜態確認 | 連續切換 10 片後以 `Instruments` 觀察 `AVPlayer`／`AVPlayerItem` 實例數是否累積 | Lead |
| U-12 | 12 個 `lproj` 與 `Localizable.strings` 內容是否完備（C-21 影響面） | 已部分確認：12 檔皆 111 行且有內容、`zh-Hant` 確為正確繁體轉換（SHA-1 不同，字形正確）。未確認者為各語系**鍵集合是否一致**（缺鍵會回退到 key 本身） | `for d in *.lproj; do grep -c '^"' $d/Localizable.strings; done` 應全為同一數值；再比對鍵集合差異 | 在地化負責人 |
| U-13 | 12 個 lproj 是否會在 `xcodegen generate` 後真正進入 target，以及 `AppLanguage` 切換是否生效 | 現行 `pbxproj` 0 命中 `Localizable`（C-26），故在採樣終點的樹上**必然無效**；generate 後的行為需實測 | `xcodegen generate` → `grep -c "\.lproj" pbxproj`；再以 `-AppleLanguages` 啟動參數驗證字串是否切換 | Lead |
| U-14 | 「19 源實測」結論是否在自動化證據內 | Lead 指出 5 個 skipped 測試即逐源實測（comic／forum／live／video／huavod 各一），本人對照源碼確認此 5 個 skip 站點存在（§5.1），故 `RUN_IOS_LIVE_SOURCE_AUDIT` 未開啟時，19 源的真實可用性**不在自動化證據內**，只有 fixture 解析證據 | 以 `RUN_IOS_LIVE_SOURCE_AUDIT=1` 分源執行並保留日誌；或將 live 稽核結果落盤為 `docs/ios/audit-2026-11-06/` 附件 | Lead |
| U-15 | `PrivacyInfo.xcprivacy` 所需 reason code 是否僅 `CA92.1` | 取決於實際 API 用法（是否用 `UserDefaults` 讀寫他 App／共享群組容器的資料） | 檢視 `WatchlistStore`／`PlaybackProgressStore`／`LocalizationManager` 是否只用 `.standard` 自有鍵（現況看是），若是則 `CA92.1` 足夠 | Lead |
| U-16 | 本地化改造的最終形態（是否續用 `@MainActor` 單例） | 審計期間該檔仍在被改寫，最終方案未知；本報告只判定採樣終點的形態違規 | 待該會話完成後，以新固定提交重驗 C-25／C-26 | Lead／該會話 |

---

**報告結束。**

**重驗要求**：本報告綁定 2026-11-06 15:43:37（UTC+8）之工作樹與 HEAD `78e7d135…`。該樹在審計期間持續被改動且已無法建置（C-25）。任何後續驗收應：(1) 先完成 C-03 的提交動作，取得固定提交；(2) 於凍結樹上重跑本報告第 7 節的 U-1／U-2／U-13；(3) 以新提交重新核對 C-25／C-26 是否已修復。在此之前，`PROJECT_STATE` 之「全量修復閉環」不宜作為發佈依據。

本報告僅覆蓋 C 線範圍；`HTMLVideoSourceAdapter.swift`（A 線）之內部邏輯未審，其相關引用僅作交叉證據使用。`LocalizationManager.swift` 為審計期間新落地之檔案，不屬原定 C 線範圍，僅因其造成建置阻斷（C-25）而納入。
