# A 線審計：iOS 資料層與網絡適配器

審計日期：2026年11月6日
審計線：A 線（數據層與網絡適配器）
審計性質：只讀源碼審計。未修改任何 `.swift`、未執行 `xcodebuild`、未啟動模擬器。

### 審計基準（重要：工作樹正被其他會話實時改動）

- 審計基準時刻：**2026-11-06 15:44 HKT**
- `HEAD` = `78e7d13`；`git status --porcelain` 共 **74** 項
- 工作樹指紋（定義：`sha256(HEAD + "\n" + git status --porcelain)` 前 16 位元）：**`3ca731a7d6342807`**
- 本線 13 個受審檔案在 15:22–15:25 之後**再無改動**（逐檔 `mtime` 見下表），故第 3 節全部 `檔案:行號` 對應下列內容摘要；若工作樹其後再被改動，行號請以本表 `sha256` 前 8 位對照。
- 本線**未**參與建置判讀。Lead 已回報：15:36 指紋下 `BUILD OK` + 96 tests passed / 5 skipped；15:41 指紋下 `xcodebuild` 失敗（`LocalizationManager` 未入 target、Swift 6 下 `nonisolated` 上下文呼叫 `@MainActor` 單例屬編譯錯誤）。該結論由 Lead 寫入總報告，本線不重複驗證，亦未以任何建置結果作為本報告前提。

| 受審檔案 | mtime | sha256 前 8 | 行數 |
|---|---|---|---|
| `Data/Video/VideoRepository.swift` | 15:22:06 | `4fd89fc2` | 1298 |
| `Data/Comic/ComicRepository.swift` | 14:50:45 | `dc6a4f58` | 1044 |
| `Data/Comic/ComicSourceAdapter.swift` | 15:22:25 | `95a36da8` | 414 |
| `Data/Comic/ComicSourceCatalog.swift` | 00:10:14 | `b361dbe0` | 207 |
| `Data/Comic/ComicProtocolSecrets.swift` | 13:59:11 | `c289b0ee` | 134 |
| `Data/Comic/PicacgAuthStore.swift` | 19:00:04（前一日） | `bb2fa830` | 49 |
| `Data/Forum/ForumSourceCatalog.swift` | 15:24:44 | `e3615841` | 462 |
| `Data/Live/LiveSourceCatalog.swift` | 15:22:52 | `e48f765a` | 402 |
| `Data/Video/VideoSourceCatalog.swift` | 22:38:30（前一日） | `ec6b877a` | 276 |
| `Data/Video/PlaybackProgressStore.swift` | 15:20:35 | `b193e47e` | 181 |
| `Data/Video/WatchlistStore.swift` | 13:33:10 | `a9350d70` | 50 |
| `Services/HTMLVideoSourceAdapter.swift` | 15:25:21 | `4f8b12d2` | 493 |
| `Models/VideoItem.swift` | 00:10:14 | `e2ab4b52` | 48 |
| （交叉引用）`Models/TabItem.swift` | 15:39:25 | `65347a5d` | — |
| （交叉引用）`Services/LocalizationManager.swift` | 15:39:19（新建） | `c54619a9` | — |

基線說明：`ComicProtocolSecrets.swift`、`ComicSourceAdapter.swift`、`PicacgAuthStore.swift`、`PlaybackProgressStore.swift`、`WatchlistStore.swift` 為新增未跟蹤檔。本報告全部結論對應上述內容摘要，不對應任何 commit。

## 1. 範圍與方法（含逐檔覆蓋清單）

方法：
1. 以 `read` 逐檔全文通讀，不以抽查代替。
2. 以 `grep` 核算硬編碼截斷、`try? NSRegularExpression` 出現次數、強制解包、單例訪問點等可量化指標。
3. 以 `wc -l` 核對行數；以 `plistlib` 只讀取 `SourceSecrets.plist` 的鍵名與值長度，未複製任何密鑰值。
4. 以 `git diff`／`git status` 判斷「本輪已修復」聲明中的改動是否真實落入工作樹。
5. 對 UI 觸發路徑（分頁觸發、錯誤展示）只讀取相關片段作交叉引用，標明「部分讀」。

覆蓋清單：

| # | 檔案 | 行數 | 覆蓋 | 備註 |
|---|------|------|------|------|
| 1 | `ios/AuroraShelf/Data/Video/VideoRepository.swift` | 1298 | 全讀 | 分段 1–460 / 461–940 / 941–1298 |
| 2 | `ios/AuroraShelf/Data/Comic/ComicRepository.swift` | 1044 | 全讀 | 分段 1–520 / 521–1044 |
| 3 | `ios/AuroraShelf/Data/Comic/ComicSourceAdapter.swift` | 414 | 全讀 | |
| 4 | `ios/AuroraShelf/Data/Comic/ComicSourceCatalog.swift` | 207 | 全讀 | |
| 5 | `ios/AuroraShelf/Data/Comic/ComicProtocolSecrets.swift` | 134 | 全讀 | 新增未跟蹤檔 |
| 6 | `ios/AuroraShelf/Data/Comic/PicacgAuthStore.swift` | 49 | 全讀 | 新增未跟蹤檔 |
| 7 | `ios/AuroraShelf/Data/Forum/ForumSourceCatalog.swift` | 462 | 全讀 | 含 `ForumRepository`、`ForumPostParser` |
| 8 | `ios/AuroraShelf/Data/Live/LiveSourceCatalog.swift` | 402 | 全讀 | 含 `LiveRepository`、`LiveSourceAdapter` |
| 9 | `ios/AuroraShelf/Data/Video/VideoSourceCatalog.swift` | 276 | 全讀 | |
| 10 | `ios/AuroraShelf/Data/Video/PlaybackProgressStore.swift` | 181 | 全讀 | 新增未跟蹤檔 |
| 11 | `ios/AuroraShelf/Data/Video/WatchlistStore.swift` | 50 | 全讀 | 新增未跟蹤檔 |
| 12 | `ios/AuroraShelf/Services/HTMLVideoSourceAdapter.swift` | 493 | 全讀 | |
| 13 | `ios/AuroraShelf/Models/VideoItem.swift` | 48 | 全讀 | 交叉引用 |
| — | 合計 | 5058 | 13 檔全讀 | |

交叉引用（部分讀，僅用於確認觸發條件與展示行為）：

| 檔案:行 | 用途 |
|---|---|
| `ios/AuroraShelf/UI/Screens/HomeScreen.swift:105-200` | 分頁觸發條件（尾項 `onAppear`）、`feedError` 展示、「已加載全部內容」文案 |
| `ios/AuroraShelf/UI/Screens/ComicsScreen.swift:159-295` | 同上，漫畫側 |
| `ios/AuroraShelf/UI/Screens/ForumScreen.swift:88-112` | 論壇分頁與「继续加载」按鈕 |
| `ios/AuroraShelf/UI/Screens/NativePlayerView.swift:470-515` | 進度上報頻率與 `flushPendingPersist()` 呼叫點 |
| `ios/AuroraShelf/UI/Comic/ComicReaderView.swift:215-245` | 章節圖片載入與空章節文案 |
| `ios/AuroraShelfTests/ComicRepositoryTests.swift:488-555` | 既有分頁狀態測試覆蓋範圍 |

## 2. 發現總覽表

| ID | 嚴重度 | 檔案:行 | 一句話症狀 |
|----|--------|---------|-----------|
| A-01 | P1 | `Data/Video/VideoRepository.swift:166-169`；`Data/Comic/ComicRepository.swift:144-147` | 下一頁請求失敗即把 `canLoadMore` 永久置否並顯示「已加載全部內容」，無錯誤、無重試 |
| A-02 | P1 | `Data/Forum/ForumSourceCatalog.swift:115,125-126`；`:388-392` | 論壇標題含任何內嵌標籤即整條丟棄，全數丟棄時呈現空白列表且無錯誤（把解析失敗當成「無內容」） |
| A-03 | P2 | `Data/Video/VideoRepository.swift:38-39,172-217`；`Services/HTMLVideoSourceAdapter.swift:178-264` | `@MainActor` 令 HTML 解析與正則匹配全數回到主線程；`nonisolated` 標記未產生線程外執行 |
| A-04 | P2 | `Data/Comic/ComicSourceAdapter.swift:357-363` | `attribute()` 每次呼叫即重新編譯正則，一頁可達上千次編譯 |
| A-05 | P2 | `Data/Video/PlaybackProgressStore.swift:105-115,159-180` | 播放記錄無上限，每次落盤在主線程全量 JSON 編碼，成本隨歷史線性上升 |
| A-06 | P2 | `Data/Video/PlaybackProgressStore.swift:70-76` | 單筆記錄解碼失敗即靜默清空全部播放歷史，無錯誤上報 |
| A-07 | P2 | `Data/Video/VideoRepository.swift:26-36,453-482` | `StreamCache` 無上限、無 TTL、無失效；Beeg 簽名直鏈快取後在本進程內永不刷新 |
| A-08 | P2 | `Data/Video/VideoRepository.swift:1089-1096` | 依伺服器回傳的 `ad_duration_ms` 無上限 `Task.sleep`，可造成任意長度「正在連接」卡死 |
| A-09 | P2 | `Services/HTMLVideoSourceAdapter.swift:261` | 通用 HTML 源每頁硬截斷 24 條，單頁第 25 條起永久不可見 |
| A-10 | P2 | `Data/Forum/ForumSourceCatalog.swift:181` | 草榴板每頁硬截斷 20 帖（本輪新增），與「截斷已清零」聲明矛盾 |
| A-11 | P2 | `Data/Live/LiveSourceCatalog.swift:55-56,95-125` | `LiveRepository` 未納入 `@MainActor`，以 `@unchecked Sendable` 掩蓋未同步可變狀態 |
| A-12 | P2 | `Data/Live/LiveSourceCatalog.swift:184-187` | Stripchat 任一 tag 失敗即丟棄先前已成功的整批直播資料 |
| A-13 | P2 | `Data/Comic/ComicProtocolSecrets.swift:5-57` | 協議密鑰以明文 plist 隨 App bundle 分發；回退鏈含 iOS 不可達的 cwd／properties 分支 |
| A-14 | P2 | `Data/Video/VideoRepository.swift:838-851` | 華視封面與卡片以「全頁索引」對齊，任一卡片缺圖即令其後全部封面錯位 |
| A-15 | P2 | `Data/Comic/ComicRepository.swift:408` | JM 單本（`series` 為空）時偽造 `albumId` 作章節 ID，該作品必定無法閱讀 |
| A-16 | P2 | `Data/Comic/PicacgAuthStore.swift:40-46`；`ComicRepository.swift:229,712` | Keychain 寫入失敗被忽略；token 過期後只顯示 HTTP 401，無重新登入引導 |
| A-17 | P3 | `Data/Video/WatchlistStore.swift:34-38` | 「寫入校驗」以內存回讀比對，恆真，`persistenceFailed` 永不可達 |
| A-18 | P3 | `Data/Comic/ComicRepository.swift:526,543` | e-hentai 預覽分頁（50）與圖片連結（1000）雙重截斷，無任何提示 |
| A-19 | P3 | `Data/Live/LiveSourceCatalog.swift:287,303`；`UI/Player/AuroraPlayerSurface.swift:38` | 全工程仍有 3 處強制解包，與「只餘 5 處」的口徑不符（本範圍內無可觸發崩潰路徑） |
| A-20 | P3 | `Data/Video/PlaybackProgressStore.swift:96-103` | `updateProgress` 每次都把 `isManuallyDismissed`／`isManuallyCompleted` 重置為 false，兩條狀態路徑互相清除 |

分佈：P0 = 0、P1 = 2、P2 = 14、P3 = 4，合計 20 項。P0 為 0 的理由見第 6 節——本輪未找到可證明的崩潰、不可恢復資料損毀或可直接利用的安全漏洞。

## 3. 逐項發現

### A-01（P1）下一頁失敗即永久鎖死分頁，並宣稱「已加載全部內容」

症狀：用戶正常滾動到列表尾部觸發加載第 2 頁；若該次請求因網絡抖動、逾時（15 秒）或上游 5xx 而失敗，列表從此不再加載，底部文案由「正在加載更多」變為「已加載全部內容」，且沒有任何錯誤提示或重試入口。唯一恢復方式是切換分類／切換來源／重啟 App。

代碼證據 — `ios/AuroraShelf/Data/Video/VideoRepository.swift:166-169`：

```swift
        } catch {
            guard !Task.isCancelled, self.currentSource.id == targetSource.id, self.currentCategory == targetCategory else { return }
            self.canLoadMore = false
        }
```

`ios/AuroraShelf/Data/Comic/ComicRepository.swift:144-147` 為同一寫法：

```swift
        } catch {
            guard !Task.isCancelled, currentSource.id == targetSource.id, selectedCategory == targetCategory else { return }
            self.canLoadMore = false
        }
```

UI 據此直接展示「已加載全部內容」——`ios/AuroraShelf/UI/Screens/HomeScreen.swift:188`：

```swift
                                    } else if !repo.canLoadMore && !repo.feedVideos.isEmpty {
                                        Text("已加載全部內容")
```

`ios/AuroraShelf/UI/Screens/ComicsScreen.swift:290` 同款文案。而分頁觸發條件是「列表尾項出現」——`HomeScreen.swift:167-171`：

```swift
                                            .onAppear {
                                                if video.id == repo.feedVideos.last?.id {
                                                    Task {
                                                        await repo.loadNextPage()
                                                    }
```

尾項已在畫面上，`onAppear` 不會因重新滾動而必然再發；即使再發，`loadNextPage` 第一行的 `guard canLoadMore` 亦會直接返回。

根因：把「本次請求失敗」錯誤映射為「已到列表盡頭」。`canLoadMore` 同時承載「還有下一頁」與「上次請求失敗」兩種語義，而錯誤本身被 `catch` 吞掉，沒有寫入 `feedError`（Video）或 `errorMessage`（Comic）。

影響：單次網絡抖動即造成該來源／分類的列表永久只剩第 1 頁內容，且用戶被明確告知「已加載全部」，屬誤導性反饋。

對照：同類模組 `ForumRepository` 的寫法正確——`ios/AuroraShelf/Data/Forum/ForumSourceCatalog.swift:339-342`：

```swift
        } else if failures.isEmpty {
            hasMorePages = false
        }
        errorMessage = failures.isEmpty ? nil : failures.joined(separator: "\n")
```

失敗時保留 `hasMorePages = true` 並上報錯誤，UI 提供「继续加载」按鈕（`ForumScreen.swift:105-108`）。修法應向此對齊。

建議修法：
1. `loadNextPage` 的 `catch` 分支不修改 `canLoadMore`，改為設定 `nextPageError`；僅當成功回應且「去重後為空」時才置 `canLoadMore = false`。
2. 新增 `retryNextPage()` 或允許 `loadNextPage` 在 `nextPageError != nil` 時重入。
3. UI 在 `nextPageError != nil` 時顯示錯誤行 + 重試按鈕，不顯示「已加載全部內容」。

驗證方式（需 Lead 執行）：
- 建議新增 `ComicRepositoryTests.testNextPageFailureKeepsPaginationAlive()`：以 `URLProtocol` stub 令第 2 頁請求回 `500`，斷言 `canLoadMore == true` 且 `nextPageError != nil`。目前 `ComicRepository` 的 `session` 為 `private let` 且在 `init` 內構造（`ComicRepository.swift:55-61`），需先補一個 `init(session:)` 測試入口，或改用 `URLProtocol.registerClass` 全域攔截。
- 手動：`xcodebuild test -scheme AuroraShelf -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:AuroraShelfTests/ComicRepositoryTests`（僅由 Lead 執行，A 線未執行）。

### A-02（P1）論壇解析器對內嵌標籤零容忍，全數失配時呈現空白列表且無錯誤

症狀：進入論壇任一板塊，列表空白。用戶看不到任何錯誤，下拉刷新亦無變化——因為資料層把「解析失敗」判成了「無內容」。

代碼證據 — `ios/AuroraShelf/Data/Forum/ForumSourceCatalog.swift:115`：

```swift
        let pattern = #"<a\s+href="(viewthread\.php\?tid=(\d+)[^"]*)"[^>]*>([^<]+)</a>"#
```

第三個捕獲組 `([^<]+)` 要求 `</a>` 之前**不含任何 `<`**。只要上游把標題包進 `<span>`、加高亮 `<font>`、或在鏈接內插圖標，該條目即整條跳過；若整頁皆如此，`parse` 回傳空陣列。同檔 `:125` 另有 `guard title.count >= 4` 過濾，短標題（如三字標題）亦被丟棄。

`ForumRepository` 對空結果的處理——`ios/AuroraShelf/Data/Forum/ForumSourceCatalog.swift:388-392`：

```swift
            if !loaded.isEmpty || failures.isEmpty {
                self.posts = loaded
            }
            self.hasMorePages = !loaded.isEmpty
            self.errorMessage = failures.isEmpty ? nil : failures.joined(separator: "\n")
```

`failures` 為空（HTTP 200 正常回應、只是解析不出任何條目）時，`posts = []` 且 `errorMessage = nil`。UI 於是落到「有回應、無錯誤、無內容」的空白狀態，無錯誤橫幅、無重試按鈕。

根因：解析器把「結構不匹配」與「本頁確實無帖」視為同一結果；上層無法區分，於是沉默。

影響：上游一次前端改版或 A/B 實驗即可令論壇板塊永久空白，且不產生任何可診斷訊號（用戶無法回報「出錯」，只會認為「沒帖」）。

建議修法：
1. `parse` 不應以 HTML 結構作為硬性前提：改為先以 `(?is)<a\b([^>]*)>(.*?)</a>` 取錨點，再以 `attribute("href")` 判 `viewthread.php?tid=`，標題用 `stripHTML(body)`。
2. 上層加「回應非空但解析為 0 條」的可診斷訊號：`ForumFetchError.parseProducedNoItems`，令 `failures` 非空，於是 UI 顯示錯誤與重試。
3. `title.count >= 4` 建議改為 `!title.isEmpty`（該過濾同時在吞掉合法短標題）。

驗證方式（需 Lead 執行）：
- 新增 `ForumPostParserTests.testParserToleratesInlineMarkupInTitle()`：以含 `<span class="highlight">` 的 fixture HTML 斷言仍取出該帖。
- 新增 `testEmptyParseSurfacesError()`：以「HTTP 200 + 無匹配 HTML」的 stub 斷言 `repo.errorMessage != nil`。
- 現有 `ForumRepositoryTests.swift`（279 行）未覆蓋「解析為空」與「標題含內嵌標籤」兩情境，可用 `init(fetcher:)`（`ForumSourceCatalog.swift:303-306`）注入 fetcher 直接測，不需網絡。

### A-03（P2）`@MainActor` 令 HTML 解析與正則匹配全部回到主線程；`nonisolated` 未產生線程外執行

症狀：換源、換分類、上拉加載與全域搜尋期間，主線程出現與 HTML 體積成正比的卡頓；全域搜尋時 19 個來源的解析實質串行。

代碼證據 — `ios/AuroraShelf/Data/Video/VideoRepository.swift:38-39`：

```swift
@MainActor
public final class VideoRepository: ObservableObject {
```

`fetchVideosForSource`（`:172`）、`fetchHTMLSourceFeed`（`:197`）、`fetch91DongmanFeed`（`:232`）、`fetchHuangguoFeed`（`:486`）等**均未標 `nonisolated`**，因此皆為 MainActor 隔離；其內部在 `await` 之後的 `String(data:encoding:)`、`parseHTMLFeed`、`NSRegularExpression` 全部於主線程執行，例如 `:209-212`：

```swift
        guard let html = String(data: data, encoding: .utf8) else {
            throw VideoFeedError.malformedResponse(source: source.name)
        }
        let videos = HTMLVideoSourceAdapter.parseHTMLFeed(html, source: source, category: category, baseURL: HTMLVideoSourceAdapter.baseURL(for: source))
```

已標 `nonisolated` 的 5 個解析方法（`:256`、`:414`、`:515`、`:648`、`:831`）只是解除自身隔離要求；以同步方式從 MainActor 呼叫時，仍然在主線程執行（`nonisolated` 不做執行緒切換）。要真正離開主線程，必須以 `Task.detached`／`await` 到非隔離執行器，或把解析搬到獨立 `actor`。

全域搜尋同理——`:1199-1212`：

```swift
        let results = await withTaskGroup(of: [VideoItem]?.self, returning: [[VideoItem]?].self) { group in
            for source in searchableSources {
                group.addTask {
                    do {
                        return try await self.searchVideos(for: source, query: trimmed)
```

`searchVideos`（`:1230`）為 MainActor 方法，子任務每次呼叫都要跳回主 actor，CPU 解析完全串行；`withTaskGroup` 在此只對「網絡等待」有並行收益。

根因：把「狀態機需要主線程」與「解析需要 CPU」混為同一個隔離域。`@MainActor` 標在整個 `class` 上，連純計算一併鎖進主線程。

影響：一頁 100 KB–1 MB 的 HTML 在主線程做多次正則掃描，屬典型 50–300 ms 級卡頓來源；搜尋時更可能累積至秒級。此為本輪「升級為 `@MainActor`」帶來的副作用。

建議修法：
1. 把解析函式改為 `nonisolated` **並** 從 `Task.detached(priority: .userInitiated)` 或專用 `actor HTMLParser` 呼叫，令其真正離開主 actor。
2. `fetchVideosForSource` 拆分為「MainActor 狀態殼 + nonisolated 抓取／解析核心」兩層：抓取與解析在背景，僅把結果陣列交回 MainActor 賦值。
3. `searchVideos` 對同樣處置；`withTaskGroup` 的子任務改為呼叫 nonisolated 核心，才會有真正的並行。

驗證方式（需 Lead 執行）：
- `xcodebuild -scheme AuroraShelf -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test`（確認改動後不破壞既有測試）。
- 以 Instruments「Time Profiler」錄製換源 10 次，觀察 main thread 取樣佔比；或以 `os_signpost` 在 `parseHTMLFeed` 首尾打點，量測主線程阻塞時長。此項屬**需運行驗證**，本線未執行。

### A-04（P2）`ComicSourceAdapter.attribute()` 每次呼叫即重新編譯正則

症狀：漫畫列表解析時 CPU 尖峰與 `NSRegularExpression` 編譯次數成正比；同一頁 HTML 每多一個錨點，編譯次數再乘 6 以上。

代碼證據 — `ios/AuroraShelf/Data/Comic/ComicSourceAdapter.swift:357-363`：

```swift
    private static func attribute(_ name: String, in html: String) -> String? {
        let pattern = "(?i)\\b\(NSRegularExpression.escapedPattern(for: name))\\s*=\\s*(['\\\"])(.*?)\\1"
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
```

呼叫點密集——`:170`（`attribute("href", in: attributes)`）、`:184-188`（`title`／`alt`）、`:195-197`（`data-src`、`data-original`、`data-lazy-src`、`src` 各一次）。即每個錨點至少觸發 6 次 `NSRegularExpression(pattern:)` 編譯；一頁 200 個錨點約 1200 次編譯。

量化：本檔共 14 處 `try? NSRegularExpression(`（`grep -c` 實測），`static let` 預編譯正則 **0** 個。這直接否證第 4 節第 5 條聲明的「正則表達式全部靜態預編譯」。

根因：以「屬性名」作執行期參數拼裝 pattern，導致無法預編譯。可改為一個固定的通用屬性正則 + 執行期按名查表。

影響：漫畫列表首屏解析在舊裝置上可佔用數百毫秒（與 A-03 疊加時落在主線程）。屬效能隱患，不影響正確性。

建議修法：新增
```swift
private static let attributeRegex = try? NSRegularExpression(pattern: #"(?i)\b([a-z_:][-a-z0-9_:.]*)\s*=\s*(['"])(.*?)\2"#)
```
一次匹配全部屬性成 `[String: String]`（`HTMLVideoSourceAdapter.attributeValues(_:regex:)`，`Services/HTMLVideoSourceAdapter.swift:464-473` 已有可直接複用的實作），令 `attribute(_:in:)` 改為查表。

驗證方式：
- 靜態：`grep -c "try? NSRegularExpression" ios/AuroraShelf/Data/Comic/ComicSourceAdapter.swift`，修法後應降至個位數。
- 動態（需 Lead）：`ComicRepositoryTests` 既有 HTML 解析測試（`:121`、`:157`、`:202`、`:216` 等）改動後應全數通過；再以 Time Profiler 對比改動前後解析耗時。

### A-05（P2）播放記錄無上限、每次落盤在主線程全量編碼

症狀：長期使用後，任意一次進度落盤都要把整個歷史陣列 JSON 編碼一次並寫入 `UserDefaults`；歷史越長，主線程寫盤尖峰越高，且 `UserDefaults` 體積無界增長。

代碼證據 — `ios/AuroraShelf/Data/Video/PlaybackProgressStore.swift:104-115`：

```swift
        } else {
            let newRecord = PlaybackRecord(
                video: video,
                positionMs: positionMs,
                durationMs: durationMs
            )
            list.insert(newRecord, at: 0)
        }

        self.records = list
        scheduleThrottledPersist()
```

全檔無任何淘汰、上限或壓縮邏輯（`grep -n "records"` 共 11 處，無 `removeAll`／`prefix`／`maxCount`）。落盤為全量編碼——`:175-180`：

```swift
    private func persistToDisk(_ newRecords: [PlaybackRecord]) {
        lastDiskPersistTime = Date()
        if let data = try? JSONEncoder().encode(newRecords) {
            defaults.set(data, forKey: Self.storageKey)
        }
    }
```

`PlaybackRecord` 內嵌完整 `VideoItem`（`:9` `public let video: VideoItem`），單筆約 300–600 B。1000 部影片的歷史即約 0.5 MB，且每次落盤皆全量重編碼。

頻率：`NativePlayerView.swift:476-495` 以 0.5 秒週期的 periodic observer 上報，條件 `Int(self.currentTime) % 3 == 0` 使每 3 秒觸發 2 次；配合 `scheduleThrottledPersist` 的 5 秒防抖，實測落盤間隔約 5–9 秒（推算，非實測）。

根因：以「整份快照」作持久化單位，且缺少容量治理。

影響：主線程編碼時間隨歷史線性增長（1000 條時單次編碼約數毫秒至數十毫秒）；`UserDefaults` 不適合承載數百 KB 級資料；長期使用者的播放歷史最終會拖慢播放器頁面。屬效能與健壯性隱患。

建議修法：
1. 上限治理：保留最近 N 條（如 200）已完結／已移除記錄，`upForNext` 只保留最近 50 條活躍記錄。
2. 改為「按記錄鍵分檔」或改用 SQLite／Core Data，落盤只寫變更項。
3. 編碼移出主線程（`nonisolated` 編碼 + 主線程僅賦值）。

驗證方式（需 Lead）：
- 新增 `PlaybackProgressStoreTests.testRecordCountIsBounded()`：連續 `updateProgress` 1000 個不同 video，斷言 `records.count <= cap`。
- 以 `XCTMetric`（`XCTClockMetric`）量測 `updateProgress` 1000 次後的單次 `flushPendingPersist()` 耗時。

### A-06（P2）單筆記錄解碼失敗即靜默清空全部播放歷史

症狀：App 版本升級（`PlaybackRecord` 或 `VideoItem` 欄位增刪）或 `UserDefaults` 內單筆記錄損壞時，全部播放歷史在啟動時被清零，用戶不會收到任何提示。

代碼證據 — `ios/AuroraShelf/Data/Video/PlaybackProgressStore.swift:70-76`：

```swift
        if let data = defaults.data(forKey: Self.storageKey) {
            do {
                self.records = try JSONDecoder().decode([PlaybackRecord].self, from: data)
            } catch {
                self.records = []
            }
        }
```

對照 `WatchlistStore.swift:17-21`，同類情境至少會設定 `loadError`：

```swift
        do {
            items = try JSONDecoder().decode([VideoItem].self, from: data)
        } catch {
            loadError = "片單数据无法读取：\(error.localizedDescription)"
        }
```

（附帶：`WatchlistStore` 只把錯誤寫進一個從未展示給用戶的 `@Published loadError`，見 `grep -rn "loadError" ios/AuroraShelf` 只命中定義處——錯誤仍然不會到達用戶，但至少可被測試與診斷讀到。）

根因：以單一 JSON blob 承載全量記錄，解碼為「全有或全無」；錯誤分支直接賦值空陣列。

影響：一次性、不可恢復的全量歷史丟失。當前 `VideoItem` 剛於本輪新增 `Codable`（`git diff` 顯示 `-public struct VideoItem: Identifiable, Hashable, Sendable` → `+... Codable, Sendable`），屬首次落盤，尚無升級風險；但下一次對 `VideoItem`／`PlaybackRecord` 增刪非可選欄位時，此路徑會即時生效。

建議修法：
1. 逐筆解碼：先解為 `[JSONValue]` 再逐條 `decode`，壞記錄跳過並記錄數量。
2. 版本化：storage key 加 schema 版本（現為 `netgrey.playback_progress.v1`），加 `migrate(from:)`。
3. 解碼失敗不得靜默：保留原始 blob 副本（`storageKey + ".corrupt"`），並以 `@Published lastError` 暴露。

驗證方式（需 Lead）：
- 新增 `PlaybackProgressStoreTests.testCorruptPayloadKeepsDecodableRecords()`：以 `UserDefaults(suiteName:)` 灌入「1 筆合法 + 1 筆缺欄位」的 JSON，斷言合法記錄仍在。
- 新增 `testSchemaVersionMigration()`。

### A-07（P2）`StreamCache` 無上限、無 TTL、無失效；Beeg 簽名直鏈永不刷新

症狀：Beeg 播放流一旦解析成功即在本進程生命週期內固定；若上游簽名 URL 具時效，重播同一影片可能直接拿到過期網址而黑屏，且無重試／刷新路徑。

代碼證據 — `ios/AuroraShelf/Data/Video/VideoRepository.swift:26-36`：

```swift
private actor StreamCache {
    private var streamMap: [String: URL] = [:]
    
    func get(key: String) -> URL? {
        streamMap[key]
    }
    
    func set(key: String, url: URL) {
        streamMap[key] = url
    }
}
```

使用點僅兩處——`:453-457` 與 `:480`：

```swift
    public func resolveBeegStream(fileId: String) async throws -> URL {
        let cacheKey = "beeg-\(fileId)"
        if let cached = await cache.get(key: cacheKey) {
            return cached
        }
```

評估「是否真去重、key 是否含 source/category」：
- 去重有效（actor 序列化讀寫，無競態）；
- key **不含** source／category，但目前只有 Beeg 一處使用，且 `fileId` 為 Beeg 專屬數值 ID（`resolvePlayback:899-909` 從 `-0<id>` 路徑解析），故現階段無跨源碰撞；
- 無上限、無淘汰：長期使用下字典條目數 = 看過的 Beeg 影片數，屬無界增長（每條僅 `URL` + `String`，量級小，故非主要風險）；
- 無 TTL／無失效：`streamMap` 永不刪除，且 `resolveBeegStream` 對快取值不做可用性校驗。

影響：若上游 `https://store.externulls.com/video/play_url/<id>` 回傳的是帶 `expires`／`sign` 的短期直鏈（該端點的常見行為），則同一影片第二次播放會命中過期 URL。此點屬**需運行驗證**：需實際抓一次回應並檢查 URL 是否含時效參數。

建議修法：`StreamCache` 增加 `expiresAt`，命中時校驗有效期；`resolveBeegStream` 在播放失敗回調時 `invalidate(key:)` 並重解一次。

驗證方式（需運行驗證，Lead）：
- `curl -s "https://store.externulls.com/video/play_url/<fileId>"` 觀察回傳路徑是否含時效簽名（A 線未發網絡請求）。
- 對應單元測試：新增 `StreamCacheTests.testExpiredEntryIsNotReturned()`。

### A-08（P2）華視解析依伺服器回傳的 `ad_duration_ms` 無上限休眠

症狀：點擊華視影片後播放器可能長時間停在「正在連接 4K 直連通道…」，無法取消，只能退出播放器。

代碼證據 — `ios/AuroraShelf/Data/Video/VideoRepository.swift:1089-1096`：

```swift
        if let root = try? JSONSerialization.jsonObject(with: nextData) as? [String: Any],
           let payload = root["data"] as? [String: Any],
           let adDuration = (payload["ad_duration_ms"] as? NSNumber)?.intValue {
            let remainingWait = max(0, adDuration - 2_000)
            if remainingWait > 0 {
                try await Task.sleep(for: .milliseconds(remainingWait))
            }
        }
```

`adDuration` 直接取自上游 JSON，無上限夾制。若上游回傳 `600000`（10 分鐘，或其 CDN 錯誤頁剛好帶大數值），播放器即休眠 10 分鐘。`Task.sleep` 只會在整個 Task 被取消時拋 `CancellationError`；`resolveTask` 由 `NativePlayerView` 持有並在退出時取消，因此「退出播放器」可解除，但停留期間無任何進度或逾時提示。

影響：用戶可見的「假死」狀態；對廣告倒數的正常情境（約 15–20 秒）亦無提示，體驗與故障無法區分。

建議修法：
```swift
let remainingWait = min(max(0, adDuration - 2_000), 20_000)
```
並在 `NativePlayerView` 顯示倒數/可取消提示。

驗證方式（需運行驗證，Lead）：以 Charles／mitmproxy 改寫 `/Player/next` 回應的 `ad_duration_ms` 為 `600000`，觀察播放器狀態；或為 `resolveHuavodStream` 抽出 `clampAdWait(_:)` 純函式並加單元測試斷言上限。

### A-09（P2）通用 HTML 源每頁硬截斷 24 條

症狀：MissAV／Pornhub／SpankBang／xHamster 等 12 個 HTML 適配源，單頁第 25 條起永久不可見。

代碼證據 — `ios/AuroraShelf/Services/HTMLVideoSourceAdapter.swift:249-262`：

```swift
            guard (!title.isEmpty || !cover.isEmpty), seen.insert(itemURL.absoluteString).inserted else { continue }

            items.append(VideoItem(
                id: "\(source.id)-\(itemURL.absoluteString)",
                ...
            ))
            if items.count == 24 { break }
        }
        return items
```

`loadNextPage` 每頁各自再截 24，故第 25 條以後的內容並非「延後出現」，而是**永久缺失**。

根因：以固定條數代替分頁語義。上游單頁條數由站點決定（Pornhub 約 30–40、SpankBang 約 36），與 24 無對應關係。

影響：目錄內容缺失，用戶無感（不會報錯，只會覺得「就這麼多」）。與 A-01 疊加時，缺失會被「已加載全部內容」進一步掩蓋。

建議修法：移除 `if items.count == 24 { break }`；如需防護，改為 `maxItems = 200` 之類的資源上限，並在 `docs` 註明為安全上限而非內容策略。同時 `feedURL` 內 `eporner` 的 `per_page=24`（`:71`）應改為與分頁一致的常數。

驗證方式（需 Lead）：
- 新增 `HTMLVideoSourceAdapterTests.testParseDoesNotCapAt24()`：以 40 個合法 `<a>` 的 fixture 斷言 `items.count == 40`。
- 手動（需網絡）：對任一源抓首頁 HTML，計 `class="..."` 影片卡數量，與 App 顯示數量對比。

### A-10（P2）草榴板每頁硬截斷 20 帖（本輪新增，與聲明矛盾）

症狀：草榴技術區每頁最多顯示 20 帖，第 21 帖起不可見。

代碼證據 — `ios/AuroraShelf/Data/Forum/ForumSourceCatalog.swift:181`：

```swift
        }.prefix(20).map { $0 }
```

`git diff` 證實此行是**本輪新增**，而非殘留：

```
187:+        }.prefix(20).map { $0 }
```

與 `.ai/PROJECT_STATE.md` 第 3 條「各源截斷魔數清零……`ForumSourceCatalog`（20帖）……的硬編碼截斷」直接矛盾——本輪不但沒有清除，反而新增。

影響：論壇內容缺失；且因 A-02 的靜默特性，用戶無從察覺。

建議修法：刪除 `.prefix(20)`。若原意為防護單頁過大，改為 `min(200)` 並在代碼註明。

驗證方式（需 Lead）：新增 `ForumPostParserTests.testT66YParserReturnsMoreThan20Entries()`，以 30 個合法 `htm_data` 連結的 fixture 斷言 `count == 30`。現有 `ForumRepositoryTests.swift` 未覆蓋 `parseT66Y`。

### A-11（P2）`LiveRepository` 未納入 `@MainActor`，以 `@unchecked Sendable` 掩蓋未同步狀態

症狀：`liveStreams`／`isLoading`／`loadErrorMessage` 的寫入路徑中，有一部分（`startLoading()` 開頭）不在主線程隔離內；`currentLoadTask`、`activeRequestID` 無任何鎖保護。

代碼證據 — `ios/AuroraShelf/Data/Live/LiveSourceCatalog.swift:55-56`：

```swift
public final class LiveRepository: ObservableObject, @unchecked Sendable {
    public static let shared = LiveRepository()
```

`startLoading()` 在隔離域之外直接寫 `@Published`——`:95-105`：

```swift
    private func startLoading() -> Task<Void, Never> {
        currentLoadTask?.cancel()
        let requestID = UUID()
        activeRequestID = requestID
        let selectedSourceID = self.selectedSourceID
        let selectedGender = self.selectedGender
        self.isLoading = true
        self.loadErrorMessage = nil

        let task = Task { [weak self] in
```

而結果寫入則包在 `MainActor.run`（`:110-121`），同一組狀態被兩種隔離域寫入。`@unchecked Sendable` 只是關閉編譯器檢查，未提供同步。

可觸發條件：目前所有呼叫點（`init`、`loadStreams()`、`selectSource(_:)`、`refreshStreams()`）實務上都由主線程進入（SwiftUI 事件或 `.refreshable`），故當前不一定表現為 bug；但 `refreshStreams()` 是 `public async`，任何 `Task.detached`／背景 context 呼叫即構成資料競態，且 SwiftUI 會輸出「Publishing changes from background threads is not allowed」運行時警告。

影響：屬潛在競態與一致性風險；亦令「全工程數據層狀態機隔離標準統一」不成立（見第 4 節）。

建議修法：比照 `VideoRepository`／`ComicRepository`／`ForumRepository`，改為 `@MainActor public final class LiveRepository: ObservableObject`，移除 `@unchecked Sendable`，把 `startLoading` 的狀態寫入與 `fetchSelectedSources` 的結果套用都收進 MainActor。

驗證方式（需 Lead）：
- 以 `-strict-concurrency=complete` 編譯該檔應 0 警告（Swift 6 語言模式已於 `ios/project.yml` 設定 `SWIFT_VERSION: "6.0"`，跑一次 `xcodebuild build` 觀察此檔是否仍有 `Sendable` 相關診斷）。
- 新增 `LiveRepositoryTests`：背景 context 呼叫 `refreshStreams()` 後斷言狀態一致（需先改為 MainActor 才有意義）。

### A-12（P2）Stripchat 任一 tag 失敗即丟棄整批已成功資料

症狀：選擇「全部」性別時，4 個 tag（girls／couples／men／trans）逐一請求；只要第 2 個 tag 逾時或 403，先前 tag 已取得的幾十條直播全部作廢，整個 Stripchat 來源顯示失敗。

代碼證據 — `ios/AuroraShelf/Data/Live/LiveSourceCatalog.swift:183-188`：

```swift
        var streams: [LiveStreamItem] = []
        for tag in primaryTags {
            streams.append(contentsOf: try await fetchStripchatFeed(primaryTag: tag))
        }
        return streams.uniqued(by: \.id)
```

`try await` 在 `for` 迴圈內，任一失敗即拋出，`streams` 已累積內容被丟棄。

影響：分頁／分組請求的局部失敗被放大為整體失敗，直接降低直播板塊可用性。

建議修法：改為 `for tag in tags { do { ... } catch { failures.append(...) } }`，回傳 `(streams, failures)` 供上層組合警告文案（`fetchSelectedSources` 已有此模式，`:132-149`）。

驗證方式（需 Lead）：新增 `LiveSourceAdapterTests` 用例，注入令第 2 個 tag 拋錯的 fetcher（需先把 `fetchStripchatFeed` 參數化或抽出 protocol），斷言回傳仍含第 1 個 tag 的資料。現有 `LiveSourceAdapterTests.swift` 只覆蓋 URL 與 JSON 解析。

### A-13（P2）協議密鑰以明文 plist 隨 App bundle 分發；回退鏈含 iOS 不可達分支

症狀與事實：
1. `ios/AuroraShelf/Resources/SourceSecrets.plist` 存在於工作樹，含 5 個鍵（`JM_DATA_SECRET`、`JM_TOKEN_SECRET`、`JM_TOKEN_SECRET_2`、`PICACG_API_KEY`、`PICACG_SIGNATURE_SECRET`），長度分別為 16／16／17／29／70，均非佔位值（僅讀取鍵名與長度，未複製值）。
2. 該檔已被 `.gitignore:5` 忽略，**無版本庫洩漏**；但 `ios/project.yml:12-16` 以整個 `AuroraShelf` 目錄作 sources，故該 plist 會被複製進 `.app`——實測 `ios/build/Build/Products/Debug-iphonesimulator/AuroraShelf.app/SourceSecrets.plist` 存在（309 bytes，2026年11月4日 20:27）。發佈 IPA 同理，無編譯期排除機制。

代碼證據 — `ios/AuroraShelf/Data/Comic/ComicProtocolSecrets.swift:5-15`（bundle 明文讀取，優先級最高）：

```swift
    private static let values: [String: String] = {
        if let url = Bundle.main.url(forResource: "SourceSecrets", withExtension: "plist"),
           let values = NSDictionary(contentsOf: url) as? [String: String], !values.isEmpty {
            return values
        }
```

回退鏈（`:16-55`）依次為：`Bundle.allBundles` → `ProcessInfo.processInfo.environment` → `FileManager.default.currentDirectoryPath` 下的 4 個相對路徑 → `source-secrets.properties` 兩個相對路徑。其中第 3、4 級在 iOS 沙盒內不可能存在（App 的 cwd 為容器根，無 `ios/AuroraShelf/Resources/...`），屬桌面開發腳手架殘留的死代碼。

評估任務所問的三點：
- **是否洩漏到日誌**：未發現。`grep -rn "print(\|os_log\|NSLog"` 於 `Data/` 只命中 `ComicRepository.swift:146`（`Secret` 無關），`ComicProtocolSecrets` 無任何輸出。
- **是否 bundle 內明文分發**：是（上述實測）。
- **找不到時是否靜默降級為「源不可用」而無提示**：**否**。缺少配置時會拋出具體錯誤並經 `errorMessage` 顯示——`ComicRepository.swift:294-296`：

  ```swift
        guard ComicProtocolSecrets.hasJMConfiguration else {
            throw ComicFetchError.authenticationRequired("此构建未配置禁漫协议参数。")
        }
  ```

  但 `ComicSourceCatalog.allSources`（`ComicSourceCatalog.swift:185-194`）仍把 JM／Picacg 列為 `supportedSources`，故 UI 會展示可點擊但必然失敗的來源。此為 A-13 附帶的 P3 級體驗缺口（見第 5 節）。

根因：客戶端必須自行簽名的協議，其密鑰本質上無法真正保密；此為設計取捨，不是實作錯誤。真正的可改進點是「未做編譯期注入與輪換」與「回退鏈把開發便利留在正式碼」。

影響：任何取得 IPA 的分析者可在數秒內提取上述密鑰，進而偽造 JM／Picacg 客戶端請求。屬可接受的殘餘風險（前提是產品接受），但必須落在風險登記中，不能以「已用 .gitignore 保護」作結。

建議修法（按優先序）：
1. 在 `docs/` 明示「此為客戶端公開密鑰，無保密性」，並納入發布風險登記。
2. 改為 `xcconfig`／建置腳本注入（`Info.plist` 或編譯期常量），令 plist 不進 repo 且可按版本輪換；如必須隨包，至少加一層輕量混淆並在每次發版輪換。
3. 刪除 `:23-55`（cwd／properties）在 iOS 目標中的死分支，或加 `#if DEBUG` 限縮。
4. `ProcessInfo.environment`（`:16-21`）建議同樣限 `#if DEBUG`，避免正式包的環境變數可被覆寫。

驗證方式（需 Lead／發佈流程）：
- `plutil -p ios/build/Build/Products/Release-iphoneos/AuroraShelf.app/SourceSecrets.plist`（僅在 Lead 建 Release 後執行）。
- `git ls-files | grep SourceSecrets` 應為空（已確認：`git ls-files ios/AuroraShelf/Resources/` 只列出 `Info.plist`、`intro_film.mp4`、`www`）。
- 打包後 `unzip -l <ipa> | grep SourceSecrets` 確認是否隨包（預期會，故需產品決策而非「修 bug」）。

### A-14（P2）華視封面與卡片以「全頁索引」對齊，任一卡片缺圖即全體錯位

症狀：華視影院列表中，某部劇的封面可能顯示為另一部劇的海報；一旦上游在卡片區之前插入任何帶 `gen-movie-img` class 的圖片（banner、推薦位），錯位從該處起擴散至整頁。

代碼證據 — `ios/AuroraShelf/Data/Video/VideoRepository.swift:838-851`：

```swift
        let imgPattern = #"<img[^>]*class="[^"]*gen-movie-img[^"]*"[^>]*src="([^"]+)""#
        let imgRegex = try? NSRegularExpression(pattern: imgPattern)
        let imgMatches = imgRegex?.matches(in: html, options: [], range: NSRange(location: 0, length: nsHtml.length)) ?? []

        var index = 0
        for m in matches {
            let vodId = nsHtml.substring(with: m.range(at: 2))
            let title = nsHtml.substring(with: m.range(at: 3))
            
            var coverUrl = ""
            if index < imgMatches.count {
                let rawImg = nsHtml.substring(with: imgMatches[index].range(at: 1))
```

封面匹配 `imgMatches` 與卡片匹配 `matches` 是兩個獨立的全頁正則結果，僅以「第 i 個」對齊，不驗證兩者是否屬於同一卡片（未取卡片上下文 HTML）。`:848` 的 `index < imgMatches.count` 只防越界，不防錯位。

影響：內容錯配——用戶點開的詳情與看到的封面不一致。不會報錯，故難被用戶準確回報。屬資料正確性缺陷。

建議修法：改為「先切卡片區塊，再在區塊內取圖」：以卡片 regex 的 `range(at: 0)` 前後擴張至最近容器邊界（可複用 `Services/HTMLVideoSourceAdapter.swift:450-462` 的 `cardContext`），再於該片段內匹配 `gen-movie-img`。若片段內無圖則留空，不再以全局索引補位。

驗證方式（需運行驗證，Lead）：
- 抓 `https://huavod.com/vodshow/1/page/1.html`，以 Python 分別計算「卡片數」與「gen-movie-img 數」，若前者 ≠ 後者即證明錯位已發生（A 線未發網絡請求）。
- 單元測試：把實測 HTML 存為 fixture，斷言每張封面的 URL 字串包含對應 `vodId` 或位於同一卡片片段內。

### A-15（P2）JM 單本作品偽造 `albumId` 作章節 ID，該類作品必定無法閱讀

症狀：打開 JM 的單本（無 `series`）作品時，章節列表顯示一條「完整内容」；點擊後閱讀器提示「此章节暂时没有可显示的图片。」

代碼證據 — `ios/AuroraShelf/Data/Comic/ComicRepository.swift:408`：

```swift
        let updated = ComicItem(id: comic.id, sourceId: comic.sourceId, title: item["name"] as? String ?? comic.title, author: author.isEmpty ? comic.author : author, coverUrl: "https://cdn-msp.jmapiproxy1.cc/media/albums/\(comic.id)_3x4.jpg", category: comic.category, tags: item["tags"] as? [String] ?? comic.tags, chapters: chapters.isEmpty ? [ComicChapter(id: comic.id, title: "完整内容")] : chapters, latestChapterTitle: chapters.first?.title ?? "完整内容")
```

`chapters` 為空時，以**專輯 ID**（`comic.id`）充當**章節 ID**。而圖片抓取路徑 — `:472-473`：

```swift
    private func fetchJMPages(chapterId: String) async throws -> [ComicPage] {
        let item = try await jmRequest(path: "chapter?id=\(chapterId)", secretKey: "JM_TOKEN_SECRET")
```

`chapter` 端點期望的是 photo（章節）ID，傳專輯 ID 會得到 `images` 為空的結果 → `files = []` → `pages = []` → `ComicReaderView.swift:235-236` 顯示「此章节暂时没有可显示的图片。」

影響：JM 單本類作品（`series` 為空是單本相簿的正常形態）完全不可讀，且用戶會被引導點擊一個必然失敗的章節。

建議修法：單本作品應改為直接讀 `photo`：以 `fetchJMPages(chapterId: comic.id)` 之餘，先確認 `chapter?id=` 回應是否回傳 `images` 非空；若非空可直接用，否則改走 `album` 內 `series[0].id`。更穩妥者：`chapters` 為空時不偽造章節，改以 `latestChapterTitle = ""` 且閱讀器入口改為「直接載入專輯」。

驗證方式（需運行驗證，Lead）：對一個 `series` 為空的 JM 專輯（例如測試檔 `ComicRepositoryTests.swift:371` 的 live 測試所涵蓋的樣本）呼叫 `album?id=`，確認回傳結構，再核對 `chapter?id=<albumId>` 是否回 `images: []`。

### A-16（P2）Picacg 憑據：Keychain 寫入失敗被忽略；token 過期後只顯示 HTTP 401

症狀：
(a) 登入看似成功，但若 Keychain 寫入失敗，重啟 App 後需重新登入，且用戶與日誌都無從得知；
(b) token 過期後，列表統一顯示「漫畫源请求失败（HTTP 401）。」，不提示重新登入。

代碼證據 (a) — `ios/AuroraShelf/Data/Comic/PicacgAuthStore.swift:40-46`：

```swift
            let status = SecItemUpdate(query as CFDictionary, update as CFDictionary)
            if status == errSecItemNotFound {
                var insert = query
                insert[kSecValueData as String] = data
                insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
                SecItemAdd(insert as CFDictionary, nil)
            }
```

`SecItemUpdate` 的 `status` 除 `errSecItemNotFound` 外全被忽略；`SecItemAdd` 的返回值直接被丟棄。寫入失敗時 `token` 的 getter 仍讀回舊值或空字串，而 `ComicRepository.loginPicacg`（`:152-164`）已把 token 寫入並返回成功。

證據 (b) — `ComicRepository.swift:229-230` 只在 token **空**時要求登入：

```swift
        let token = PicacgAuthStore.shared.token
        guard !token.isEmpty else { throw ComicFetchError.authenticationRequired("哔咔需要先登录。") }
```

過期 token 非空，於是走到 `:712`：

```swift
            guard (200...299).contains(http.statusCode) else { throw ComicFetchError.httpStatus(http.statusCode) }
```

`:22` 把 `httpStatus` 顯示為「漫畫源请求失败（HTTP 401）。」。全工程無任何 401 攔截——`grep -rn "401" ios/AuroraShelf/Data/Comic/*.swift` 無命中；`signOutPicacg`（`:166-169`）亦無任何 UI 呼叫點（`grep -rn "signOutPicacg"` 只命中定義）。

附帶觀察：`isPicacgAuthenticated`（`:150`）為普通計算屬性而非 `@Published`，登入成功後不會觸發 `ComicsScreen` 重繪，只能靠 `errorMessage` 的變化間接觸發。

影響：(a) 低機率但完全靜默的登入失效；(b) 高機率（token 有時效）且用戶無法自助恢復——必須卸載重裝或找到隱藏入口，屬用戶可見功能缺陷。

建議修法：
1. `PicacgAuthStore.token` setter 全校驗 `SecItemUpdate`／`SecItemAdd` 的 `status`，失敗即拋錯或回傳 Bool，令 `loginPicacg` 能如實回報。
2. `request` 遇到 `401` 時拋 `authenticationRequired("哔咔登入已過期，請重新登入。")`，並清空 Keychain token；`ComicsScreen` 既有 `isPicacgAuthenticated == false` 分支（`:159`）即會顯示登入表單。
3. 把 token 讀取結果緩存為 `@Published`，避免每次 `ComicRepository` 判斷都同步讀 Keychain（`:150`、`:228`、`:329`、`:413-414`、`:495`）。

驗證方式（需 Lead）：
- 新增 `PicacgAuthStoreTests`：注入一個會令 `SecItemAdd` 失敗的 keychain（或以 `kSecUseDataProtectionKeychain` 的測試帳號模擬），斷言 setter 回報失敗。
- 新增 `ComicRepositoryTests.testExpiredPicacgTokenSurfacesReauthentication()`：stub `401`，斷言錯誤文案含「重新登入」。

### A-17（P3）`WatchlistStore` 的寫入校驗形同虛設

症狀：`persistenceFailed` 這個錯誤永遠不會出現；磁盤寫入真正失敗（空間不足、資料保護鎖定）時，用戶仍看到「已加入片單」。

代碼證據 — `ios/AuroraShelf/Data/Video/WatchlistStore.swift:34-39`：

```swift
        let data = try JSONEncoder().encode(updated)
        defaults.set(data, forKey: Self.storageKey)
        guard defaults.data(forKey: Self.storageKey) == data else {
            throw WatchlistStoreError.persistenceFailed
        }
        items = updated
```

`UserDefaults.set` 先寫入記憶體快取，同一進程內 `data(forKey:)` 必然回讀到剛寫入的值，故 guard 恆真。此校驗只證明「內存寫入成功」，與磁盤無關。`:44-50` 的 `persistenceFailed` 文案「无法保存片单，请检查设备储存空间后重试。」因此永不可達。

影響：磁盤寫入失敗被靜默吞掉（非資料損壞，因 `UserDefaults` 寫入本身是整塊原子替換），用戶在下次啟動時才會發現片單丟失。屬健壯性缺口。

建議修法：`defaults.synchronize()` 已不建議使用，務實做法是改為寫檔 + 原子替換（`Data.write(to:options:.atomic)`），以 `do/catch` 如實上報；或接受 UserDefaults 語義並刪除該誤導性的 guard 與錯誤型別。

驗證方式（需 Lead）：新增 `WatchlistStoreTests.testSaveFailureSurfacesError()`；現有 `WatchlistStoreTests.swift`（31 行）只覆蓋基本增刪。

### A-18（P3）e-hentai 預覽分頁與圖片連結雙重截斷，無任何提示

症狀與事實：畫廊頁數超過 1000 張時，第 1001 張起不出現；預覽頁數上限 50（即最多 1000 張連結）。

代碼證據 — `ios/AuroraShelf/Data/Comic/ComicRepository.swift:526`：

```swift
        let previewPageCount = min(max((pageCount + 19) / 20, 1), 50)
```

`:543`：

```swift
        return imagePageURLs.prefix(1_000).map { pageURL in
```

同檔另有：`fetchPicacgDetails` 頁數上限 50（`:423`）、`fetchPicacgPages` 上限 100（`:500`）、`fetchIkmmhPages` 上限 1000（`:846`）、`ComicSourceAdapter.parseChapters` 500（`ComicSourceAdapter.swift:218`）、`parseImagePages` 500（`:311`）、`eightMusesPageURLs` 1000（`:261`）。這些屬「安全上限」而非內容策略，量級合理，故僅列為 P3 並建議在超出時提示「已截斷」。

影響：超長畫廊靜默缺失尾段內容。

建議修法：沿用現有 `pageCount` 判斷是否觸及上限，觸及時在 `ComicPage` 或 `ComicChapter` 上帶 `isTruncated` 標記，由閱讀器提示。

驗證方式：單元測試建構 `pageCount = 2000` 的假 HTML fixture，斷言回傳 `1000` 且標記 `isTruncated == true`。

### A-19（P3）強制解包 3 處，與「只餘 5 處」聲明口徑不符

實測（`grep -rnE "\)!([^=]|$)|\.url!|\.first!|\.last!| as! |try!" ios/AuroraShelf --include=*.swift`，排除 `WKNavigation!` 協議簽名）：

| 位置 | 代碼 | 可觸發性 |
|---|---|---|
| `Data/Live/LiveSourceCatalog.swift:287` | `var components = URLComponents(string: "https://zh.stripchat.com/api/front/models")!` | 字面量常量，**不可**由外部輸入觸發 |
| `Data/Live/LiveSourceCatalog.swift:303` | `return components.url!` | 元件由上表構造且 query 值經 `URLQueryItem` 轉義，**理論上**不會 nil |
| `UI/Player/AuroraPlayerSurface.swift:38` | `layer as! AVPlayerLayer` | 若宿主層型別不符即崩潰（需運行驗證；`layerClass` 已回傳 `AVPlayerLayer`，故風險低） |

`URL(string:)!` 形式在 `ios/AuroraShelf` 內實測 **0 處**（`grep -rnE "URL\(string:.*\)!"` 無命中）。故「全項目只餘 5 處」的統計口徑與本線量測結果不一致（本線量得 3 處、且不含 `URL(string:)`），需與聲明方核對計數範圍（是否含 Android 端／測試檔／`try!`）。

影響：無可證明的崩潰路徑；此項列入 P3 僅為記錄口徑差異，避免下輪重複排查。

建議修法：`LiveSourceCatalog.swift:287-303` 改為 `guard let components = ..., let url = components.url else { return fallback }`；`:38` 改為 `guard let layer = layer as? AVPlayerLayer else { return }`。

### A-20（P3）`updateProgress` 每次重置兩個手動標記，與 `markCompleted`／`removeFromUpNext` 互相清除

症狀：用戶在「待播清單」手動移除或標記已看完後，只要該影片再有**任何一次**進度寫入（包括同一播放器實例的收尾寫入、或日後重新播放），兩個手動標記都會被清成 false，該影片重新出現在待播清單。

代碼證據 — `ios/AuroraShelf/Data/Video/PlaybackProgressStore.swift:96-103`：

```swift
        if let index = list.firstIndex(where: { $0.videoId == video.id && $0.sourceName == video.sourceName }) {
            var existing = list[index]
            existing.positionMs = positionMs
            existing.durationMs = durationMs
            existing.lastUpdated = Date()
            existing.isManuallyDismissed = false
            existing.isManuallyCompleted = false
            list[index] = existing
        }
```

對照 —— `:121`（`markCompleted` 置 true）、`:139`（`removeFromUpNext` 置 true）、`:48`（`shouldAppearInUpNext` 以兩者為否決條件）：

```swift
        guard !isManuallyDismissed && !isManuallyCompleted && !isAutoCompleted else {
```

呼叫點（`grep -rn "markCompleted\|removeFromUpNext"`）：`UI/Components/UpNextShelf.swift:48`（標記已看完）、`:51`（從待播移除）、`UI/Components/InSightSheetView.swift:185`（標記已看完）、`UI/Screens/NativePlayerView.swift:343`（播放結束通知）；而 `updateProgress` 由 `NativePlayerView.swift:488`（0.5 秒 periodic observer，每 3 秒觸發 2 次）與 `:499`（`cleanupPlayer` 收尾寫入）兩處呼叫。

根因：同一組旗標由「手動意圖」與「自動進度」兩條路徑寫入，且自動路徑採「無條件清零」而非「依狀態機轉移」。全檔無任何註釋說明此重置是刻意為之。

影響：端視產品語義——
- 若語義為「重新播放即恢復待播」（Apple TV Up Next 慣例），則此重置正確，僅缺註釋；
- 若語義為「手動移除一經確認即持久」，則屬缺陷：移除後同一播放器的收尾 `updateProgress`（`:499`，`cleanupPlayer` 內）即可能把它拉回，且用戶無從得知。

本線不預設產品語義，故列 P3；若產品定為後者，應升為 P2 並按下列方式修法。

建議修法（語義明確化）：
1. `updateProgress` 不再清零 `isManuallyCompleted`（已看完屬持久事實，除非用戶明確「重新觀看」）。
2. `isManuallyDismissed` 僅在「重新播放且新位置 > 30 秒」時清除，而非任意一次進度寫入。
3. 在 `PlaybackRecord` 上以註釋固定上述語義，並補測試。

驗證方式（需 Lead）：新增 `PlaybackProgressStoreTests.testManualDismissalSurvivesProgressTick()`：`removeFromUpNext` 後再 `updateProgress(positionMs: 6000)`，斷言 `shouldAppearInUpNext == false`（依所選語義）。現有 `PlaybackProgressStoreTests.swift:57` `testManualMarkCompletedAndRemoveFromUpNext` 只驗證標記後立即的狀態，未覆蓋「標記後再接進度寫入」。

附註：Lead 於本線交付前獨立指出「`records` 無上限、`persistToDisk` 在主線程全量編碼、`updateProgress` 每次重置標記」三點。前兩點與本報告 A-05 完全一致（本線獨立得出），第三點即本項 A-20；本線對其嚴重度的判斷為 **P3 而非 P2**，理由為產品語義未定（見上），此差異請以本節說明為準。

## 4. 對既有「已修復」聲明的核實結果

核實對象：`.ai/PROJECT_STATE.md` 第 1 頁「iOS 全項目 12 項代碼缺陷與隱患地毯式排查與全量修復閉環」中屬 A 線範圍的 4 條。

### 4.1「`VideoRepository` 升級為 `@MainActor public final class`、拔除脫軌異步 Task、純解析方法標 `nonisolated`，全工程數據層狀態機隔離標準統一」

判定：**部分屬實**。

證據：
- 屬實部分：`@MainActor public final class VideoRepository` 已落地（`VideoRepository.swift:38-39`，`git diff` 顯示由 `public final class VideoRepository: ObservableObject, @unchecked Sendable` 改為 `@MainActor` + 去 `Sendable`）；`nonisolated` 已標在 5 個純解析方法（`:256`、`:414`、`:515`、`:648`、`:831`）。
- 不屬實部分 1（效果）：`nonisolated` 未產生「離開主線程」的效果——呼叫端 `fetch91DongmanFeed` 等仍是 MainActor 且以同步方式呼叫，解析仍在主線程執行（A-03）。
- 不屬實部分 2（「拔除脫軌異步 Task」）：`searchAllSources` 的 `withTaskGroup`（`:1199-1212`）不持有任務句柄、不可取消、且子任務全部回跳 main actor；`loadNextPage` 亦無任務句柄，換源時無法取消其在途請求。兩者仍屬「脫軌」。
- 不屬實部分 3（「全工程……統一」）：`LiveRepository` 仍為 `public final class ... @unchecked Sendable`（`LiveSourceCatalog.swift:55`），未納入 MainActor；`ComicRepository`／`ForumRepository`／`PlaybackProgressStore`／`WatchlistStore`／`PicacgAuthStore` 則已統一，故為 5/6 統一，非全工程。

### 4.2「`PlaybackProgressStore` 有 5 秒防抖窗口 + 10 秒硬節流，並有 `flushPendingPersist()` 兜底」

判定：**屬實（存在性），效果描述部分屬實**。

證據：
- 5 秒窗口存在——`PlaybackProgressStore.swift:165-172`：`try? await Task.sleep(nanoseconds: 5_000_000_000) // 5 秒防抖窗口`。
- 10 秒硬節流存在——`:161`：`if now.timeIntervalSince(lastDiskPersistTime) >= 10.0 {`。
- `flushPendingPersist()` 存在（`:151-157`）且**確有呼叫點**：`NativePlayerView.swift:505`（`cleanupPlayer` 內，退出播放）與 `:343`／`markCompleted`（完結）。
- 效果修訂：由於播放器上報本身已是「每 3 秒 2 次」（`NativePlayerView.swift:487` 的 `Int(self.currentTime) % 3 == 0`），10 秒分支在穩定播放中幾乎不會命中（5 秒防抖任務總是先到期），實際落盤間隔約 5–9 秒。故聲明的「降頻 >85%」屬**推算值、未經實測**，本線不採信為已驗證結論。
- 缺口：`flushPendingPersist()` 只在 `cleanupPlayer` 路徑兜底；App 由背景被系統直接終止時無 `scenePhase` 兜底，最多丟約 5 秒進度（P3，未單列為發現）。

### 4.3「各源硬編碼截斷已清零（`ComicSourceAdapter` 36 部、`ForumSourceCatalog` 20 帖、`UniversalVideoEngine` XVideos 24 部、`LiveSourceCatalog` HornyStudents 30 間）」

判定：**不屬實**。

逐項證據：

| 子項 | 判定 | 證據 |
|---|---|---|
| `ComicSourceAdapter` 36 部 | 屬實（結果） | 現檔無 `36` 相關截斷（`grep -nE "36"` 僅命中 `parseImagePages` 的 `if pages.count == 500`）；惟該檔為**新增未跟蹤檔**（`git status` 顯示 `?? `），無 diff 可證「清除」動作 |
| `ForumSourceCatalog` 20 帖 | **不屬實** | `ForumSourceCatalog.swift:181` 現存 `}.prefix(20).map { $0 }`；且 `git diff` 顯示該行為**本輪新增**（`+        }.prefix(20).map { $0 }`） |
| `UniversalVideoEngine` XVideos 24 部 | 屬實 | `git diff` 顯示 `-            if items.count >= 24 { break }` 已刪除；現檔僅餘 `per_page=24`（API 分頁參數，非內容截斷，`:142`） |
| `LiveSourceCatalog` HornyStudents 30 間 | 屬實 | `git diff` 顯示 `-            if items.count >= 30 { break }` 已刪除；現檔僅餘 Stripchat 的 `limit=30`（API 分頁參數，`:299`） |

補充：聲明未列舉的 `Services/HTMLVideoSourceAdapter.swift:261` 仍有 `if items.count == 24 { break }`（A-09），以及 A-18 列出的 6 處安全上限。故「各源截斷清零」在四源清單上 2 真 1 假 1 無從核實，在整體命題上不成立。

### 4.4「正則表達式全部靜態預編譯」

判定：**不屬實**。

證據（`grep -c "try? NSRegularExpression"` 實測）：

| 檔案 | 執行期編譯次數 | `static let` 預編譯 |
|---|---|---|
| `Data/Video/VideoRepository.swift` | 15 | 0 |
| `Data/Comic/ComicRepository.swift` | 10 | 0 |
| `Data/Comic/ComicSourceAdapter.swift` | 14 | 0 |
| `Data/Forum/ForumSourceCatalog.swift` | 9 | 0 |
| `Data/Live/LiveSourceCatalog.swift` | 2 | 0 |
| `Services/HTMLVideoSourceAdapter.swift` | 11 | 3（`:7-9`） |

僅 `HTMLVideoSourceAdapter` 有 3 個 `private static let` 預編譯，其餘 5 檔為 0；且 `ComicSourceAdapter.attribute()`（`:357-363`）與 `LiveRepository.extractTag()`（`LiveSourceCatalog.swift:273-281`）屬**每次呼叫即編譯**。`UniversalVideoEngine.swift:80-83` 的 4 個預編譯正則屬 B 線範圍，本線僅記錄不評。結論：靜態預編譯只覆蓋了極小部分，聲明不成立。

### 4.5 附帶核實：「`URL(string:)!` 全項目只餘 5 處」

判定：**口徑不符**。`ios/AuroraShelf` 內 `URL(string:)!` 實測 0 處；全部強制解包實測 3 處（A-19）。需與聲明方核對統計範圍。

## 5. 已核查但未發現問題的點

以下各點已逐行核查，未發現缺陷，列出以免下輪重複排查：

1. **`loadFeed` 的取消與 `isLoading` 復位**：`VideoRepository.swift:107-142` 的 `guard self.currentSource.id == source.id && self.currentCategory == category` 早退分支不會留下永久 `isLoading = true`——因為早退的前提是「已有更新的載入在跑」，而新載入會自行收尾。`ComicRepository.swift:86-119` 同理。未發現「永久轉圈」路徑。
2. **`loadNextPage` 的重入保護**：`guard canLoadMore, !isLoading, !isLoadingNextPage` 與 `isLoadingNextPage = true` 之間無 `await`，在 MainActor 上屬原子，未發現 TOCTOU 或重複追加。
3. **`Set(ids)` 去重是否吞掉合法重複**：Video 側 id 由來源各自構造（`dm-<animeId>`、`hg-<dramaId>`、`91p-<viewkey>`、`hua-<vodId>`、`<source>-<url>`、`beeg-<fileId>`），同一作品在同一分類頁重複出現時去重屬正確行為；`parseHTMLFeed:226-247` 對同一 `playbackUrl` 的第二次錨點採「補全標題/封面」而非丟棄，處理正確。Forum 改用 `compositeID`（`fid:id`）杜絕跨板塊撞鍵（`ForumSourceCatalog.swift:20`），正確。未發現吞掉合法項目。
4. **`ForumRepository.loadDetail` 的 SSRF 防護**：`ForumSourceCatalog.swift:355-358` 以 `url.scheme == "https"` + host 白名單（`t924.sol140.com`、`t66y.com`）雙重限制，正確。
5. **`HTMLVideoSourceAdapter.playerFrameURLs` 的主機限制**：`:383-386` 僅接受與來源同 host（或子域）及 Javnoni 的 3 個已知播放器域，避免任意 iframe 抓取，正確。
6. **`resolveHTMLSourceStream` 的遞歸深度**：`:976` 的 `if depth < 2` 有上限，且 `playerPages` 數量受同 host 限制約束，未發現無限遞歸。
7. **`jmRequest` 的 4 域名回退**：`ComicRepository.swift:732-751` 為有界 `for` 迴圈，`lastError` 最終拋出，未發現無限重試。
8. **`fetchIkmmhPages`／`fetchPicacgPages`／`fetchEHentaiPages` 的迴圈**：三者均有 `pages.count`／`page <= pageCount && page <= N` 的雙重上限，且 `docs.isEmpty` 即 `break`，未發現死迴圈。
9. **分頁 URL 構造**：`ComicSourceAdapter.browseURL`／`searchURL` 與 `HTMLVideoSourceAdapter.feedURL` 均對 `page` 做 `max(page,1)`，offset 計算 `(page-1)*limit` 正確，未發現跳頁或重複頁。
10. **`PlaybackProgressStore` 的並發寫盤**：所有寫入路徑（`updateProgress`、`markCompleted`、`removeFromUpNext`、`clearAll`、`flushPendingPersist`）皆在 `@MainActor` 上串行，且以整塊 `Data` 覆寫單一 key，不存在「兩個寫入交錯造成 JSON 半截」的路徑。`UserDefaults` 內部亦為原子替換。任務所問「並發寫同一檔案是否會損壞 JSON」：**在此設計下不會**。
11. **`WatchlistStore` 寫入原子性**：同第 10 點（單 key 整塊覆寫、MainActor 串行）。未發現撕裂寫入。（`persistenceFailed` 校驗失效見 A-17，屬校驗無效而非原子性問題。）
12. **`ComicProtocolSecrets` 的密鑰是否洩漏到日誌**：全檔無 `print`／`NSLog`／`os_log`；`PlaybackSourceResult` 組裝的 headers 亦不落日誌。未發現洩漏。
13. **`ComicProtocolSecrets` 找不到配置時是否靜默降級**：否，會拋 `authenticationRequired` 並顯示明確文案（A-13 內文已列證據）。此點與任務假設相反，特此記錄。
14. **`PicacgAuthStore` 的 token 競態**：`@MainActor` 類 + 每次由 Keychain 讀取（無內存快取），不存在「兩次刷新互相覆蓋」的競態；同時亦無刷新機制（過期問題見 A-16）。
15. **`StreamCache` actor 用法**：`await cache.get/set` 序列化正確，無資料競態；問題在策略（A-07）而非同步。
16. **`JavSbSignature.createSignature`**：`while number.first == 0 { number.removeFirst() }` 為 O(n²)，但輸入只含 `fileCode@timestamp`（<40 位元組），無實際風險，故不列為發現。
17. **`VideoRepository.fetchVideosForSource` 的來源路由**：`switch source.id` + `htmlAdapterSourceIDs` 白名單 + 最終 `unsupportedSource` 拋錯，`VideoSourceCatalog` 的 `dedicatedAdapterSourceIDs ∪ htmlAdapterSourceIDs` 實測覆蓋全部 19 個來源（`VideoSourceCatalog.swift:244-255`），未發現「來源已列出但無路由」。
18. **`ForumBoardSelection.fids` 與 `parseT66Y` 的 tid 提取**：`read|viewthread` 與 `/htm_data/*.html` 兩路徑均有 `seen` 去重與 host 校驗，未發現注入或重複。
19. **`VideoItem`／`ComicItem` 的 `Hashable` + `Codable` 語義**：`VideoItem` 新增 `Codable`（本輪）後未改動任何欄位名或型別（`git diff` 僅新增 `coverBaseURL` 計算屬性與預設值調整），故不存在「本輪即令既有持久化資料失效」的問題（未來風險見 A-06）。
20. **`autoreleasepool` 使用**：本線範圍 13 個檔均無 `autoreleasepool`；全工程僅 `Services/ComicPageImageLoader.swift:112` 使用一處（解碼 `UIImage`）。就本線範圍而言無「該用而未用」的位置——解析產物皆為 Swift 值型別，不涉及大量 autoreleased 物件。未發現缺陷。
21. **跨層依賴（B/C 線交界，僅記錄不評級）**：`Models/TabItem.swift`（15:39:25 改動）已把 UI 文案改為 `"tab.home".localized` 等，實際行為取決於 `Services/LocalizationManager.swift`（15:39:19 新建）的 `@MainActor` 單例；`TabItem.swift` 本身不直接引用 `LocalizationManager`，而是走 `String.localized` 擴展（實作在 `LocalizationManager.swift`）。資料層（本線範圍）**不依賴** `LocalizationManager`——`grep -rn "LocalizationManager" ios/AuroraShelf/Data --include=*.swift` 實測 0 命中（僅 `UI/Screens/MainContainerView.swift`、`UI/Screens/PersonalScreen.swift` 引用），故未引入「資料層單例初始化時觸發 `@MainActor` 依賴」這類啟動期風險。惟 `Data/` 內各源的 `subtitle` 與錯誤文案仍為硬編碼中文字串（例如 `VideoRepository.swift:293` 的 `"91动漫 · \(category)"`、`ComicFetchError` 的 6 條文案），若本地化要覆蓋資料層需另開規格，不屬缺陷。此點交 B/C 線處理。

## 6. 未能確認、需運行驗證的假設

以下各項本線**未**取得運行證據，不得視為事實；建議由 Lead 以建置／測試或抓包確認。

1. **A-07 Beeg 直鏈是否帶時效簽名**：需抓 `https://store.externulls.com/video/play_url/<fileId>` 的回應，檢查是否含 `expires`／`sign`／`token` 參數。若含，則「同片第二次播放黑屏」為 P1，須提升嚴重度。
2. **A-03 主線程阻塞的實際量級**：需 Instruments Time Profiler 實測換源與搜尋期間 main thread 佔用；本報告只證明「解析在主線程執行」這一結構事實，未量化耗時。
3. **A-08 `ad_duration_ms` 的真實取值範圍**：需抓一次 `/Player/next` 回應；若上游恆為 15–20 秒且無異常值，則實際風險僅為「無進度提示」，嚴重度可降為 P3。
4. **A-14 華視封面錯位是否已實際發生**：需比對 `https://huavod.com/vodshow/1/page/1.html` 的卡片數與 `gen-movie-img` 數；若兩者相等且順序一致，則屬「結構脆弱」而非「當前錯位」。
5. **A-15 JM 單本作品的比例**：需抽樣 `album?id=` 的 `series` 空值比例；若單本佔比極低，嚴重度可降為 P3。
6. **A-16 Keychain 寫入失敗的實際機率**：需在真機（尤其低儲存空間、Data Protection 鎖定狀態）驗證；純代碼層只能確認「失敗被忽略」。
7. **A-19 `layer as! AVPlayerLayer`**：需在真機（尤其 AirPlay／畫中畫切換時）確認宿主層型別是否可能變更；本線未能證明可觸發。
8. **A-02 上游 HTML 是否真的含內嵌標籤標題**：需抓一次 `https://t924.sol140.com/forumdisplay.php?fid=4` 與 `https://t66y.com/thread0806.php?fid=7` 的實際 HTML。若上游始終為純文字標題，則此缺陷只在「上游改版時」才有害（潛伏性 P1）。
9. **`(.*?)</a>` 在大頁面上的回溯成本**：`HTMLVideoSourceAdapter:7`、`ForumSourceCatalog:142` 等以 `<a ...>(.*?)</a>` 掃描全頁，理論上在「大量未閉合 `<a>`」的畸形 HTML 上可能退化為 O(n²)。本線未量測，不列為發現，僅記錄為殘餘風險。
10. **單元測試對分頁缺陷的可測性**：`ComicRepositoryTests.testComicRepositoryPaginationStateResetOnSourceAndCategorySwitch`（`:492-509`）直接操作 `ComicRepository.shared` 單例並呼叫 `switchCategory`（會觸發真實網絡載入），故現有測試**不能**用作 A-01 的回歸防護，需先補 `session`／fetcher 注入點。同理，`VideoRepository` 完全無測試（`ios/AuroraShelfTests/` 無對應檔案），A-01／A-03 在 Video 側目前零測試覆蓋。
11. **本報告的可重現性限制**：工作樹在審計期間被其他會話持續改動（`LocalizationManager.swift` 15:39:19 新建、`Models/TabItem.swift` 15:39:25 改動、`PersonalScreen.swift` 審計期間仍在改）。本線 13 個受審檔案在 15:22–15:25 後未再變動（指紋 `3ca731a7d6342807`，逐檔 sha256 見第 1 節），但**整體工作樹**已非 15:36 那次成功建置的狀態；Lead 回報 15:41 指紋下 `xcodebuild` 失敗。故本報告的靜態結論可重現，而「本輪 96 tests 全綠」不能被用作「本報告的 P1 已被測試覆蓋」的證據——A-01、A-02、A-09、A-10 均無對應測試。

## 附：建議 Lead 優先處理順序

1. **A-01、A-02**（P1）：兩者都會令用戶在「資料正常」的情況下看到空白或殘缺列表，且無錯誤可回報，屬最直接的體驗損害。
2. **A-10**（P2，一行改動）：本輪新增的 `.prefix(20)`，刪除成本最低、同時修復一處與 PROJECT_STATE 矛盾的事實。
3. **A-09**（P2，一行改動）：移除 `items.count == 24`，影響 12 個 HTML 源的目錄完整性。
4. **A-03、A-04**（P2）：需設計改動，但屬本輪 `@MainActor` 升級的副作用，越早處理越好。
5. **A-05、A-06**（P2）：持久化治理，宜在正式發佈前完成版本化，否則下一次 schema 變更即靜默清空用戶歷史。
6. **A-13、A-16**（P2）：需產品／發佈流程決策，非純代碼改動。
7. 其餘 P2／P3 按線路排期。
