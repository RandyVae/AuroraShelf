# Antigravity 交接：Slots v2.16.9 / Build 235 模擬器全量檢驗

日期：2026-11-10  
交接對象：Antigravity  
專案：`/Users/itou/Documents/ChatGPT/91pron`  
Git HEAD：`1731468`（`docs: 同步会话接力记忆与项目状态至 v2.16.9 (Build 235)`）  
最新程式版本：`v2.16.9 / Build 235`

## 1. 交付包與驗證基線

- 最新 Android 交付包：`dist/Netgrey.apk`
  - `versionCode=235`
  - `versionName=2.16.9`
  - 檔案大小：218,623,342 bytes
  - SHA-256：`22fce9b515474fd435dc3b69761c2944889dad369ddcd596d12f4661adde4df7`
- OTA 伺服器：`https://bananarey.us`
  - `Netgrey.apk` HTTP 200，Content-Length 218,623,342
  - `manifest.plist` 的 `bundle-version=2.16.9`
  - `Netgrey.ipa` HTTP 200，Content-Length 214,827,243
- iOS 模擬器 Release 產物由同一個 HEAD 建置：
  - iPhone 17 Pro：`build.nosync/ios-release-v235-slots/Build/Products/Release-iphonesimulator/AuroraShelf.app`
  - iPad Pro 13-inch：`build.nosync/ios-release-v235-slots-ipad/Build/Products/Release-iphonesimulator/AuroraShelf.app`
- Android APK 與工作樹 Slots 資產一致：
  - `slots.js` SHA-256：`6696400d75e81c4be2e08549f11181b8c7cea7e9f0305d5f044ead226c0e966a`
  - `app.css` SHA-256：`ccd1e2a7aae192e4ce3681128f70ba60e0dad275331291b5280f1e965c3330fc`

本輪只做檢驗與文件交接，沒有修改業務代碼。`captures/slots-recheck-2026-11-10/` 內的截圖、影片與 JSON 是本輪證據。

## 2. 已完成的驗證

### iPhone 17 Pro / iOS 26.4 Release

已驗證：

- Slots 冷啟動與乾淨安裝
- 5×3 滾輪、連線高亮、LAST WIN、免費旋轉狀態
- AUTO、TURBO、8 局連續旋轉
- 免費旋轉流程與存檔保存
- 18+ 伴遊切換與互動
- 赔率表彈窗
- 高倍率結果與 MEGA HIT 彈層
- v2.16.7 引入的半屏全景突入立繪
- 金幣耗盡流程

全景突入影片：[iphone17-v235-spin.mp4](../../../captures/slots-recheck-2026-11-10/iphone17-v235-spin.mp4)  
免費旋轉記錄：[iphone17-v235-spin-log.json](../../../captures/slots-recheck-2026-11-10/iphone17-v235-spin-log.json)  
金幣耗盡畫面：[iphone17-v235-zero-gold-no-bailout.png](../../../captures/slots-recheck-2026-11-10/iphone17-v235-zero-gold-no-bailout.png)

### iPad Pro 13-inch / iOS 26.4 Release

已驗證：

- Slots 主界面可啟動
- 機台、滾輪、雙翼操作台沒有裁切或重疊
- 赔率表可完整顯示
- 高倍率結果可顯示

證據：[ipad13-v235-release-slots.png](../../../captures/slots-recheck-2026-11-10/ipad13-v235-release-slots.png)

### 自動化基線

- iOS Debug 全量測試：**157 passed / 0 failed / 6 skipped**
- Android：`./gradlew testDebugUnitTest assembleRelease` **BUILD SUCCESSFUL**
- Slots 確定性測試：`node game/tools/test_slots_determinism.js` **PASS，1000 seeds**
- 遊戲邏輯回歸：`node scripts/verify-game-logic.js` **PASS**
- 最新 Release 模擬器建置成功；Xcode 有 41 個既有 warning，但沒有 build error
- 模擬器 runtime log 沒有 App crash；只有 iOS Simulator 的 WebKit accessibility duplicate-class 警告

第一次用 Release 產物直接跑 XCTest 時，測試 target 因 Release module 未帶 `-enable-testing` 而未能編譯；切換到同一 HEAD 的 Debug 測試產物後，157/0/6 全量測試通過。這是測試配置問題，不是產品測試失敗。

## 3. 必須修復的問題

### P0：正式啟動流程仍注入 QA 全資源

**現象**：乾淨安裝後直接進入 Slots，金幣為 `99,999,999`，所有英雄、裝備與 18+ 內容被解鎖。

**代碼證據**：

- [app.js:1031](../../../app/src/main/assets/www/js/app.js:1031) 的 `boot()` 無條件呼叫 `LGSave.applyQAFullTestResources(...)`。
- [save.js:339](../../../app/src/main/assets/www/js/save.js:339) 將金幣、鑰匙、體力、碎片設為上限，並解鎖所有英雄與裝備。
- [save.js:383](../../../app/src/main/assets/www/js/save.js:383) 也會強制 `state.settings.adult = true`。

**修復要求**：

- 生產啟動流程不得呼叫 QA boost。
- QA boost 只能由明確的 Debug/Test 開關觸發，且不能被 Release 包默認啟用。
- 不得在啟動時覆寫玩家存檔、成人內容選擇或 Slots 進度。
- 保留測試能力時，必須有可追蹤的測試入口與 Release 安全邊界。

### P1：免費旋轉 3 倍倍率在實際流程沒有生效

**現象**：最新 Release 自動旋轉 8 局後，免費旋轉期間的 runtime log 顯示 `sessionFreeSpins=0`，所有 `isFreeSpin=false`。

**可重現記錄**：

- seed `3285004900`、spin index 3：實際 `160`，設計倍率結果應為 `480`。
- seed `3285006502`、spin index 4：實際 `146`，設計倍率結果應為 `438`。
- 完整記錄：[iphone17-v235-spin-log.json](../../../captures/slots-recheck-2026-11-10/iphone17-v235-spin-log.json)

**根因位置**：

- [slots.js:116](../../../app/src/main/assets/www/js/slots.js:116) 只讀 `session.freeSpins` / `session.isFreeSpin`。
- [slots.js:1246](../../../app/src/main/assets/www/js/slots.js:1246) 的 UI 真正依賴 `this.freeSpins` 判定免費旋轉。
- [slots.js:1271](../../../app/src/main/assets/www/js/slots.js:1271) 只遞減 `this.freeSpins`。
- [slots.js:1359](../../../app/src/main/assets/www/js/slots.js:1359) 將結果保存到 `state.slots.freeSpins`，沒有同步到 `state.slotsSession.freeSpins`。

**修復要求**：

- 只保留一個免費旋轉狀態來源，或在呼叫引擎前後明確同步狀態。
- 不得雙重遞減免費旋轉次數。
- 確保 `result.wins[*].isFreeSpin === true`，且每局倍率確實為 3 倍。
- 使用本交接中的兩個 seed 增加回歸測試，預期結果固定為 480 與 438。

### P1：金幣耗盡時救濟與充值彈窗不可見

**現象**：把金幣設為 0、免費旋轉設為 0 後拉桿，畫面沒有出現「緊急戰術救濟」。

**根因**：

- [slots.js:764](../../../app/src/main/assets/www/js/slots.js:764) 的 `handleOutOfGold()` 只設 `modal.hidden = false`。
- [app.css:937](../../../app/src/main/assets/www/css/app.css:937) 的 `.sheet` 預設 `display: none`，只有 `.sheet.open` 才會顯示。
- [slots.js:769](../../../app/src/main/assets/www/js/slots.js:769) 與 [slots.js:773](../../../app/src/main/assets/www/js/slots.js:773) 都沒有加上 `.open`。

**修復要求**：

- Bailout 與 Recharge 都用同一套 `openSheet/closeSheet` helper，確保 `hidden` 與 `.open` 狀態一致。
- 驗證第一次、第二次、第三次 bailout，以及超過 3 次後的 recharge 分支。
- 驗證領取 bailout 後金幣 +300、`luckyBailout` 保存、下一局保底流程可運作。

### P2：遊戲內版本號仍是 2.16.0

- [index.html:40](../../../app/src/main/assets/www/index.html:40) 顯示 `Version 2.16.0`。
- [index.html:581](../../../app/src/main/assets/www/index.html:581) 設定頁同樣顯示 `Version 2.16.0`。

**修復要求**：下一個修復版本統一升級到 `2.16.10 / Build 236`（或依版本策略選定更高版本），同步：

- Android `versionCode/versionName`
- iOS `CURRENT_PROJECT_VERSION/MARKETING_VERSION/Info.plist`
- 遊戲標題、設定頁、PersonalScreen
- OTA `index.html`、`manifest.plist`
- Release 包與 Git tag

### P2：iPad 大屏仍被固定 560px 中央欄限制

- [app.css:172](../../../app/src/main/assets/www/css/app.css:172) 的 `.stage` 使用 `width: min(100vw, 560px)`。
- iPad Release 可用，但左右及下方留白過大，沒有真正利用大屏。

**修復要求**：

- 手機維持目前窄屏布局。
- iPad 直向與橫向使用自適應容器，提升機台與赔率表可讀性。
- 不得引入新的橫向溢出、文字截斷或安全區重疊。
- 驗證 iPhone 393pt、Duo 466/669pt、iPad 820pt 以及 iPad 橫向。

### P2：Jackpot 顯示與結算邏輯脫節

- UI 固定顯示 GRAND / MAJOR / MINOR / MINI。
- `spin()` 回傳結果沒有 `isJackpot` 欄位；`isHighImpactSpin` 只是讀取不存在的欄位。[slots.js:1289](../../../app/src/main/assets/www/js/slots.js:1289)

**修復要求**：選定一個一致方案：

1. 實作可驗證的 Jackpot 條件、派彩、動畫與回歸測試；或
2. 移除會讓玩家以為可獲得真實獎池的 Jackpot 文案，改成與實際結算一致的展示。

### P2：Near-miss 文案宣稱下一局機率提高，但程式沒有提高機率

- 程式以 60% 機率製造兩個 Scatter 的 near-miss。[slots.js:97](../../../app/src/main/assets/www/js/slots.js:97)
- UI 顯示「下一把机会极高」「下一把一定出」。

**修復要求**：改成中性、可驗證的結果描述，或真正建立可測量的概率策略並加入測試。不要向玩家承諾程式沒有提供的機率提升。

## 4. 已確認正常的部分

- v2.16.7 的半屏全景突入立繪已在 v2.16.9 Release 實際出現，沒有再被滾輪框腰斬。
- MEGA HIT 彈層可顯示完整角色、標題、文案與「繼續」按鈕。
- iPhone 赔率表可完整顯示。
- iPad 赔率表可完整顯示，未觀察到裁切。
- AUTO/TURBO 連續旋轉不會 crash，狀態能保存。
- 伴遊切換可更新名稱、倍率與等級資訊。

## 5. Antigravity 修復後的驗收門檻

### 代碼與版本

- [ ] QA boost 不再於 Release 啟動流程執行。
- [ ] 免費旋轉倍率回歸測試通過，兩個固定 seed 結果為 480 / 438。
- [ ] Bailout / Recharge modal 可見、可關閉、可領取，狀態保存正確。
- [ ] Jackpot 顯示與實際結算一致。
- [ ] Near-miss 文案不再做無依據承諾。
- [ ] 版本號升級並在所有顯示與分發文件同步。

### 測試與包

- [ ] `node game/tools/test_slots_determinism.js`
- [ ] `node scripts/verify-game-logic.js`
- [ ] `./gradlew testDebugUnitTest assembleRelease`
- [ ] iOS Debug 全量 XCTest，記錄 passed/failed/skipped
- [ ] iPhone 17 Pro Release fresh install
- [ ] iPad Pro 13-inch Release fresh install
- [ ] AUTO/TURBO、免費旋轉、Bailout、Recharge、赔率表、伴遊切換、MEGA HIT、Cut-in
- [ ] Release 包與交付包資產 hash 一致
- [ ] `node scripts/probe_all_sources.mjs` 或等價全源探針
- [ ] 多端視口截屏與 overflow/404/console error 檢查
- [ ] Android 實機或可用模擬器；若環境沒有設備，必須明確標記 blocked
- [ ] OTA 包、Git commit、tag、GitHub Release 按專案規範同步

## 6. 不可誤宣稱的事項

- 本輪沒有實體 Android 或實體 iPhone/iPad 驗證。
- Android 只完成 Release APK 與 JVM/Gradle 驗證，沒有 ADB UI E2E。
- iOS 全量測試中的 6 個 skipped 不是通過證據，需在報告中列出其原因。
- Headless Chrome 的視口驗證不能替代原生 WKWebView、Android WebView 或實體設備驗收。

