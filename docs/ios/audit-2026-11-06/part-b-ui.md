# B 線審計：iOS UI 層與播放器生命週期

## 0. 審計基準與漂移聲明（必讀）

| 項目 | 值 |
|------|-----|
| 審計基準時刻 | **2026年11月6日 15:45:51（HKT）** |
| Git HEAD | `78e7d13`（`78e7d13572fc265f17602690e1732703f6a20ce2`） |
| 未提交變更 | 74 個路徑（`git status --porcelain`） |
| UI 樹指紋 | **`b940ec1c761ea7ec04c7404b2e4f4e13d59fe9fb`**（`find ios/AuroraShelf/UI -name '*.swift' | sort | xargs shasum | shasum`，33 檔 6,005 行） |
| 首輪讀取完成時刻 | 15:44:08（當時指紋 `f8dfd56e2d37d2433367f50f482be5bd402ec5ae`） |
| Lead 回報指紋 | `046271a7…`（Lead 於另一時刻取樣，與本表指紋不同屬預期） |

**漂移處理**：本輪審計期間工作樹正被另一會話實時改動（`LocalizationManager.swift` 15:39 新建、`TabItem.swift` 15:39 改為 `.localized`、`PersonalScreen.swift` 15:42 由 368 行增至 441 行、`HomeScreen/ComicsScreen/ForumScreen/LiveScreen/ChannelsShelf/HeroBanner/UpNextShelf` 於 15:42–15:44 被改、`project.pbxproj` 15:45 重新生成）。凡涉及上述檔案的發現，行號已於 **15:44–15:46 逐一重新核對**，並在表中以 `†` 標示。其餘檔案自 15:20–15:38 讀取後未再變動。

**方法**：逐檔全文閱讀 33 個 UI 檔（6,005 行）；以 `grep` 窮舉生命週期／動畫／固定字級／無障礙／SF Symbol 用法；以 `python3` 按 WCAG 2.x 相對亮度公式實算設計令牌對比度；以 `python3` 解析 12 份 `Localizable.strings`（各 92 鍵）並與 41 個實際呼叫點比對。

**本輪未執行**：`xcodebuild`、模擬器、實機（由 Lead 負責）。凡行為推論一律標「需運行驗證」。

---

## 1. 範圍與方法

### 1.1 覆蓋清單（33 檔全讀，行數為 15:45 基準）

| # | 檔案 | 行數 | 讀取 |
|---|------|------|------|
| 1 | `UI/Screens/NativePlayerView.swift` | 728 | 全讀 |
| 2 | `UI/Screens/HomeScreen.swift` † | 499 | 全讀（15:45 覆核） |
| 3 | `UI/Screens/PersonalScreen.swift` † | 441 | 全讀（15:45 覆核，+73 行） |
| 4 | `UI/Screens/ComicsScreen.swift` † | 405 | 全讀（15:45 覆核） |
| 5 | `UI/Live/LivePlayerView.swift` | 310 | 全讀 |
| 6 | `UI/Screens/LiveScreen.swift` † | 301 | 全讀（15:45 覆核） |
| 7 | `UI/Screens/VideoDetailSheet.swift` | 285 | 全讀 |
| 8 | `UI/Comic/ComicReaderView.swift` | 242 | 全讀 |
| 9 | `UI/Components/InSightSheetView.swift` | 229 | 全讀 |
| 10 | `UI/Forum/ForumPostDetailView.swift` | 218 | 全讀 |
| 11 | `UI/Components/HeroBanner.swift` † | 209 | 全讀（15:45 覆核） |
| 12 | `UI/Screens/ForumScreen.swift` † | 199 | 全讀（15:45 覆核） |
| 13 | `UI/Live/LiveChatSheet.swift` | 185 | 全讀 |
| 14 | `UI/Theme/AuroraDesignTokens.swift` | 166 | 全讀 |
| 15 | `UI/Screens/MainContainerView.swift` † | 161 | 全讀（15:45 覆核，+2 行） |
| 16 | `UI/Search/SearchSheetView.swift` | 151 | 全讀 |
| 17 | `UI/Components/UpNextCard.swift` | 148 | 全讀 |
| 18 | `UI/Components/ChannelsShelf.swift` † | 125 | 全讀（15:45 覆核） |
| 19 | `UI/Components/PosterCard.swift` | 119 | 全讀 |
| 20 | `UI/Theme/DeviceLayoutProfile.swift` | 116 | 全讀 |
| 21 | `UI/Game/GameContainerView.swift` | 116 | 全讀 |
| 22 | `UI/Components/AuroraCoverImage.swift` | 94 | 全讀 |
| 23 | `UI/Live/StripchatLiveWebView.swift` | 89 | 全讀 |
| 24 | `UI/Components/SourceFilterBar.swift` | 74 | 全讀 |
| 25 | `UI/Components/PlayerOverlayBar.swift` | 69 | 全讀 |
| 26 | `UI/Screens/WatchlistScreen.swift` | 65 | 全讀 |
| 27 | `UI/Components/UpNextShelf.swift` † | 64 | 全讀（15:45 覆核） |
| 28 | `UI/Components/NetgreyLogoView.swift` | 53 | 全讀 |
| 29 | `UI/Components/Top10Card.swift` | 52 | 全讀 |
| 30 | `UI/Player/AuroraPlayerSurface.swift` | 41 | 全讀 |
| 31 | `UI/Components/AppleSpringCardButtonStyle.swift` | 21 | 全讀 |
| 32 | `UI/Components/AirPlayRoutePicker.swift` | 18 | 全讀 |
| 33 | `UI/Components/SourceWebsiteSheet.swift` | 12 | 全讀 |

> 任務書寫「27 個檔案」，實測 **33 個**。另因上游改動，`PersonalScreen.swift` 由 368 行增至 441 行（新增語言選擇），總行數由 5,930 增至 **6,005**；本報告行號以此為準。

### 1.2 交叉引用（僅作證據補強，不納入審計範圍）

`Models/VideoItem.swift`、`Models/TabItem.swift`、`App/PlayerCoordinator.swift`、`App/AuroraShelfApp.swift`、`Resources/Info.plist`、`Services/LocalizationManager.swift`、`Data/Video/VideoRepository.swift`、`Data/Video/PlaybackProgressStore.swift`、`Data/Forum/ForumSourceCatalog.swift`、`Data/Live/LiveSourceCatalog.swift`、`Data/Comic/ComicRepository.swift`、`Resources/*.lproj/Localizable.strings`。

### 1.3 數量基線

| 指標 | 任務書假設 | 實測（15:45） |
|------|-----------|--------------|
| `Task {` 出現次數 | 22 | **14**（另有 1 次為 `if cancelResolveTask {` 誤配） |
| `.task(id:)` | — | 4 |
| `withAnimation` 未接 `reduceMotion` | — | 2 |
| `UIScreen.main.bounds` | — | 5 |
| `AVAudioSession` 設定點 | — | 1（僅 `NativePlayerView`） |
| `isIdleTimerDisabled` | — | **0** |
| `WKUserContentController.add(self)` | 疑似 | **0**（假設的 retain cycle 不存在） |
| `dismantleUIView` | — | **0** |
| 固定字級 `.font(.system(size:` | — | 18（11 處為內容文字） |
| 已本地化呼叫點 `.localized` / `L10n(` | — | 41 個不同鍵，分佈 14 檔 |
| 仍為硬編碼中文的 `Text("…")` / `Button("…")` | — | 46 / 28 處 |
| `Localizable.strings` 語系 | 8 個 lproj（Lead 觀察） | **12 個 lproj × 92 鍵，0 缺鍵**（見 B-27） |

---

## 2. 發現總覽表

`†` = 該檔於 15:42–15:44 被上游改動，表內行號已於 15:45 重新核對。

| ID | 嚴重度 | 檔案:行 | 症狀 |
|----|--------|---------|------|
| B-01 | **P1** | `NativePlayerView.swift:48-50,324-327`；`LivePlayerView.swift:28-30,192-195` | 手動 90° 旋轉與系統原生橫屏並存，旋轉後畫面雙重旋轉／側躺 |
| B-02 | **P1** | `NativePlayerView.swift:57,94,459-462`；`LivePlayerView.swift:41,78,289-292` | 解碼失敗時錯誤與「重新連接」層被 `if let player` 分支永久遮蔽，只見黑屏 |
| B-03 | **P1** | `NativePlayerView.swift` 全檔；`LivePlayerView.swift` 全檔 | 全工程 0 處 `isIdleTimerDisabled`，自動鎖屏會中斷播放 |
| B-04 | **P1** | `GameContainerView.swift:58-91` | 唯一返回膠囊可被無限拖出屏幕且無夾取，用戶被困在遊戲內 |
| B-05 | P2 | `NativePlayerView.swift:426-427`；`LivePlayerView.swift:198-219` | 音訊工作階段只設不還原；直播完全未設定 |
| B-06 | P2 | `NativePlayerView.swift:476-494`；`AuroraPlayerSurface.swift:22-29` | 0.5 秒時間觀察器驅動整個播放器視圖樹重建；進度實際每 3 秒寫 2 次 |
| B-07 | P2 | `NativePlayerView.swift:275-284,575-584` | Slider 每次值變更即發 seek；拖動期間控制層仍會自動收起 |
| B-08 | P2 | `NativePlayerView.swift:64-74`；`LivePlayerView.swift:48-59` | `count:2` 與 `count:1` 手勢併用，雙擊切換畫幅難以命中 |
| B-09 | P2 | `SearchSheetView.swift:45-48`；`VideoDetailSheet.swift:112-114` | 關閉 sheet 與請求 fullScreenCover 同輪提交，播放器有機會被丟棄 |
| B-10 | P2 | `AuroraDesignTokens.swift:65-70`；`SourceFilterBar.swift:50,57` †；`ForumScreen.swift:162` † | `textOnBrand` 依配色而非背景選色：深色 3.15:1、淺色對綠底 4.10:1、對紅底 4.38:1 |
| B-11 | P2 | `HomeScreen.swift:362` †、`ChannelsShelf.swift:24,36,39` †、`UpNextShelf.swift:31` †、`UpNextCard.swift:87`、`InSightSheetView.swift:180` | `AuroraColor.brand` 直接當文字色，淺色底 2.92:1（`brandLabel` 6.59:1 未用） |
| B-12 | P2 | `AuroraDesignTokens.swift:59-64` | `textTertiary` 淺色 4.38:1、深色 4.18:1、深色卡片 3.76:1，卻用於 `.caption`／`.caption2` |
| B-13 | P2 | `UpNextCard.swift:48,80,86,90,94`；`VideoDetailSheet.swift:275,278`；`InSightSheetView.swift:219,222`；`ChannelsShelf.swift:72,99` † | 11 處內容文字用固定 pt，完全無視動態字級，最低 10pt |
| B-14 | P2 | `HeroBanner.swift:118` †；`VideoDetailSheet.swift:165,192-195`；`InSightSheetView.swift:93-96` | 硬編碼宣稱 4K HDR／DCI-P3／Dolby 5.1／空間立體聲／DLNA，與實際串流無關 |
| B-15 | P2 | `HomeScreen.swift:229` †；`VideoDetailSheet.swift:18`；`HeroBanner.swift:15,20` †；`UpNextCard.swift:132` | 以 `UIScreen.main.bounds` 判定佈局，iPad 分割視圖／Stage Manager 下形態誤判 |
| B-16 | P2 | `Top10Card.swift:9,22,31,43` | `@ScaledMetric` 88pt 配固定 56/76pt 寬框，大數字溢出並壓住海報 |
| B-17 | P2 | `NativePlayerView.swift:234,302,320`；`LivePlayerView.swift:171,186` | 46/12/42pt 為硬編碼常數而非 `safeAreaInsets`，旋轉後對位失效 |
| B-18 | P2 | `LiveChatSheet.swift:132-160` | 3 秒重複 Timer 從未 `invalidate`，每次關閉聊天室遺留一個永不停止的 Timer |
| B-19 | P2 | `GameContainerView.swift:16,24-27` | 本地 www 載入失敗無錯誤／載入路徑；另以 KVC 私有鍵開檔存取 |
| B-27 | P2 | `Services/LocalizationManager.swift:116-133`；14 個 UI 檔；`PersonalScreen.swift:406` † | 本地化只鋪到一半：41 鍵已用、46+28 處仍硬編碼中文；14 個讀 `.localized` 的檔案不訂閱語言管理器 |
| B-28 | P2 | `PersonalScreen.swift:170` † | 片單數量被計兩次，顯示「3 3 部」／「3 3 items」 |
| B-29 | P2 | `ChannelsShelf.swift:118` †；`ComicsScreen.swift:161` † | 無效 SF Symbol 造成空白圖示（已由運行時日誌確認）；另有 6 個動態 symbol 未經驗證 |
| B-30 | P2 | `Services/LocalizationManager.swift:88` | 未支援的系統語言（pt／ru／ar…）「跟隨系統」一律回退簡體中文 |
| B-20 | P3 | `VideoDetailSheet.swift:253`；`PersonalScreen.swift:329` †；`InSightSheetView.swift:207`；`NativePlayerView.swift:275` | 三處純圖示關閉鍵與播放進度 Slider 缺 `accessibilityLabel` |
| B-21 | P3 | `VideoDetailSheet.swift:172`；`ChannelsShelf.swift:52` † | 兩處 `withAnimation` 未判斷 `reduceMotion` |
| B-22 | P3 | `HomeScreen.swift:168` †；`ComicsScreen.swift:266` †；`ForumScreen.swift:96,106` †；`ComicReaderView.swift:24`；`ForumPostDetailView.swift:36`；`NativePlayerView.swift:532` | 8 個 `Task {}` 無取消路徑（目前由 repo guard 防重入，未造成重複請求） |
| B-23 | P3 | `StripchatLiveWebView.swift:56-64`；`LiveChatSheet.swift:110-121`；`GameContainerView.swift:18-29` | 三處 WKWebView 均無 `dismantleUIView`，關閉後不會主動 `stopLoading()` |
| B-24 | P3 | `ForumPostDetailView.swift:49-53` | `ForEach(detailImageURLs, id: \.self)`，重複圖片 URL 即重複鍵 |
| B-25 | P3 | `MainContainerView.swift:117-159` † | `--auto-play` 輪詢綁在 `onAppear`，收起重播器後會重啟並自動重播 |
| B-26 | P3 | `PosterCard.swift:101-105`；`InSightSheetView.swift:170` | 片單寫入失敗被 `catch {}`／`try?` 靜默吞掉 |

**分佈：P0 = 0、P1 = 4、P2 = 19、P3 = 7，合計 30 項。**

---

## 3. 逐項發現

### B-01（P1）手動 90° 旋轉與系統原生橫屏互相衝突

**症狀**　播放器提供「橫屏沉浸」按鈕，以 `.rotationEffect(.degrees(90))` 把整個畫面旋轉 90°。但 App 在 `Info.plist` 已宣告支援 `LandscapeLeft`／`LandscapeRight`，全工程又沒有任何執行期方向鎖（`grep supportedInterfaceOrientations|requestGeometryUpdate|UIDevice.orientation` → 0 命中）。用戶真的把機身轉橫時，系統先把視窗轉成橫屏；此時再按旋轉鍵，就在橫屏幾何上再轉 90°。

**代碼證據**
```swift
// NativePlayerView.swift:36
@State private var isLandscape: Bool = false
// NativePlayerView.swift:48-50
let isRotated = isLandscape
let targetWidth  = isRotated ? geo.size.height : geo.size.width
let targetHeight = isRotated ? geo.size.width  : geo.size.height
// NativePlayerView.swift:324-327
.frame(width: targetWidth, height: targetHeight)
.rotationEffect(isRotated ? .degrees(90) : .zero)
.position(x: geo.size.width / 2, y: geo.size.height / 2)
```
```xml
<!-- Resources/Info.plist -->
<key>UISupportedInterfaceOrientations</key>
<array>
  <string>UIInterfaceOrientationPortrait</string>
  <string>UIInterfaceOrientationLandscapeLeft</string>
  <string>UIInterfaceOrientationLandscapeRight</string>
</array>
```
`LivePlayerView.swift:28-30,192-195` 為相同複製。

**根因**　`isLandscape` 是純 UI 布爾值，只由按鈕驅動，既不讀 size class，也不觀察 scene 幾何；而系統方向未鎖定。兩套橫屏機制同時生效。

**影響**　P1：iPhone 上「橫屏沉浸」在最常見的兩條序列（先轉機身再按鍵／先按鍵再轉機身）都會得到側躺或比例錯亂畫面；黑邊問題反被放大。亦直接否證 `.ai/PROJECT_STATE.md` 第 47 行「橫屏下 16:9 視頻完美充滿整機」的表述。

**建議修法**　二選一：① 刪除手動旋轉，改用系統方向並以 `verticalSizeClass` 驅動佈局；② 保留手動旋轉，但播放器出現時以 `UIWindowScene.requestGeometryUpdate` 鎖 `.portrait`，按鈕改為請求 `.landscapeRight`，讓方向只有一個驅動源。

**驗證方式**　三條路徑截圖比對：豎屏→按鍵、轉橫→進播放器→按鍵、播放中轉橫。

---

### B-02（P1）解碼失敗時錯誤與重試層被播放器分支永久遮蔽

**症狀**　`AVPlayerItem.status == .failed` 時只寫入 `resolveError`／`liveError`，但 body 的分支順序是「有 player 就畫播放表面」；失敗的 item 仍掛在 player 上，因此錯誤層與「重新連接」按鈕永遠不可達，用戶只見全黑畫面加一條頂欄。

**代碼證據**
```swift
// NativePlayerView.swift:57 / 75 / 94 —— 分支順序
if let player = player {
    AuroraPlayerSurface(player: player, videoGravity: ...)
} else if isResolving {
    ...
} else if let error = resolveError {
    // 錯誤層 +「重新连接」按鈕（第 94-138 行）
}
// NativePlayerView.swift:459-462
} else if item.status == .failed {
    self.isResolving = false
    self.resolveError = item.error?.localizedDescription ?? "视频解码失败，请重试"
}
```
```swift
// LivePlayerView.swift:41 / 60 / 78 —— 同一結構
} else if let player = player { ... }
} else if isLoadingLive { ... }
} else if let error = liveError { ... }
// LivePlayerView.swift:289-292
} else if item.status == .failed {
    self.isLoadingLive = false
    self.liveError = item.error?.localizedDescription ?? "直播流解码失败"
}
```

**根因**　failed 分支未把 `player` 置 nil，錯誤狀態與渲染狀態的互斥假設不成立。

**影響**　P1：所有「解析成功但解碼／串流中斷」場景（HLS 403、m3u8 過期、換源失敗、直播掉線）都沒有重試入口，只能退出重進。

**建議修法**　failed 分支同時 `player?.pause()`、移除時間觀察器、`player = nil`；或把 body 首分支改為 `if let error = resolveError { 錯誤層 } else if let player = player { … }`。

**驗證方式**　指向 404 的 m3u8 或斷網，觀察是否出現「播放失败／重新连接」。

---

### B-03（P1）全工程未停用自動鎖屏，播放會被系統鎖屏打斷

**症狀**　`grep isIdleTimerDisabled` 在整個 `ios/AuroraShelf` 為 0 命中。播放器使用自訂 `AVPlayerLayer`，而非 `AVPlayerViewController`，系統不會自動延長螢幕點亮時間。

**代碼證據**
```swift
// AuroraPlayerSurface.swift:14-20 —— 自訂 AVPlayerLayer
public func makeUIView(context: Context) -> PlayerUIView {
    let view = PlayerUIView()
    view.playerLayer.player = player
    view.playerLayer.videoGravity = videoGravity
    view.backgroundColor = .black
    return view
}
```

**根因**　自訂播放表面缺少 HIG「影片播放期間保持螢幕點亮」的處理。

**影響**　P1：自動鎖定 30 秒（出廠常見值）時，任何超過 30 秒的觀看都會被鎖屏中斷；長片、直播均受影響，用戶會理解為「播放器自己停了」。

**建議修法**　進入播放時 `UIApplication.shared.isIdleTimerDisabled = true`，`cleanupPlayer`／`onDisappear` 還原 `false`（務必成對，否則全域耗電）。

**驗證方式**　系統自動鎖定設 30 秒，播放中不做任何操作，觀察是否鎖屏。

---

### B-04（P1）遊戲容器唯一返回膠囊可被拖出屏幕，無夾取亦無重置

**症狀**　`GameContainerView` 以 `fullScreenCover` 呈現，全屏 WKWebView 之上只有一個「返回视频」膠囊；該膠囊綁定 `DragGesture`，位移量無限累加且不受邊界約束。`fullScreenCover` 預設不可互動下滑關閉，故膠囊是唯一出口。

**代碼證據**
```swift
// GameContainerView.swift:77-90
.offset(x: capsuleOffset.width + dragPosition.width + 16,
        y: capsuleOffset.height + dragPosition.height + 54)
.gesture(
    DragGesture()
        .onChanged { value in wakeCapsule(); dragPosition = value.translation }
        .onEnded { value in
            capsuleOffset.width  += value.translation.width
            capsuleOffset.height += value.translation.height
            dragPosition = .zero
            scheduleAutoFade()
        }
)
```
入口：`PersonalScreen.swift:225-229`（15:45 覆核）；膠囊 3.5 秒後降為 40% 不透明度（`GameContainerView.swift:101-108`）。

**根因**　缺少位移夾取與越界自恢復。

**影響**　P1：一次誤拖即可把唯一出口推出可視範圍，遊戲內無其他返回路徑，只能強制退出 App。

**建議修法**　`onEnded` 以 `geo.size` 夾取 `capsuleOffset`（保留 16pt 邊距）；或固定位置不可拖；至少加一個備用退出（長按／雙指下滑）與邊緣熱區。

**驗證方式**　進入遊戲後把膠囊拖向左上角外放開，嘗試返回。

---

### B-05（P2）音訊工作階段只設不還原；直播完全未設定

**症狀**　`AVAudioSession` 全工程僅 2 行，皆在 `NativePlayerView`；`cleanupPlayer()` 無 `setActive(false, .notifyOthersOnDeactivation)`；`LivePlayerView` 完全沒有音訊工作階段設定；`Info.plist` 無 `UIBackgroundModes: audio`。

**代碼證據**
```swift
// NativePlayerView.swift:426-427
try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback, options: [])
try? AVAudioSession.sharedInstance().setActive(true)
// NativePlayerView.swift:497-525（cleanupPlayer）—— 無任何 setActive(false)
```
```swift
// LivePlayerView.swift:198-207 —— 只做 fetch 與 cleanup
.onAppear { ... fetchAndStartLiveStream() }
.onDisappear { cleanupLivePlayer() }
```

**根因**　會話生命週期無配對管理；直播假設會話已由影片播放器設好。

**影響**　P2：① 用戶聽着其他 App 音樂進出本 App 播放器後，音樂不會自動恢復（會話仍 active 且 `.playback`）；② 冷啟動直接進直播（`--auto-live` 或直接開直播分頁）時會話仍是 `.ambient`——靜音鍵開啟時直播無聲，亦無法 duck 其他音訊。

**建議修法**　建 `AudioSessionCoordinator`：任一播放器出現時 `setCategory(.playback, mode: .moviePlayback)` + `setActive(true)`，cleanup 時 `setActive(false, .notifyOthersOnDeactivation)`；直播一併接入。

**驗證方式**　① Music 播放中進出本 App 播放器，觀察是否恢復；② 開靜音鍵冷啟動直入直播聽是否有聲。

---

### B-06（P2）0.5 秒時間觀察器驅動整個播放器視圖樹重建，進度寫入頻次為宣稱的 2 倍

**症狀**　`addPeriodicTimeObserver(forInterval: 0.5, queue: .main)` 每 tick 寫兩個 `@State`，令 `NativePlayerView.body` 每秒重建 2 次；進度寫入條件 `Int(currentTime) % 3 == 0` 在 0.5 秒節奏下於 3.0/3.5、6.0/6.5… 各命中兩次。`AuroraPlayerSurface.updateUIView` 每次重建都重新賦值 `playerLayer.player`。

**代碼證據**
```swift
// NativePlayerView.swift:476-494
let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
timeObserverToken = avPlayer.addPeriodicTimeObserver(forInterval: interval, queue: .main) { time in
    guard !isSeeking else { return }
    self.currentTime = time.seconds
    ...
    // Apple TV Up Next 状态机：每 3 秒同步一次播放进度
    if Int(self.currentTime) % 3 == 0 && self.duration > 5.0 {
        PlaybackProgressStore.shared.updateProgress(...)
    }
}
// AuroraPlayerSurface.swift:22-29
public func updateUIView(_ uiView: PlayerUIView, context: Context) {
    uiView.playerLayer.player = player
    ...
}
// PlaybackProgressStore.swift:92-115 —— 每次 updateProgress 整份複製 records 並 @Published
var list = records
...
self.records = list
```

**根因**　播放進度以 view 狀態而非獨立 observable 持有；寫入用「秒數取模」而非時間戳節流。

**影響**　P2（效能）：`PlaybackProgressStore` 被 `UpNextShelf`（首頁＋資料庫各一份）、`VideoDetailSheet`、`InSightSheetView` 訂閱；每 3 秒兩次整份陣列重建與發佈，令貨架在不必要時重繪，並在滾動首頁時與圖片解碼爭主線程。

**建議修法**　① 進度改由獨立 observable／`TimelineView` 承載子視圖；② 條件改為「3 秒時間戳節流」；③ `updateUIView` 加 `if uiView.playerLayer.player !== player`。

**驗證方式**　Time Profiler + SwiftUI View Body：播放 60 秒，統計 `NativePlayerView.body` 與 `UpNextCard.body` 呼叫次數。

---

### B-07（P2）Slider 每次值變更即發 seek；拖動期間控制層仍會自動收起

**症狀**　進度 Slider 的 `Binding.set` 每次變化直接 `seekTo`，沒有 `onEditingChanged` 區分拖動與放開；HLS 上一次拖動會產生數十次 seek。另外自動隱藏計時器只在 tap 時重排，拖動超過 3 秒時控制層會在手指下方消失。

**代碼證據**
```swift
// NativePlayerView.swift:275-284
Slider(
    value: Binding(
        get: { currentTime },
        set: { newValue in
            currentTime = newValue
            seekTo(time: newValue)      // 每個 delta 都 seek
        }
    ),
    in: 0...max(duration, 1.0)
)
// NativePlayerView.swift:575-584
private func seekTo(time: Double) {
    ...
    isSeeking = true
    player.seek(to: cmTime) { _ in DispatchQueue.main.async { self.isSeeking = false } }
}
// NativePlayerView.swift:376-391（scheduleAutoHideControls 只在 tap / play 內重排）
```

**影響**　P2：HLS 長片拖動明顯卡頓、頻繁轉圈；拖動中控制層消失導致操作中斷。

**建議修法**　以 `onEditingChanged` 只在放開時 seek，拖動中僅更新本地 `currentTime`；`onEditingChanged(true)` 時取消自動隱藏，放開後重排。

**驗證方式**　HLS 長片上緩慢拖動 3 秒，觀察緩衝指示與控制層是否消失。

---

### B-08（P2）雙擊手勢被單擊手勢吞掉

**症狀**　同一表面先掛 `.onTapGesture(count: 2)` 再掛 `.onTapGesture(count: 1)`；SwiftUI 建立兩個獨立 recognizer，`count: 1` 在第一次點擊即成功，`count: 2` 幾乎不可能辨識。

**代碼證據**
```swift
// NativePlayerView.swift:64-74
.onTapGesture(count: 2) { toggleFillMode() }
.onTapGesture(count: 1) {
    withAnimation(reduceMotion ? nil : AuroraMotion.quick) { showControls.toggle() }
    if showControls { scheduleAutoHideControls() }
}
// LivePlayerView.swift:48-59 同構
```

**影響**　P2：核心畫幅切換（PROJECT_STATE 列為已交付能力）實際不可達，只能改用頂欄按鈕。

**建議修法**
```swift
.gesture(ExclusiveGesture(
    TapGesture(count: 2).onEnded { toggleFillMode() },
    TapGesture(count: 1).onEnded { toggleControls() }
))
```

**驗證方式**　快速雙擊畫面中央，觀察是否出現「畫幅：滿鋪全屏（無黑邊）」HUD。

---

### B-09（P2）sheet 關閉與 fullScreenCover 請求同輪提交

**症狀**　搜尋結果與詳情頁播放都在同一輪更新內先後改兩個 presentation 狀態：`SearchSheetView` 先 `onPlayVideo`（設 `coordinator.activeVideo` → 觸發 `fullScreenCover`）再 `onDismiss`；`VideoDetailSheet` 先 `onDismiss` 再 `onPlay`。兩者都在 sheet 未收完時要求呈現 fullScreenCover。

**代碼證據**
```swift
// SearchSheetView.swift:45-48
Button(action: {
    onPlayVideo(item)
    onDismiss()
}) { ... }
// VideoDetailSheet.swift:112-114
Button {
    onDismiss()
    onPlay(video)
} label: { ... }
// MainContainerView.swift:97-111（† 行號已覆核）
.fullScreenCover(item: $coordinator.activeVideo) { video in
    NativePlayerView(video: video) { coordinator.dismiss() }
}
.sheet(isPresented: $showSearchSheet) { SearchSheetView(...) }
```

**影響**　P2：iOS 對「已有 presentedViewController 時再 present」會拒絕或延後，表現為第一次點搜尋結果無反應、第二次才播，或延遲 0.3–0.5 秒。

**建議修法**　先 `dismiss()`，在 `sheet(onDismiss:)` 回調中才 `onPlayVideo`；或把 `fullScreenCover` 下沉到各 sheet 內容層級。

**驗證方式**　搜尋結果連點 10 次，統計首次即播放的比例。

---

### B-10（P2）`textOnBrand` 依配色模式而非背景色選前景色

**症狀**　規則為「淺色模式或高對比 → 黑；否則白」。但 `#FF4F7B` 配黑 6.66:1、配白僅 3.15:1，深色模式卻選白；`forestGreen`／accentRed 配黑分別只有 4.10／4.38，淺色模式卻強制黑。

**代碼證據**
```swift
// AuroraDesignTokens.swift:65-70
public static let textOnBrand = Color(uiColor: UIColor { traits in
    if traits.accessibilityContrast == .high || traits.userInterfaceStyle == .light {
        return UIColor.black
    }
    return UIColor.white
})
// SourceFilterBar.swift:50,57（†）
.foregroundStyle(isSelected ? AuroraColor.textOnBrand : AuroraColor.textSecondary)
.fill(isSelected ? AuroraColor.brand : unselectedChipFill)
// ForumScreen.swift:162-168（†）
.foregroundStyle(AuroraColor.textOnBrand)
.background(RoundedRectangle(cornerRadius: 4, style: .continuous)
    .fill(isT66y ? AuroraColor.forestGreen : AuroraColor.accentRed))
```

**實算（WCAG 2.x）**

| 前景 / 背景 | 對比 | AA 4.5 |
|---|---|---|
| 白 / #FF4F7B（深色模式現況） | **3.15** | ✗ |
| 黑 / #FF4F7B | 6.66 | ✓ |
| 黑 / #2E7D32（淺色模式現況） | **4.10** | ✗ |
| 白 / #2E7D32 | 5.13 | ✓ |
| 黑 / #E50914（淺色模式現況） | **4.38** | ✗ |
| 白 / #E50914 | 4.79 | ✓ |

**影響**　P2（無障礙）：分類膠囊選中態（15pt）與論壇板塊徽章（11pt）在對應模式下不達 AA，強光下可讀性明顯下降。

**建議修法**　改為「依背景決定前景」的 `textOn(_ background:)`，或依亮度 `<0.5 → 白` 動態選色。

**驗證方式**　淺色／深色／增加對比三態截圖，量測實際像素對比。

---

### B-11（P2）`AuroraColor.brand` 當文字色，淺色底 2.92:1

**症狀**　品牌色 `#FF4F7B` 被直接用作文字色；淺色 canvas（`#F5F6F8`）下實算 **2.92:1**。專案已有為文字而設的 `brandLabel`（淺色 `#B20A14`，6.59:1）卻未在這些位置使用。

**代碼證據**
```
HomeScreen.swift:362（†）     .foregroundStyle(AuroraColor.brand)   // 「已配置 N 個源」按鈕
ChannelsShelf.swift:24,36,39（†） .foregroundStyle(AuroraColor.brand) // 「CHANNELS」標籤、「全量 19 源」＋chevron
UpNextShelf.swift:31（†）      .foregroundStyle(AuroraColor.brand)   // 「UP NEXT」標籤
UpNextCard.swift:87           .foregroundStyle(AuroraColor.brand)   // 「45%」進度文字
InSightSheetView.swift:180    .foregroundStyle(isBookmarked ? AuroraColor.brand : ...) // 片單按鈕文字
```

**影響**　P2：小字號品牌色文字（`.footnote`／`.caption`／`.caption2`）淺色模式僅 2.92:1，品牌色在所有面板失去可讀性。

**建議修法**　約束 `brand` 只作填充／描邊，文字一律 `brandLabel`；可在 token 層加 `typealias brandText = brandLabel` 固化語意。

**驗證方式**　淺色模式截圖 HomeScreen 推薦欄右上按鈕與 UpNextCard 進度文字，取色量測。

---

### B-12（P2）`textTertiary` 對比不足，卻承擔 `.caption`／`.caption2`

**症狀**　非高對比模式下固定 45% 灰階：淺色 canvas 4.38:1、深色 canvas 4.18:1、深色 `surfaceCard` 3.76:1，全部低於 AA 4.5，卻用於大量次要說明文字。

**代碼證據**
```swift
// AuroraDesignTokens.swift:59-64
public static let textTertiary = Color(uiColor: UIColor { traits in
    let isDark = traits.userInterfaceStyle == .dark
    let isHighContrast = traits.accessibilityContrast == .high
    let whiteLevel: CGFloat = isHighContrast ? (isDark ? 0.78 : 0.25) : 0.45
    return UIColor(white: whiteLevel, alpha: 1)
})
```
使用點示例：`HomeScreen.swift:191`（`.caption`）、`ComicsScreen.swift:255`（`.caption2`）、`VideoDetailSheet.swift:276`（10pt）、`UpNextCard.swift:91`、`PersonalScreen.swift:302`（chevron）。

**影響**　P2：所有次要說明在一般模式不達 AA，深色卡片上最嚴重（3.76:1）。

**建議修法**　非高對比模式提升至 0.55–0.58，或統一改用 `textSecondary`（系統 `secondaryLabel`）。

**驗證方式**　深色模式截圖 UpNextCard 分隔點與 VideoDetailSheet 規格標題，取色量測。

---

### B-13（P2）11 處內容文字使用固定 pt，無視動態字級

**症狀**　`.font(.system(size:))` 共 18 處，其中 11 處屬內容文字，完全不受 Dynamic Type 影響，最低 10pt。

**代碼證據**
```
UpNextCard.swift:48       .font(.system(size: 10, weight: .bold))   // 來源角標
UpNextCard.swift:80       .font(.system(size: 14, weight: .semibold)) // 影片標題
UpNextCard.swift:86,90,94 .font(.system(size: 11 ...))               // 百分比／剩餘時間
VideoDetailSheet.swift:275/278  size: 10 / size: 11                  // 規格標題／值
InSightSheetView.swift:219/222  size: 10 / size: 12                  // 規格標題／值
ChannelsShelf.swift:72,99（†）  size: 11                              // 頻道名稱
```
（其餘 7 處為 `NetgreyLogoView` logotype、`PersonalScreen.swift:36` 頭像字母、播放器 `size: 40` 圖示，屬可接受的裝飾性固定尺寸。）

**影響**　P2（無障礙）：開啟「放大文字」的用戶在 Up Next 貨架、詳情頁規格、頻道貨架得不到放大，與 App 其餘部分（已普遍使用語意字級）不一致。

**建議修法**　改用語意字級，或以 `.font(.system(size: 11, relativeTo: .caption))` 保留視覺並跟隨縮放。

**驗證方式**　輔助使用 → 放大文字 → AX3／AX5，檢查上述三個元件。

---

### B-14（P2）三處「媒體規格」為硬編碼失實資訊

**症狀**　播放器、詳情頁與 InSight 面板都展示「4K HDR」「DCI-P3」「Dolby 5.1」「空間立體聲」「AirPlay / DLNA」，與實際串流無關且所有影片相同；詳情頁在缺簡介時自動生成「支持 4K 超清硬解出画与多端投屏」的宣稱。

**代碼證據**
```swift
// HeroBanner.swift:118（†）
Text("超清 4K HDR")
// VideoDetailSheet.swift:165
let description = video.subtitle.isEmpty ? "本片由 \(video.sourceName) 官方源直连分发，支持 4K 超清硬解出画与多端投屏。" : video.subtitle
// VideoDetailSheet.swift:192-195
specItem(title: "画质标准", value: "4K HDR")
specItem(title: "视频编码", value: video.playbackUrl.contains(".m3u8") ? "HLS 变码率" : "H.264 MP4")
specItem(title: "音频标准", value: "Dolby 5.1")
specItem(title: "投屏支持", value: "AirPlay / DLNA")
// InSightSheetView.swift:93-96
specBadge(title: "清晰度", value: "4K HDR")
specBadge(title: "协议流", value: video.playbackUrl.contains(".m3u8") ? "HLS" : "MP4")
specBadge(title: "色彩模式", value: "DCI-P3")
specBadge(title: "声道", value: "空间立体声")
```
只有「视频编码／協議流」依 `.m3u8` 推斷；`DLNA` 在本 App 完全沒有對應能力。

**影響**　P2：對用戶陳述不實技術規格（DLNA、Dolby 5.1 均不存在），屬誤導性宣稱與 App Review 風險；同時令規格區塊失去診斷價值。

**建議修法**　以 `AVAssetTrack`／`formatDescriptions`／可選音軌數取真實值，取不到時顯示「未提供」；刪除 DLNA 一項。

**驗證方式**　對 480p 來源打開詳情頁與 InSight，確認仍顯示 4K HDR／Dolby 5.1。

---

### B-15（P2）以 `UIScreen.main.bounds` 判定佈局形態

**症狀**　5 處以 `UIScreen.main.bounds` 推導 `DeviceFormFactor`、hero 高度與卡片寬度。`UIScreen.main` 自 iOS 16 起被標記棄用，回傳的是「裝置螢幕」而非「當前視窗」；iPad 分割視圖／Slide Over／Stage Manager 下與實際寬度不符。`HeroBanner` 把 `horizontalSizeClass` 傳進 `DeviceLayout.resolve`，但該形參在函式體內從未使用。

**代碼證據**
```
HomeScreen.swift:229（†）      let screen = UIScreen.main.bounds.size
VideoDetailSheet.swift:18     let screen = UIScreen.main.bounds.size
HeroBanner.swift:15,20（†）    let screen = UIScreen.main.bounds.size / .height
UpNextCard.swift:132          let screen = UIScreen.main.bounds.size
```
```swift
// DeviceLayoutProfile.swift:22-31 —— horizontalSizeClass 形參未使用
public static func resolve(
    size: CGSize,
    horizontalSizeClass: UserInterfaceSizeClass? = nil,
    idiom: UIUserInterfaceIdiom = UIDevice.current.userInterfaceIdiom
) -> DeviceFormFactor {
    if idiom == .pad || size.width >= 740 { return .pad }
```

**影響**　P2：iPad 分割視圖（例如 1/3 寬 ≈ 320pt）仍被判為 `.pad` → hero 520pt、Up Next 卡片 320pt、詳情頁背景 320pt，在窄欄內嚴重溢出；iPhone Duo 的 445–520pt 判定同樣誤判。

**建議修法**　改用 `GeometryReader` 的 `size` 或真正使用 `horizontalSizeClass`，刪除 `UIScreen.main`。

**驗證方式**　iPad 分割視圖把 App 置於 1/3 寬，檢查 hero 與詳情頁背景是否溢出。

---

### B-16（P2）Top10Card 大數字在無障礙字級下溢出

**症狀**　排名數字以 `@ScaledMetric(relativeTo: .largeTitle) = 88` 縮放（AX5 可達 200pt 以上），但外層固定 `.frame(width: rank >= 10 ? 76 : 56)`，且 `HStack(spacing: -14)` 與 120×180 海報重疊擺放。

**代碼證據**
```swift
// Top10Card.swift:9
@ScaledMetric(relativeTo: .largeTitle) private var rankSize: CGFloat = 88
// Top10Card.swift:21-33
Text("\(rank)")
    .font(.system(size: rankSize, weight: .black, design: .rounded))
    .frame(width: rank >= 10 ? 76 : 56, alignment: .trailing)
    .offset(y: 6)
// Top10Card.swift:43
.frame(width: 120, height: 180)
```

**影響**　P2：AX2 以上字級時數字與海報疊印，Top 10 貨架視覺崩壞。

**建議修法**　寬度與負間距同樣以 `@ScaledMetric` 縮放，或在 `dynamicTypeSize.isAccessibilitySize` 時改垂直排列。

**驗證方式**　AX5 截圖 Top 10 貨架。

---

### B-17（P2）頂欄／底欄以 46 / 12 / 42pt 魔數代替安全區

**症狀**　播放器以 `isLandscape ? 12 : 46` 作頂欄上內距、`isLandscape ? 16 : 42` 作底欄下內距，皆為固定常數而非 `safeAreaInsets`；旋轉 90° 後畫面「上緣」對應機身側邊，動態島不在該處；iPad 上 46pt 又過大。`PlayerOverlayBar` 另外再疊 12pt，實際總內距為 24pt。

**代碼證據**
```swift
// NativePlayerView.swift:234 / 302 / 320
.padding(.top, isLandscape ? 12 : 46)
.padding(.bottom, isLandscape ? 16 : 42)
// LivePlayerView.swift:171 / 186
.padding(.top, isLandscape ? 12 : 46)
// PlayerOverlayBar.swift:56-58
.padding(.horizontal, AuroraMetrics.contentInset)
.padding(.top, AuroraMetrics.spacingM)
```

**影響**　P2：旋轉模式下頂欄與挖孔區的關係失準（可能壓住、也可能多留 34pt）；iPad／Duo 留白明顯錯誤。此為 PROJECT_STATE「頂欄動態避讓安全區」聲明的實質缺口。

**建議修法**　改用 `geo.safeAreaInsets` 並在旋轉時做軸向映射；或放棄手動旋轉（B-01），由系統方向 + `.safeAreaPadding()` 自動處理。

**驗證方式**　與 B-01 同一組截圖，量測頂欄與動態島／Home 指示條的實際間距。

---

### B-18（P2）LiveChatSheet 的 3 秒 Timer 永不停止

**症狀**　`LiveRoomWebView.Coordinator` 在 `didFinish` 建立 `Timer.scheduledTimer(repeats: true)`，全程沒有任何 `invalidate()`：無 `deinit`、無 `dismantleUIView`、視圖內亦無通知。關閉聊天室後 Timer 仍由主線程 RunLoop 持有並每 3 秒觸發（因 `[weak self, weak webView]` 立即返回）。

**代碼證據**
```swift
// LiveChatSheet.swift:132-135
func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
    onStatus("已连接 · 正在监听消息")
    pollTimer?.invalidate()
    pollTimer = Timer.scheduledTimer(withTimeInterval: 3, repeats: true) { [weak self, weak webView] _ in
        guard let self, let webView else { return }
        ...
    }
}
// 全檔無 deinit / dismantleUIView / 其他 invalidate
```

**影響**　P2：每次開關聊天室遺留一個每 3 秒醒來的主線程 Timer，長時間使用會累積數十個，屬持續耗電與喚醒。

**建議修法**　`Coordinator.deinit` 內 `pollTimer?.invalidate()`，並在 `dismantleUIView` 明確停止；或改用 `Task` + `sleep` 迴圈隨 `.task` 自動取消。

**驗證方式**　反覆開關聊天室 10 次，Instruments 觀察 `__NSCFTimer` 實例數是否單調上升。

---

### B-19（P2）遊戲容器載入失敗無錯誤路徑

**症狀**　`GameWebViewRepresentable` 只建 config、找 `Bundle.main.url(forResource:"index", withExtension:"html", subdirectory:"www")`、載入。資源缺失／毀損／WebGL 失敗時純黑畫面，無 loading、無錯誤、無重試；亦未設 `navigationDelegate` 接 `didFailProvisionalNavigation`。另以 KVC 私有鍵開檔存取。

**代碼證據**
```swift
// GameContainerView.swift:16
config.preferences.setValue(true, forKey: "allowFileAccessFromFileURLs")
// GameContainerView.swift:18-29
let webView = WKWebView(frame: .zero, configuration: config)
...
if let url = Bundle.main.url(forResource: "index", withExtension: "html", subdirectory: "www") {
    webView.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
}
return webView
// GameContainerView.swift:32
func updateUIView(_ uiView: WKWebView, context: Context) {}
```
資源來源：`ios/AuroraShelf/Resources/www -> ../../../app/src/main/assets/www`（symlink；`index.html` 存在）。

**影響**　P2：打包流程若未帶上 www（symlink 目標在 CI 上未必存在），用戶只見全黑且無法區分「載入中」與「壞了」；私有 KVC 鍵有 App Review 與未來版本失效風險。

**建議修法**　加 `guard let url` 錯誤佔位；設 `navigationDelegate` 顯示錯誤與重試；觀察 `.progress`；移除私有 KVC。

**驗證方式**　把 www 指向缺檔路徑（暫存副本），確認是否出現錯誤提示。

---

### B-27（P2）本地化只鋪到一半，且多數畫面不訂閱語言管理器

**症狀**　本輪審計期間本地化設施剛落地（`LocalizationManager.swift` 15:39 新建、12 份 `.strings` 15:37–15:38 建立、`TabItem` 15:39 改為 `.localized`），但 UI 只遷移了一部分：41 個鍵已被使用，仍有 **46 處 `Text("中文…")`** 與 **28 處 `Button("中文…")`** 未本地化，導致同一畫面內中英／中日混雜。更關鍵的是：全 UI 只有 `MainContainerView` 與 `PersonalScreen` 訂閱 `LocalizationManager`，其餘 **14 個直接讀 `.localized` 的檔案**不訂閱，語言切換後它們是否更新取決於父層是否重建。

**代碼證據**
```swift
// Services/LocalizationManager.swift:116-127 —— 切換後只發佈給訂閱者
public func setLanguage(_ language: AppLanguage) {
    ...
    objectWillChange.send()
}
```
```swift
// MainContainerView.swift:5,96（†）
@ObservedObject private var locManager = LocalizationManager.shared
.environment(\.locale, locManager.activeLocale)
```
```swift
// PersonalScreen.swift:406（†）—— 語言選擇器本身仍有硬編碼中文
if lang == .system { Text("跟随当前系统语言设置") }
// 同一畫面其餘未本地化處：:248「选择 AirPlay 设备」、:257「完成」、:264「选择直播来源」、:270「取消」、:323「iOS 漫画源 · …个已配置适配」、:348「iOS 解析器尚未接入」、:359「暂不可用」
```
未訂閱語言管理器但讀 `.localized` 的 14 檔：`NativePlayerView`、`WatchlistScreen`、`ForumScreen`、`VideoDetailSheet`、`HomeScreen`、`ComicsScreen`、`LiveScreen`、`SearchSheetView`、`ComicReaderView`、`ForumPostDetailView`、`ChannelsShelf`、`UpNextShelf`、`HeroBanner`、`LivePlayerView`。

**根因**　本地化以「逐字串就地替換」方式推進，未先建立「由環境注入語言、所有畫面統一訂閱」的機制；`LanguagePickerSheet` 本身也未被本地化。

**影響**　P2：① 非中文用戶會在多數畫面看到中文殘留（例如播放器錯誤層、詳情頁、漫畫、論壇仍為純中文），語言選擇形同半成品；② 切換語言後非當前分頁／已開啟的 sheet 可能仍顯示舊語言（需運行驗證 V-13）。**另註**：Lead 觀察「8 個 lproj、選 es/ms/id/vi 顯示原始 key」在 15:36 建置產物上成立，但源碼現況已補齊——12 個 lproj 各 92 鍵、0 缺鍵、0 個 `value == key`、佔位符跨語系一致（詳見 §5 第 14 項）。是否真正打包進 App 需重新建置後覆核（兩份 `Debug-iphonesimulator/AuroraShelf.app` 均為 **0 個 lproj**，因其建置時刻早於 15:37）。

**建議修法**　① 建立 `LocalizationManager` 的 `EnvironmentObject` 注入，所有畫面以 `@EnvironmentObject` 訂閱（或統一在根層以 `.id(locManager.selectedLanguage)` 強制重建子樹）；② 完成剩餘 74 處硬編碼字串遷移；③ 語言選擇器自身先本地化。

**驗證方式**　① 以英語系統語言啟動，逐分頁檢查殘留中文；② 在 Home 分頁切成日文，再切回查看 Tab 標籤、貨架、sheet 是否同步；③ 重新建置後檢查 `.app` 內 lproj 數量。

---

### B-28（P2）片單數量被計兩次

**症狀**　`PersonalScreen` 設定列的片單數量用「插值數字 + 帶 `%d` 的本地化字串」拼接：`"\(count) " + "watchlist.count".localized(count)`，而 `watchlist.count` 的譯文本身已含 `%d`（en=`"%d items"`、zh-Hant=`"%d 部"`）。結果顯示「3 3 部」／「3 3 items」。

**代碼證據**
```swift
// PersonalScreen.swift:167-172（†）
settingRow(
    icon: "bookmark",
    title: "watchlist.title".localized,
    value: "\(watchlist.items.count) " + "watchlist.count".localized(watchlist.items.count),
    isNavigable: true
)
```
```text
en.lproj/Localizable.strings : "watchlist.count" = "%d items";
zh-Hant.lproj/Localizable.strings : "watchlist.count" = "%d 部";
```

**根因**　改寫本地化時把「舊的硬編碼前綴」與「新的格式字串」同時保留。

**影響**　P2：設定列顯示重複數字，所有 12 個語系皆受影響，屬明顯可見的低級錯誤。

**建議修法**　改為 `value: "watchlist.count".localized(watchlist.items.count)`。

**驗證方式**　打開資料庫分頁，片單非空時看「我的片單」右側數值。

---

### B-29（P2）無效 SF Symbol 造成空白圖示（2 處已確認，6 個動態映射待驗證）

**症狀**　SF Symbol 名稱皆以字串在執行期解析，編譯期不會報錯；名稱不存在時 `Image(systemName:)` 靜默渲染為空白。Lead 已用 iOS 26.4 模擬器系統日誌確認 2 個無效名稱，本節延伸列出同類風險面。

**已確認無效（Lead 運行時日誌，`No symbol named … found in system symbol set`）**
```
ChannelsShelf.swift:118（†）  case "beeg": return "bolt.horizontal.tv.fill"        // Beeg 頻道圖示空白
ComicsScreen.swift:161（†）   Label("需要哔咔账号", systemImage: "person.crop.circle.badge.key")  // 圖示空白
```
**未驗證的動態映射（同一函式，8 個 id → 8 個名稱，全部字串硬編碼）**
```swift
// ChannelsShelf.swift:112-123（†）
private func channelIcon(for id: String) -> String {
    switch id {
    case "huavod":    return "film.stack.fill"                  // 待驗證
    case "dongman91": return "sparkles.tv.fill"                 // 待驗證
    case "huangguo":  return "play.square.stack.fill"           // 待驗證
    case "91porn":    return "video.badge.plus"                 // 待驗證
    case "beeg":      return "bolt.horizontal.tv.fill"          // ✗ 已確認無效
    case "xvideos":   return "play.rectangle.on.rectangle.fill" // 待驗證
    case "eporner":   return "4k.tv.fill"                       // 待驗證
    case "missav":    return "tv.inset.filled"                  // 待驗證（命名格式可疑）
    default:          return "tv.fill"
    }
}
```
其餘 UI 內 30 餘個字面 symbol 名稱（`magnifyingglass`、`bookmark`、`goforward.15`、`airplayvideo` 等）均為常用名稱，本次未逐一於執行期核對。

**根因**　symbol 名稱沒有型別或測試約束；`channelIcon` 以字串映射，任何拼寫錯誤只會在執行期表現為空白。

**影響**　P2：受影響頻道／來源在貨架與清單上少一個圖示（文字標籤仍在，故不阻斷操作），但 88×56 的圖示區塊變全空，視覺上像「載入失敗」；同時系統日誌持續輸出警告。

**建議修法**　加一條單元測試（成本極低、可覆蓋全部 8 個）：
```swift
func testAllChannelIconsExist() {
    for source in VideoSourceCatalog.supportedSources {
        XCTAssertNotNil(UIImage(systemName: iconName(for: source.id)), source.id)
    }
}
```
並為 `channelIcon` 的每個分支改用已驗證名稱；`default` 分支已有 `tv.fill` 兜底，可延伸為「名稱無效時回退 `tv.fill`」。

**驗證方式**　`xcrun simctl spawn booted log stream --predicate 'eventMessage CONTAINS "No symbol named"'`，逐分頁巡覽後確認日誌為空。

---

### B-30（P2）未支援的系統語言一律回退簡體中文

**症狀**　`AppLanguage.system` 以 `resolveSystemLanguageCode()` 比對系統偏好語言，對 12 個支援語言以外的任何語言（葡、俄、阿拉伯、印地等）都落到 `return "zh-Hans"`；同時 `.environment(\.locale, …)` 也被設為 `zh-Hans`，連數字／日期格式一併變為中文習慣。

**代碼證據**
```swift
// LocalizationManager.swift:88
return "zh-Hans"
// LocalizationManager.swift:44 / 65 —— 同函式內 zh-Hans / zh-Hant 分支
case .zhHans: return "zh-Hans"
...
return "zh-Hans"
```
```swift
// MainContainerView.swift:96（†）
.environment(\.locale, locManager.activeLocale)
```

**影響**　P2：系統語言為不支援語言（如 `pt-BR`、`ru-RU`）的用戶，「跟隨系統」得到簡體中文介面與中文格式，而非慣例回退英文；對非中文用戶屬明顯錯誤。

**建議修法**　最終回退改為 `"en"`（或 `Bundle.preferredLocalizations.first`），並在 `AppLanguage.displayName` 標示；同時把 `.environment(\.locale)` 僅在用戶明確選定語言時覆寫。

**驗證方式**　模擬器把系統語言設為葡萄牙文，選「跟隨系統」，觀察介面語言。

---

### B-20（P3）圖示按鈕與 Slider 缺無障礙標籤

**症狀**　三處純圖示關閉鍵未設 `accessibilityLabel`；播放進度 Slider 無標籤與值描述；`PosterCard` 對同一動作提供兩個按鈕（封面與標題），VoiceOver 用戶會遇到重複的「播放」動作。

**代碼證據**
```
VideoDetailSheet.swift:253     Button(action: onDismiss) { Image(systemName: "xmark.circle.fill") … }
PersonalScreen.swift:329（†）  Button(action: { dismiss() }) { Image(systemName: "xmark.circle.fill") … }
InSightSheetView.swift:207     Button(action: onDismiss) { Image(systemName: "xmark.circle.fill") … }
NativePlayerView.swift:275-285 Slider(value: Binding(...), in: 0...max(duration, 1.0))   // 無 label
PosterCard.swift:22-58, 60-71  兩個 Button 皆呼叫 onSelect()
```

**影響**　P3（HIG）：VoiceOver 讀出無名按鈕；Slider 只能報「調整式」；海報卡片朗讀冗長重複。

**建議修法**　三處加 `.accessibilityLabel("關閉")`；Slider 加 `.accessibilityLabel("播放進度").accessibilityValue(formatTime(currentTime))`；`PosterCard` 標題按鈕 `.accessibilityHidden(true)` 或合併為單一元素。

**驗證方式**　開 VoiceOver 巡覽播放器控制層與詳情頁頂欄。

---

### B-21（P3）兩處動畫未尊重「減少動態效果」

**代碼證據**
```swift
// VideoDetailSheet.swift:171-179
withAnimation(.easeInOut(duration: 0.2)) { isDescriptionExpanded.toggle() }
// ChannelsShelf.swift:51-54（†）
withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) { repo.switchSource(to: source) }
```
（全 UI 層 `withAnimation` 未帶 `reduceMotion` 者僅此 2 處。）

**影響**　P3：違反「減少動態效果」偏好；`ChannelsShelf` 切源時整個首頁貨架彈性重排。

**建議修法**　改用 `withAnimation(AuroraMotion.selection(reduceMotion: reduceMotion))`，`ChannelsShelf` 補 `@Environment(\.accessibilityReduceMotion)`。

**驗證方式**　設定 → 輔助使用 → 動態效果 → 減少動態效果，操作上述兩處。

---

### B-22（P3）8 個 `Task {}` 無取消路徑

**症狀**　14 個 `Task {` 中 6 個有持有與取消（見 §5），其餘 8 個 fire-and-forget；離開頁面後請求仍在跑。

**代碼證據**
```
HomeScreen.swift:168（†）      Task { await repo.loadNextPage() }
ComicsScreen.swift:266（†）    Task { await repo.loadNextPage() }
ComicsScreen.swift:322（†）    Task { await loginPicacg() }
ForumScreen.swift:96,106（†）  Task { await repo.loadNextPage() }
ComicReaderView.swift:24      Button("重试") { Task { await loadPage() } }
ForumPostDetailView.swift:36  Button("重试") { Task { await loadDetail() } }
NativePlayerView.swift:532    Task { @MainActor in … loadMediaSelectionGroup … }
```

**影響**　P3：**未**造成重複請求（三個 repo 的 `loadNextPage()` 均有 `guard canLoadMore, !isLoading, !isLoadingNextPage` 且以 id 去重：`VideoRepository.swift:145`、`ComicRepository.swift:122`、Forum `:324`；字幕組載入亦有 item 身分比對）。本項僅描述「離開頁面後仍佔網絡／算力」與「無法立即中止」。

**建議修法**　改 `.task {}` 或存入 `@State` 任務並在 `onDisappear` 取消。

**驗證方式**　Instruments Network：離開 ComicsScreen 後觀察是否仍有分頁請求在飛。

---

### B-23（P3）三處 WKWebView 均無 `dismantleUIView`

**代碼證據**
```
StripchatLiveWebView.swift:56-64   makeUIView + load(...)
LiveChatSheet.swift:110-121        makeUIView + load(...)
GameContainerView.swift:18-29      makeUIView + loadFileURL(...)
（grep -rn dismantleUIView ios/AuroraShelf → 0）
```

**影響**　P3：WKWebView 在其網絡進程完成當前請求前可能短暫繼續傳輸（直播串尤甚），退出後數秒仍有流量；user script 需等 WKWebView 完全釋放才回收。無證據顯示永久洩漏。

**建議修法**　實作 `dismantleUIView`：`stopLoading()`、`navigationDelegate = nil`、`removeAllUserScripts()`。

**驗證方式**　進出 Stripchat 直播間 10 次，觀察 WebContent 進程記憶體是否回落。

---

### B-24（P3）論壇正文圖片以 URL 字串作 ForEach 鍵

**代碼證據**
```swift
// ForumPostDetailView.swift:49-53
ForEach(detailImageURLs, id: \.self) { imageURL in
    if let url = URL(string: imageURL) { ForumPostImage(url: url, referer: post.threadUrl) }
}
// ForumPostDetailView.swift:98-101
private var detailImageURLs: [String] {
    if !displayPost.contentImageUrls.isEmpty { return displayPost.contentImageUrls }
    return displayPost.previewImageUrl.map { [$0] } ?? []
}
```
（對照：`ForumScreen.swift:89` 列表層已改用 `compositeID`，正文層未跟上。）

**影響**　P3：正文重複引用同一圖片時可能少顯示或錯位，並持續輸出 "ForEach with non-unique ids" 警告。

**建議修法**　改 `ForEach(Array(detailImageURLs.enumerated()), id: \.offset)`。

**驗證方式**　找一篇重複引用同一圖片的帖子，比對圖片數量與 console 警告。

---

### B-25（P3）`--auto-play` 輪詢綁在 `onAppear`，收起播放器後會自動重播

**代碼證據**
```swift
// MainContainerView.swift:129-143（†）
if args.contains("--auto-play") {
    autoPlayTask?.cancel()
    autoPlayTask = Task {
        for _ in 0..<25 {
            try? await Task.sleep(nanoseconds: 200_000_000)
            guard !Task.isCancelled else { return }
            if let realVideo = videoRepo.billboardVideo ?? videoRepo.feedVideos.first {
                await MainActor.run { coordinator.play(realVideo) }
                break
            }
        }
    }
}
// MainContainerView.swift:156-159（†）
.onDisappear { autoPlayTask?.cancel(); autoPlayTask = nil }
```

**影響**　P3：僅影響 `--auto-play` 自動化驗收路徑——關閉播放器後被再次自動播放，回歸腳本會卡在播放器。一般用戶路徑不受影響。

**建議修法**　加 `@State private var didRunAutoPlay = false` 守衛，或改用 `.task {}`（只隨 identity 觸發一次）。

**驗證方式**　以 `--auto-play` 啟動，關閉播放器後觀察是否又自動進入播放。

---

### B-26（P3）片單寫入失敗被靜默吞掉

**代碼證據**
```swift
// PosterCard.swift:101-105
Button {
    do {
        let isBookmarked = WatchlistStore.shared.contains(video)
        try WatchlistStore.shared.setBookmarked(!isBookmarked, for: video)
    } catch {}
}
// InSightSheetView.swift:169-170
Button { try? watchlistStore.setBookmarked(!isBookmarked, for: video) }
```
（同一 App 的 `HeroBanner.swift:105-112`、`VideoDetailSheet.swift:262-269`、`WatchlistScreen.swift:56-63` 都有 `alert` 呈現失敗原因。）

**影響**　P3：持久化失敗（磁碟滿、權限、編碼）時用戶以為已加入，重開 App 後消失。

**建議修法**　比照 `HeroBanner` 以 `@State watchlistError` + `alert` 呈現。

**驗證方式**　把片單儲存路徑設為不可寫（暫存副本）後點「加入片單」。

---

## 4. 對既有「已修復」聲明的核實結果

來源：`.ai/PROJECT_STATE.md` 第 6 行、第 10 行、第 47-48 行。

| # | 聲明原文（摘要） | 判定 | 代碼證據與說明 |
|---|------------------|------|----------------|
| 1 | 「`NativePlayerView.swift` 內 `seek` 完成回調切回主線程修改 `@State`」 | **屬實** | `NativePlayerView.swift:579-583`：`player.seek(to:) { _ in DispatchQueue.main.async { self.isSeeking = false } }`；時間觀察器亦以 `queue: .main` 註冊（`:477`），KVO 回調亦經 `DispatchQueue.main.async`（`:451,470`）。**保留意見**：seek 路徑仍有 B-07 的「每個 delta 都 seek」功能／效能問題，與執行緒無關。 |
| 2 | 「`MainContainerView` 自動播放輪詢 Task 納入生命週期管控，離開頁面主動 `cancel()`」 | **屬實（附保留）** | `MainContainerView.swift:10` 持有 `autoPlayTask`、`:156-159`（†）`onDisappear` 內 `cancel()` 並置 nil。保留：① 該 Task 只在 `--auto-play` 參數下建立，不覆蓋一般路徑；② 因綁在 `onAppear`，收起 `fullScreenCover` 後會重啟並自動重播（B-25）。 |
| 3 | 「`SearchSheetView` 單例引用修正為 `@ObservedObject`」 | **屬實** | `SearchSheetView.swift:7`：`@ObservedObject private var repo = VideoRepository.shared`。競態另在資料層被 `currentSearchToken` 封住（`VideoRepository.swift:1187,1213`），舊結果不覆蓋新結果——此點亦可作「已修復」確認。 |
| 4 | 「`ForumScreen` 採用 `compositeID`（`fid:id`）杜絕跨板塊重複鍵」 | **屬實** | `ForumScreen.swift:89` `ForEach(repo.posts, id: \.compositeID)`；`ForumSourceCatalog.swift:20` `public var compositeID: String { "\(fid):\(id)" }`。**保留意見**：正文圖片層 `ForEach(…, id: \.self)` 未同步（B-24）。 |
| 5 | 「橫屏 90° 旋轉沉浸全屏…完美充滿整機，徹底消除上下大黑邊；頂欄動態避讓狀態欄安全區（豎屏 46pt，橫屏 12pt），底欄避讓 Home 指示條（42pt），控件絕不遮擋時間與動態島」 | **部分屬實（功能層面不成立）** | 旋轉與留白**確實存在**：`NativePlayerView.swift:325`、`:234`、`:302`；`LivePlayerView.swift:193,171,186` 同構。但：① 所謂「動態避讓」實為硬編碼常數，非 `safeAreaInsets`（B-17）；② App 已宣告原生橫屏且無執行期方向鎖，手動旋轉會與系統旋轉疊加（B-01）——「橫屏下完美充滿」在最常見操作序列下不成立；③ 非瀏海機型／iPad 上 46pt 為錯誤值；④ 頂欄總內距實際 12+12=24pt（`PlayerOverlayBar.swift:57`），與聲明所述避讓量不符。 |
| 附 | 「`PlaybackProgressStore` 將 0.5s 回調引發的高頻寫盤降頻 >85%」 | **部分屬實** | `PlaybackProgressStore.swift:159-173` 確有 10 秒硬節流 + 5 秒防抖。但播放器端仍每 3 秒觸發 **2 次** `updateProgress`（B-06），每次整份重建 `records` 並 `@Published`：磁碟已節流，記憶體與視圖重繪成本未節流。 |

---

## 5. 已核查而未發現問題的點

以下按任務書重點逐條查證，**未**發現缺陷，列出以標明覆蓋範圍，避免重複審計：

1. **WKWebView retain cycle**：全工程**沒有**任何 `WKUserContentController.add(self, name:)`（grep 0 命中），三個 WebView 只用 `addUserScript`；`navigationDelegate` 由 `WKWebView` 以 weak 持有。任務書假設的「add(self) 未 remove 造成循環引用」**不存在**（真正的問題是 B-18 的 Timer 與 B-23 的未 stopLoading）。
2. **AVPlayer 觀察者配對**：`cleanupPlayer`（`NativePlayerView.swift:497-525`）次序正確——寫回進度 → cancel resolveTask → cancel hideControlsTask → invalidate 兩個 KVO → `removeTimeObserver` → `pause()` → `player = nil`。未發現 time observer 或 KVO 遺漏移除。
3. **`replaceCurrentItem(with: nil)` 缺失是否致漏**：未發現實質洩漏。`player = nil` 後 body 的 `if let player` 失敗 → `AuroraPlayerSurface` 從層級移除 → `AVPlayerLayer` 隨 UIView 釋放，形成穩定拆解路徑。
4. **搜尋競態**：`VideoRepository.searchAllSources` 以 `currentSearchToken` 比對，舊任務結果在 `guard currentSearchToken == token else { return }` 被丟棄（`:1187,1213`）；被取消的舊任務不會誤將 `isSearching` 置 false（token 先不匹配而 return）。未發現舊果覆蓋新果。
5. **分頁重複請求**：三個 repo 的 `loadNextPage` 皆有「載入中／無更多」守衛與 id 去重（`VideoRepository.swift:145`、`ComicRepository.swift:122`、Forum `:324`）。未追蹤的 onAppear Task 因此不會造成重複頁請求。
6. **`ForEach` 鍵唯一性（跨來源）**：影片 id 帶源前綴（`"\(source.id)-…"`、`"xv-"`、`"ep-"`，見 `HTMLVideoSourceAdapter.swift:252`、`UniversalVideoEngine.swift:121,191`）；直播 id 為 `"hs-…"`／`"sc-…"`（`LiveSourceCatalog.swift:256,324`）；論壇用 `compositeID`。未發現跨來源鍵衝突。
7. **陣列越界與強拆**：全 UI 層未發現 `[0]`、`first!`、`last!`；唯一 `as!` 為 `AuroraPlayerSurface.swift:38` 的 `layer as! AVPlayerLayer`（`layerClass` 已覆寫，安全）。`ComicReaderView.currentChapter` 有完整邊界檢查（`:72-77`）。
8. **Button 內嵌 Button**：未發現嵌套（`PosterCard` 是兩個**並列** Button，`UpNextCard` 用 `.contextMenu` 承載次要動作）。任務書假設的命中衝突不存在。
9. **`.task(id:)` 使用**：4 處（`ComicReaderView:34`、`ForumPostDetailView:73,210`、`AuroraCoverImage:48`）均正確帶 id，換 id 會取消上一輪。
10. **三態設計令牌**：`canvas`／`surface`／`surfaceCard`／`surfaceHigh`／`brandLabel`／`hairline` 實作為**四態**（light／dark／lightHigh／darkHigh，依 `accessibilityContrast` 切換，`AuroraDesignTokens.swift:88-107`），比聲明更完整。不足者僅 `brand`／`accentRed`／`forestGreen`／`success`／`warning`／`destructive`（單態）——後果已記於 B-10／B-11。
11. **最小觸控目標**：`AuroraMetrics.minTouchTarget = 44`（`:121`）普遍套用；未發現 <44pt 的可點擊圖示。
12. **數值健壯性**：`formatTime`／`formatDuration` 對 `NaN`、負值均有守衛（`NativePlayerView.swift:618-632`）；`Slider` 上界用 `max(duration, 1.0)` 防 `0...0` 崩潰。
13. **圖片載入生命週期**：`AuroraCoverImage` 以 `.task(id: url)` 綁 URL，取消後以 `guard !Task.isCancelled` 丟棄結果（`:48-66`）；`ComicPageImageLoader` 另有 `loadID` 比對。
14. **本地化資料完整性（15:45 實測）**：12 個 `*.lproj` 各 92 鍵，**0 缺鍵、0 額外鍵、0 個 `value == key`、0 個重複鍵**；與 41 個實際呼叫點比對**無「已使用但未定義」的鍵**；跨語系 `%@`／`%d` 佔位符數量一致（無格式字串不匹配風險）。`xcodegen` 已於 15:45 重新生成 `project.pbxproj`，12 個 lproj 全部列入。**唯一保留**：兩份 `Debug-iphonesimulator/AuroraShelf.app` 產物均含 **0 個 lproj**（建置時刻早於 15:37 建立語系檔），打包結果須重新建置後覆核。
15. **`MainTab` 圖示名稱**：`TabItem.swift` 中 `house`／`books.vertical`／`tv.and.mediabox`／`bubble.left.and.bubble.right`／`rectangle.stack` 及其 `.fill` 變體均屬常用有效名稱（本次未運行核對，屬 §6 待驗項之一）。

---

## 6. 未能確認、需運行驗證的假設

| # | 假設 | 為何無法靜態確認 | 建議驗證手段 |
|---|------|------------------|--------------|
| V-1 | 旋轉按鈕與系統橫屏疊加後畫面側躺（B-01） | 取決於實機方向鎖與 scene 幾何更新時序 | 模擬器三條路徑截圖（見 B-01） |
| V-2 | 雙擊切換畫幅實際失效（B-08） | SwiftUI 手勢 recognizer 競爭的實測行為 | 快速雙擊畫面中央，看是否出現 HUD |
| V-3 | sheet → fullScreenCover 同輪切換是否吞掉播放器（B-09） | 依賴 presentedViewController 時序 | 搜尋結果連點 10 次統計失敗率 |
| V-4 | 46/12pt 在旋轉後與動態島的實際間距（B-17） | 需實機量測挖孔位置 | 旋轉模式截圖量距 |
| V-5 | Top10Card 在 AX2–AX5 的溢出程度（B-16） | 需實際字級渲染 | 無障礙字級截圖 |
| V-6 | 直播在靜音鍵開啟時是否無聲（B-05） | 取決於啟動時會話狀態 | 冷啟動直入直播 + 靜音鍵 |
| V-7 | 退出播放器後其他 App 音樂是否恢復（B-05） | 系統會話仲裁行為 | Music 播放中進出本 App 播放器 |
| V-8 | 遊戲 WebView 載入 203MB 資源的記憶體峰值與首屏時間（B-19） | 需運行量測 | Instruments + 首屏計時 |
| V-9 | LiveChatSheet Timer 是否確實累積（B-18） | 需分配計數 | Instruments `__NSCFTimer` 計數 |
| V-10 | 播放器頂欄在 AX5 下標題是否被裁（`PlayerOverlayBar.swift:38-50` + `NativePlayerView.swift:234`） | 需實際字級與寬度 | AX5 播放器截圖 |
| V-11 | `AuroraPlayerSurface` 每次重設 `playerLayer.player` 是否引起閃爍（B-06 附帶） | 需逐幀觀察 | 錄屏逐幀比對，或加 `!==` 判斷後對比 |
| V-12 | `--auto-play` 收起播放器後重播（B-25） | 需實際 onAppear 觸發時序 | 以啟動參數跑一次回歸 |
| V-13 | 語言切換對「非當前分頁／已開啟 sheet」是否即時生效（B-27） | 取決於 SwiftUI 重建範圍 | 在 Home 分頁切日文，切回後檢查 Tab 標籤、貨架、sheet |
| V-14 | 剩餘 6 個動態 SF Symbol 名稱是否有效（B-29） | 只能於執行期解析 | `simctl spawn booted log stream` + 逐個來源巡覽 |
| V-15 | 重新建置後 `.app` 是否含 12 個 lproj（B-27 附註） | 打包結果需實際建置 | 建置後 `ls AuroraShelf.app/*.lproj` |

---

## 7. 給 Lead 的優先處理建議

1. **先修 B-01／B-02／B-03**：皆在播放主路徑，修法獨立、風險低（刪手動旋轉或加方向鎖；failed 分支 nil 掉 player；`isIdleTimerDisabled` 成對設置），直接決定「播放器是否可用」。
2. **B-28 為零風險即修項**（一行）：目前設定列顯示「3 3 部」，屬最容易被測試與用戶一眼看到的錯誤。
3. **B-10／B-11／B-12 建議一次改 `AuroraColor` 語意層**（填充 vs 文字 vs 次要文字），避免逐處打補丁；改完可加單元測試以 `UIColor` 解析值斷言對比度 ≥ 4.5。
4. **B-27 建議在本地化遷移完成前不要對外宣稱「多語系支援」**：現況 41 鍵已用、74 處仍硬編碼中文、14 個畫面不訂閱語言管理器。另請在總報告中修正「缺 es/ms/id/vi」的表述——該問題在 15:37–15:38 已於源碼補齊（12 lproj × 92 鍵），但仍需重新建置確認打包。
5. **B-29 建議加一條 symbol 存在性測試**（一次覆蓋 8 個映射），與 Lead 已確認的 2 個無效名稱同一提交修完。
6. **B-04 與 B-19 同批處理**：遊戲板塊的退出與載入失敗屬同一使用者旅程，應同一輪修完並同一輪驗證。
7. 其餘 P3 排入下一輪，不宜與播放器修復混在同一提交，以免驗證歸因困難。
