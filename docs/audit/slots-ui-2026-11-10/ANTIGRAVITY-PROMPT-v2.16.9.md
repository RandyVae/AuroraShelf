# 可直接交給 Antigravity 的提示詞

請在 `/Users/itou/Documents/ChatGPT/91pron` 接手並完成 Slots v2.16.9 / Build 235 的修復與重新交付。先閱讀：

- `AGENTS.md`
- `.ai/PROJECT_STATE.md`
- `.ai/HANDOFF.md`
- `.ai/ENVIRONMENT.md`
- `docs/audit/slots-ui-2026-11-10/ANTIGRAVITY-HANDOFF-v2.16.9.md`
- `docs/audit/slots-ui-2026-11-10/REPORT.md`
- `docs/audit/slots-ui-2026-11-10/FIX-REPORT.md`

不要只回覆計畫；請直接完成修復、測試、打包、驗收與交接。不要丟棄工作樹既有修改，也不要把 Debug/瀏覽器結果冒充 Release 或實體機證據。

## 目標

修復交接文件列出的 Slots 問題，並交付下一個版本。當前版本是 `v2.16.9 / Build 235`；本輪建議升級到 `v2.16.10 / Build 236`，同步 Android、iOS、遊戲內文字、OTA 網頁、manifest、Git tag 與 Release。

## 必修問題

1. 移除或嚴格隔離 `boot()` 中無條件執行的 `LGSave.applyQAFullTestResources(...)`。Release 不能自動得到 `99,999,999` 金幣、全英雄、全裝備或被強制開啟 18+。QA 能力只能由明確 Debug/Test 開關觸發。
2. 修復免費旋轉倍率。引擎和 `SlotsUI` 必須使用同一個免費旋轉狀態來源，免費旋轉的 `isFreeSpin` 必須為 true，倍率必須為 3。使用交接中的 seed `3285004900` 與 `3285006502` 回歸，預期 totalWin 為 480 與 438。
3. 修復 bailout/recharge sheet。不能只取消 `hidden`；必須正確加上 `.open` 或使用共用 modal helper。驗證 0 金幣時能看到 bailout、可領取 +300、`luckyBailout` 保存，3 次後正確進入 recharge 分支。
4. 修復遊戲內 `Version 2.16.0`，同步到新版本號。
5. 重做 iPad 大屏容器，移除對 560px 固定欄的依賴；保留手機窄屏穩定性，驗證 iPad 直向/橫向與 Duo 視口沒有溢出或截斷。
6. 讓 Jackpot 顯示與實際結算一致：要麼實作可測試的 Jackpot 結算，要麼移除會造成誤解的 Jackpot 宣稱。
7. 移除 Near-miss 中「下一把一定出」「下一把机会极高」等沒有機率依據的承諾，改為中性、可驗證的結果描述，或實作可測試的概率策略。

## 測試要求

先在乾淨安裝/乾淨 WebKit 存檔上驗證，不要沿用上一輪免費旋轉或 QA 資源。至少完成：

- `node game/tools/test_slots_determinism.js`
- `node scripts/verify-game-logic.js`
- 新增或更新免費旋轉、QA 隔離、modal 可見性、Jackpot 結算的回歸測試
- `./gradlew testDebugUnitTest assembleRelease`
- iOS Debug 全量 XCTest，報告 passed/failed/skipped
- iPhone 17 Pro Release：冷啟動、普通旋轉、AUTO、TURBO、免費旋轉、Bailout、Recharge、赔率表、伴遊切換、MEGA HIT、Cut-in
- iPad Pro 13-inch Release：直向與橫向，檢查大屏利用率、文字、按鈕與彈層
- 檢查 Release APK 與交付包的 Slots 資產 hash；iOS Simulator 使用同一 HEAD 的 Release 建置
- 執行全源探針：`node scripts/probe_all_sources.mjs` 或當前專案等價命令
- 做多端 overflow、404、console error、截圖與 runtime log 檢查

如果 Android ADB 或實體 Apple 設備不存在，必須把它列為 blocked，不能寫成通過。

## 交付要求

- 先讀實際代碼與現有測試，再用最小改動修復；不要順手重構無關模組。
- 版本號必須遞增，且客戶端、遊戲內、OTA、manifest 全部一致。
- 按 `AGENTS.md` 完成 Release 打包、簽名、OTA 原子覆蓋、遠端碎片清理、磁碟容量檢查、Git commit/tag/push/Release。
- 不要把密鑰、Token、密碼或伺服器敏感資訊寫入交接文件。
- 更新 `.ai/PROJECT_STATE.md`、`.ai/HANDOFF.md` 與專案 `MEMORY.md` 的當前狀態，然後執行：
  `python3 ~/.agent-memory/sync.py --project /Users/itou/Documents/ChatGPT/91pron`

## 最終報告格式

請用香港繁體中文回報：

1. 實際修改的檔案與每個問題的修復方式。
2. 新版本號、Build、APK/IPA 路徑、SHA-256。
3. iOS/Android 測試 passed/failed/skipped 數量。
4. iPhone/iPad/Android 哪些是 Release 實測，哪些是 Debug、瀏覽器或靜態驗證。
5. 全源探針與多端 UI 截圖結果。
6. 仍然 blocked、skipped 或未能證明的事項。
7. Git commit、tag、push、OTA 與 GitHub Release 證據。

