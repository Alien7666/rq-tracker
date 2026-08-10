# RQ Tracker — UI/UX 重構 Brief（給 Claude.ai design）

## 怎麼用這份檔案

1. 把 `rq-tracker-mockup.html` 丟給 Claude.ai 的 **design**（或 chat 開 artifact）。
2. 連同下方「貼給 design 的 Prompt」一起送出。
3. design 產出新版 HTML/CSS 後，把檔案給回我（Claude Code），我會把
   `:root` 的 `--rq-*` token 值 + layout 調整 **port 回 JavaFX `rq-theme.css`**。

> 為什麼要繞 HTML：Claude.ai design 只吃 web (HTML/CSS/React)，**不能直接 build JavaFX**。
> 這份 mockup 用跟 JavaFX 同名的 token（web `--rq-accent` ↔ JavaFX `-rq-accent`），
> 所以 design 改完，port 幾乎是逐行抄值，不會走味。

---

## App 是什麼

- **RQ Tracker**：Windows 桌面 app（JavaFX），追蹤「變更需求單 RQ」的開發進度。
- 使用者：軟體開發者，整天開著，**長時間盯著 → 暗色優先、降低視覺疲勞**。
- 全繁體中文 UI。

## 畫面結構（mockup 已完整呈現）

```
┌─────────────┬────────────────────────────────────┐
│ side-panel  │ rq-header（固定，標題+進度條+%）      │
│  brand      ├────────────────────────────────────┤
│  toolbar    │ content-scroll（捲動）：             │
│  rq-list ↕  │   archive-banner（100% 時才出現）    │
│  (進度條)    │   note-block（備忘錄，黃左條）       │
│             │   section-card 共用流程（青左條）    │
│  status     │   version-card × N（A/B…，三欄）     │
│             │   section-card 共用最終交付（黃左條） │
└─────────────┴────────────────────────────────────┘
```

## 設計語彙現況

- Material 3 / Fluent 風，token 化雙主題（dark 預設 + light）。
- 主色 cool teal `#2DD4BF`。語意色：success 綠 / warn 黃 / danger 紅 / optional 紫。
- 左側 3px 彩條標示卡片類型（青=流程、黃=交付/備忘）。
- 圓角 7–10px，subtle dropshadow。

---

## ⚠ 重構硬限制（JavaFX CSS 能力，違反就 port 不回去）

| 能用 | 不能用 |
|------|--------|
| 顏色、漸層 linear-gradient | flexbox / grid（靠 HBox/VBox，比例 OK 即可）|
| 圓角 border-radius | `::before` / `::after` 偽元素 |
| 邊框 border（含單邊）| `transition` / `animation` / `transform` |
| 陰影 dropshadow | `calc()`、CSS filter |
| 字重、字級、字距 | 自訂 web font（用系統中文字型）|

**還要遵守：**
- 顏色一律走 `--rq-*` 變數，不要新寫死 hex（要新增色就加新 token）。
- **不要改 class 命名**（`.side-panel` `.section-card` `.check-item` `.version-badge`…）——它們對應 JavaFX styleClass，改名斷掉對應。
- dark + light 兩主題都要顧到（改 `:root` 也要同步改 `.theme-light`）。

---

## 貼給 design 的 Prompt（直接複製）

```
這是我的 JavaFX 桌面 app「RQ Tracker」的 UI 視覺複刻（單檔 HTML）。
它是給開發者長時間盯著用的進度追蹤工具，繁體中文、暗色優先。

請重構 UI/UX，目標：
1. 視覺更現代、層次更清楚，降低長時間使用的視覺疲勞。
2. 強化資訊階層：RQ 標題/進度、卡片類型、任務完成狀態要一眼分得出。
3. 進度條、勾選列、版本徽章的視覺可以更精緻。
4. dark 與 light 兩主題都要好看（點右下角鈕切換預覽）。

硬限制（這會 port 回 JavaFX，務必遵守）：
- 顏色只用 :root 的 --rq-* 變數，要加色就新增 token，不要寫死 hex。
- 不要改任何 class 名稱（.side-panel / .section-card / .check-item 等）。
- 不要用 ::before/::after、transition、animation、transform、calc()、CSS filter。
- 可改：配色值、間距、圓角、字級字重、陰影、layout 比例、hover/active 狀態。
- :root（dark）和 .theme-light 要同步調整。

請直接改這份 HTML，回傳完整檔案。
```

---

## design 回傳後我會做什麼

1. diff `:root` / `.theme-light` 的 `--rq-*` → 抄進 `rq-theme.css` 的 `-rq-*`。
2. 對照各 class 的間距/圓角/字級/陰影調整 → 改 JavaFX 對應規則。
3. layout 比例（側邊欄寬、欄距）→ 改對應 Java 元件或 CSS。
4. `mvn clean javafx:jlink` + jpackage 出新 MSI 驗證（依 CLAUDE.md 流程）。

> 對應檔：`src/main/resources/css/rq-theme.css`、`src/main/java/com/rqtracker/ui/`
```
