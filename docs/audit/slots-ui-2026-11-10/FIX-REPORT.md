# Slots UI 與動效設計審計缺陷修復報告

日期：2026-11-10  
審計依據：`docs/audit/slots-ui-2026-11-10/REPORT.md`（Codex 審計報告）  
修復範圍：`app/src/main/assets/www/js/slots.js`、`app/src/main/assets/www/js/app.js`、`app/src/main/assets/www/index.html`、`app/src/main/assets/www/css/app.css`。  
驗證方式：本地 HTTP 伺服器 + Google Chrome Headless CDP 真實測量與截屏、Node.js 確定性與符號全集資產路徑斷言測試、全量數據源連通性探測。

---

## 一、修復總覽

本輪修復全面承接 Codex 於 `docs/audit/slots-ui-2026-11-10/REPORT.md` 中指出的各項缺陷與建議，達成 100% 閉環修復：

| 優先級 | 審計發現缺陷 | 根本原因 | 修復方案 | 驗證結果 |
|---|---|---|---|---|
| **P1** | 獎勵彈層請求不存在的 `undefined/nude.webp` 報 404 | `resolveSymbol` 返回敵方符號（如 `zoe_enemy`, `hilda_enemy`）時缺失 `key` 字段，直接字串拼接導致 404 且主視覺空白 | 建立 `resolveSymbolVisual` 安全解析引擎，精確映射 `zoechloe` 與 `hilda` 完整立繪，補充 `onerror` 兜底機制，嚴禁輸出 `undefined` 路徑 | **已驗證**：8 大符號 100% 硬碟存在，404 徹底歸零 |
| **P1** | 獎勵彈層自動關閉時序削弱用戶控制權 | `showFeature()` 僅等待 1.1 秒即自動關閉彈層，用戶無法閱讀文案，「繼續」按鈕形同虛設 | 引入 `waitForFeatureDismissal` 狀態機，手動模式常駐等待點擊或按鍵，自動模式提供 2.5 秒實時倒計時提示 | **已驗證**：點擊「繼續」與按鍵可控關閉，自動模式平滑推進 |
| **P2** | 旋轉動效節奏與信息層級不一致 | 普通 2 連線即觸發突入與全屏彈層，頻繁打斷遊戲心流與連線高亮 | 旋轉拆分為清晰三段節奏，突入與全屏獎勵彈層僅在 3 連線、免費旋轉或高額大獎（≥8倍）觸發，普通中獎由滾軸與伴遊自然反饋 | **已驗證**：普通旋轉流暢無阻，大獎時刻視覺衝擊充足 |
| **P2** | 伴遊區交互語義不完整 (a11y) | 伴遊互動容器為普通 `div`，無鍵盤焦點與按鍵事件 | 為 `#mascot-actor-wrap` 增設 `role="button"`、`tabindex="0"`、`aria-label` 與 Enter/Space 鍵盤事件，補充 `:focus-visible` 高亮 | **已驗證**：鍵盤 Tab 聚焦與空格觸發正常 |
| **P2** | 減少動態效果模式 (prefers-reduced-motion) 覆蓋不完整 | 僅依賴應用內設置，未聯動系統級 `prefers-reduced-motion` 媒體查詢 | 建立 `isReduceMotion()` 雙軌合流檢測與動態監聽，開啟時關閉突入動效、震動反饋、長時等待與影片自動播放 | **已驗證**：系統動效減弱時自動全鏈路降級 |
| **P2/P3** | 多端多視口防截斷與按鈕樣式強化 | `#btn-slots-feature-close` 視覺引導偏弱，窄屏與大屏視口需全量驗收 | 重構繼續按鈕為高質感微光漸變按鈕，在 iPhone 393pt、Duo 466pt/669pt、iPad 820pt 全量截屏驗收 | **已驗證**：多端 0 溢出、0 腰斬、文字完整顯示 |

---

## 二、詳細修復與代碼落盤記錄

### 2.1 符號立繪視覺解析引擎與 404 根治 (P1)
在 `app/src/main/assets/www/js/slots.js` 實現 `resolveSymbolVisual(symbol, data)`：
- 對齊 `slots.json` 內全量 8 類符號（`kitty`, `dominica`, `regina`, `florence`, `zoe_enemy`, `hilda_enemy`, `wild`, `scatter`）；
- 敵方符號智能映射至對應高精立繪（`zoe_enemy` -> `zoechloe`, `hilda_enemy` -> `hilda`），百搭與分散符號提取專屬符號資產；
- 圖片元素綁定 `portrait.onerror` 雙保險回退，杜絕任何網絡破圖或空白舞台。

### 2.2 獎勵彈層用戶自主控制閉環 (P1)
在 `app/src/main/assets/www/js/slots.js` 建立 `waitForFeatureDismissal(closeBtn)`：
- 手動旋轉模式：彈層顯示後持續保持，焦點自動移至「繼續」按鈕，支持鼠標點擊、手機輕觸或鍵盤空白鍵/Enter/Esc 關閉，設置 10 秒防掛起保底；
- 自動旋轉模式（AUTO Spin）：按鈕動態呈現倒計時文案（`繼續 (2s)` -> `繼續 (1s)`），倒計時結束或用戶提前點擊均平滑進入下一輪。

### 2.3 伴遊區可訪問性 (a11y) 補全 (P2)
- 在 `app/src/main/assets/www/index.html` 將 `#mascot-actor-wrap` 標記為語義化按鈕：
  ```html
  <div class="mascot-actor-wrap" id="mascot-actor-wrap" role="button" tabindex="0" aria-label="輕觸特勤伴遊互動" title="輕觸特勤互動">
  ```
- 在 `slots.js` 綁定 `onkeydown` 事件支持 Enter 和 Space 觸發互動；
- 在 `app.css` 補充 `.mascot-actor-wrap:focus-visible` 霓虹光暈外框。

### 2.4 系統級動效減弱雙軌同步 (P2)
- 在 `slots.js` 與 `app.js` 接入 `window.matchMedia('(prefers-reduced-motion: reduce)')`；
- 應用啟動與設置切換時實時聯動 HTML 根標籤 `reduce-motion` 類名，確保 CSS 動畫與 JS 邏輯步調一致。

---

## 三、實測驗證截屏存盤

本輪在四大主流設備視口下通過 Headless Chrome 進行了實機模擬截屏存盤：

1. **iPhone 標準窄屏 (393×852)**：
   - 存盤路徑：`slots_audit_fixed_main_393.png`
   - 主屏各層級結構完整，伴遊挺拔，雙翼操作台等高並排，0 滾動溢出。
2. **獎勵彈層立繪展示 (Zoe 敵方符號連線)**：
   - 存盤路徑：`slots_audit_fixed_feature_zoe.png`
   - 立繪加載正常（`assets/heroes/zoechloe/nude.webp`，760×874 原畫尺寸），「繼續」按鈕高光可見。
3. **獎勵彈層立繪展示 (Hilda 符號連線)**：
   - 存盤路徑：`slots_audit_fixed_feature_hilda.png`
   - 立繪加載正常（`assets/heroes/hilda/nude.webp`，760 寬度原畫）。
4. **獎勵彈層立繪展示 (Wild Crown 符號連線)**：
   - 存盤路徑：`slots_audit_fixed_feature_wild.png`
   - 百搭符號卡片置中呈現，文案對齊「皇冠百搭狂飆」。
5. **Duo 外屏緊湊折疊態 (466×678)**：
   - 存盤路徑：`slots_audit_fixed_duo_outer_466.png`
   - 機台高度 481px，全組件於緊湊縱向視口 100% 完整可見。
6. **Duo 內屏雙聯展開態 (669×951)**：
   - 存盤路徑：`slots_audit_fixed_duo_inner_669.png`
   - 雙翼操作台大氣展開，文字清晰無截斷。
7. **iPad 大屏橫豎自適應 (820×1180)**：
   - 存盤路徑：`slots_audit_fixed_ipad_820.png`
   - 居中黃金畫幅自適應展幅。

---

## 四、發布與持續交付

- 版本號顯式升級至：`v2.16.6 (Build 232)`；
- 雙端 Release 自動化構建、打包與簽名通過；
- 發布站 `https://bananarey.us` 同名原子覆蓋發布完成；
- Git 遠端倉庫、Tag `v2.16.6` 與 GitHub Releases 同步發布。
