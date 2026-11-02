# 欲望女神 本地版 · Lust Goddess Local Edition (v2.19)

> 本目錄整合自 `project-hgame`，已正式併入「極光視界」（AuroraShelf）Android 客戶端。
> 可在 App 內「我的」分頁中的「遊戲」模塊直接啟動，支援 100% 本機離線運行、確定性回放、Spine 骨骼動畫與持久化存檔。

---

## 一、目錄結構與架構索引

| 目錄 / 文件 | 說明 |
|---|---|
| `BUILD_MANIFEST.json` | 遊戲版本元數據與各子系統清單（v2.19，Schema v2） |
| `web/` | 軟連結指向 `../app/src/main/assets/www/`，包含完整的 HTML5/WebGL 遊戲前端 |
| `docs/` | 遊戲架構設計文檔、P2 戰術特勤規範、驗收標準與審計記錄 |
| `tools/` | 離線驗證工具鏈（Spine 骨骼校驗、數值模擬、確定性戰鬥測試） |

---

## 二、Android 端集成架構

遊戲在 Android 端採用原生獨立 Activity 隔離架構，確保大型 WebGL / Spine 畫布在退出時完整釋放記憶體：

1. **入口**：
   - 位於「我的」（`PersonalScreen.kt`）的「娛樂」板塊：「遊戲」導航條目。
   - 點擊後啟動 `com.aurorashelf.app.game.GameActivity`。
2. **本地資源服務器（`GameAssetServer.kt`）**：
   - 監聽 `127.0.0.1:8765`（支持動態回退），直接服務 APK 內建資產 `www/`。
   - 實現 HTTP 206 Partial Content（Range 分段請求），保障 Spine 骨骼、1080p CG 視頻及 M4A 音訊無卡頓播放。
3. **持久化存檔（`GameSaveBridge.kt`）**：
   - 透過 `@JavascriptInterface` 提供 `window.AndroidSave` 橋接，存入獨立 SharedPreferences（`lg_local_save`），不受 WebView 儲存清理影響。
4. **全螢幕沉浸式體驗**：
   - 強制豎屏（`SCREEN_ORIENTATION_PORTRAIT`）與異形螢幕劉海適配（`LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`）。
   - 支援系統原生返回手勢攔截與退出回跳。

---

## 三、本機開發與離線測試

可在本目錄下使用以下命令進行離線調試與驗證：

```sh
# 1. 本地啟動 HTTP 服務器測試 Web 端
cd game && python3 web/serve.py 8765

# 2. 運行 1000 場戰鬥確定性測試
node game/tools/test_battle_determinism.js

# 3. 運行全量 55 英雄 + 168 敵人 Spine 與音訊資產校驗
python3 game/tools/verify_curated_assets.py
```
