# RQ Tracker 各檔案職責規則

> 產生日期：2026-06-05
> 範圍：JavaFX 桌面版（`src/main`、`src/test`）。`RQ_TODO_Tracker.html` 為舊單檔網頁版，邏輯已移植至 Java，故下列多數類別標註「對應 HTML 的 xxx」。
> 架構分層：`model`（資料）→ `service`（業務/IO）→ `ui`（畫面）；`util`（無狀態工具）橫跨各層。

---

## 進入點 / 設定

| 檔案 | 職責規則 |
|------|---------|
| `BuildInfo.java` | 單一版本號常數 `VERSION`。每次改版須同步更新此值與 `pom.xml`。不可實例化。 |
| `RQTrackerApp.java` | JavaFX `Application` 入口。決定資料目錄（`%APPDATA%\RQTracker`，無則 `~/.rqtracker`）、載入 `AppConfig` 與 `DataStore`、建主視窗、還原/儲存視窗狀態、關閉時 shutdown 並存設定。 |
| `module-info.java` | JPMS 模組宣告。`requires` JavaFX/Jackson/java.net.http 等；`opens` model 給 Jackson 反射、`opens` UI 給 FXML；`exports` 各 package。新增需 Jackson 序列化的 package 須在此 `opens`。 |

---

## model（純資料，與 HTML localStorage JSON 完全相容）

| 檔案 | 職責規則 |
|------|---------|
| `AppData.java` | 匯出/匯入 JSON 頂層包裝：`version` / `savedAt` / `index` / `rqs` / `history`。setter 對 null 做防禦填空集合。`@JsonIgnoreProperties(ignoreUnknown)` 容錯未知欄位。 |
| `RQData.java` | 單筆 RQ 完整模型：id、projectNum、versions、checks、timestamps、note、collapseState、sectionDone、versionFiles、createdAt、archivedAt、bugFix。提供 check/uncheck/isChecked 等便利方法。`versionFiles` 用 `Map<String,String>`（字串化 int key）以相容 JS 行為。`bugFix=true` 時跳過測試報告類任務。 |
| `RQVersion.java` | 一個版本，只含 `name`（如「網路郵局中文版 pstID」）。vid 由 `PathUtils.versionId` 取最後一詞解析。 |
| `TaskDef.java` | 任務定義（動態生成，不落地）。Builder 模式；欄位含 key/step/label/sub/folder/filename/codeRoot/codeVid/warn/optional/checkHasFiles/checkModTime/createFolder。`getCreateFolder()` 在 null 時回退 folder。 |
| `TaskResult.java` | `record`，磁碟掃描結果。`ScanState` 枚舉：FILE/PARTIAL/FOLDER/NONE/STALE/UNKNOWN/SCANNING。提供工廠方法與 `isComplete()`（僅 FILE 為真，可自動打勾）。 |
| `VersionPreset.java` | 使用者可編輯的版本預設：vid + displayName + sbomFolder。`toCombinedName()` 組「顯示名 vid」存進 `RQVersion.name`。取代原寫死於 PathUtils 的 SBOM 對應表。 |

**model 規則**：純 POJO/record，不含業務邏輯或 IO；序列化欄位須與 HTML JSON 對齊。

---

## service（業務邏輯 + 檔案/網路 IO）

| 檔案 | 職責規則 |
|------|---------|
| `AppConfig.java` | 設定管理，存 `settings.json`。含路徑（downloadsRoot/svnRoot/backupDir）、視窗狀態、備份時間、更新 URL、主題、SplitPane 位置、versionPresets。`load()` 後跑 `migrate()` 自動修錯值與 seed 預設版本。多數 setter 即時 `save()`；`splitPositions`/`noteHeight` 由呼叫端 debounce。getter 對非法值回退預設並做防禦拷貝。單例 `getInstance()` 供 PathUtils/ThemeManager 取用。 |
| `BackupService.java` | 自動備份（每 6 分鐘）。寫 `RQ_backup_{yyyyMMdd_HHmmss}.json`，清理規則：24h 內留最近 50、24h–7d 每天留最早一筆、>7d 全刪。提供 `BackupStatus` 與 UI 顯示文字。全靜態，不可實例化。 |
| `DataStore.java` | 主資料儲存，存 `data.json`，格式相容 HTML `getAllData()`。啟動一次性載入記憶體；每次寫入用單執行緒 `ExecutorService` 非同步落地（避免卡 UI）。提供 RQ CRUD、rename、history 歸檔/復原/刪除、import/export 快照。`getIndex`/`getHistory` 回不可修改 view。`shutdown()` 等待 pendingSave。 |
| `DiskScanService.java` | 磁碟掃描，直接讀 AppConfig 路徑（免授權彈窗）。`scan()` 在背景執行緒比對任務檔案，結果進 `ConcurrentHashMap` 快取。`autoCheck()` 兩規則：(1) state=FILE 自動勾選；(2) 委派 `TaskCascadeService` 跨欄回填。支援檔名通配 `\d{8}` / `Vx.x`、checkModTime（檔案改動時間 > RQ 建立時間）、checkHasFiles（比對 versionFiles 清單）。 |
| `FileListGenerator.java` | 產生 `list.txt`：版本改動程式段落（vid\路徑）+ 掃描 `D:\Systex\{rqId}\` 其他子目錄（安全性文件/SBOM/測試報告，依固定順序）。`writeListFile()` 寫入 RQ 根目錄（UTF-8）。全靜態。 |
| `FolderCreatorService.java` | 批量建立 `D:\Systex` 下交付資料夾。先做日期前綴資料夾更名（`{舊日期}_{rqNum}`→今日，避免每日重建空殼；多個只更名最早一個其餘留手動）；只建 downloadsRoot 下、非 optional 任務的資料夾。回 `[created, failed]`。 |
| `ImportExportService.java` | JSON 匯入/匯出，用 JavaFX `FileChooser`。匯出加 `savedAt`；匯入先驗格式（須有 index）、彈確認對話框（覆蓋無法復原）才寫入。透過回呼回報 Toast 與刷新。全靜態。 |
| `ProgressCalc.java` | 進度計算（移植 HTML calcProgress/calcVersionProgress）。`Progress` record 提供 percent/isComplete。整體進度排除 optional 任務；版本進度含 dev+deliverables+svn。`allTasks()` 彙總全部任務供掃描用。全靜態。 |
| `TaskCascadeService.java` | 任務勾選連動規則：依「交付給客戶」欄位狀態回填同版本「開發流程」（如 del_code→sf_01/sf_02/dev_03；zapBoth→整串 dev）。回傳是否有變更。全靜態。 |
| `TaskFactory.java` | 任務定義生成器（移植 HTML 6 函數）：sharedFlowTasks / versionDevTasks / versionDeliverables / versionSVNTasks / finalDeliveryTasks / sharedSVNTasks。無狀態，路徑設定注入；含 `DEFAULT_DOWNLOADS_ROOT`/`DEFAULT_SVN_ROOT`。`bugFix` 時跳過 dev_07b / del_testrpt / svn_004 / svn_003 紀錄單。 |
| `UpdateService.java` | 軟體更新：GitHub Releases API（或自訂 JSON）查版本、語意化版號比較、下載 MSI（含進度/取消）、產生 UTF-16LE VBScript 經 schtasks 啟動 msiexec（繞過 jpackage Job Object 連坐殺子程序問題）。全靜態。 |

**service 規則**：業務與 IO 集中於此；UI 不可直接碰檔案/網路。耗時操作走背景執行緒、結果以回呼或 `Platform.runLater` 回 UI。多為無狀態靜態工具（DataStore/DiskScanService/AppConfig 例外，持狀態）。

---

## util（無狀態工具，橫切各層）

| 檔案 | 職責規則 |
|------|---------|
| `ClipboardUtils.java` | 複製純文字到系統剪貼板。須在 JavaFX 執行緒呼叫。 |
| `DateTimeUtils.java` | 日期時間格式化：zh-TW 時間戳、ISO 8601、備份日期/時間/時間戳、ISO↔zh-TW 互轉。集中所有格式 pattern。 |
| `DialogHelper.java` | 無邊框透明視窗工具：`initTransparent`（在 initOwner 前呼叫）、`applyTheme`（透明背景+載 CSS+註冊 ThemeManager）、`makeMovable`（header 拖曳移窗）、`makeResizable`（8 向邊緣縮放）。所有自訂 Dialog 共用。 |
| `PathUtils.java` | 路徑/名稱工具（移植 HTML）：todayDate、rqNumber、versionId、versionDisplayName、sbomSystemFolder/CustomerFolder（查 AppConfig.versionPresets）、winSafeName（過濾 Windows 非法字元）、toWindowsPath、ensureTrailingSlash。全靜態。 |
| `ThemeManager.java` | dark/light 主題集中切換。以 WeakReference 註冊所有 Scene，切換時加 `theme-light`/`theme-dark` class 到 root，與 `AppConfig.themeMode` 同步，並通知 listener。 |

**util 規則**：純函式/無業務狀態（ThemeManager 持 Scene 註冊表例外）；可被任何層呼叫，不反向依賴 UI 具體類別。

---

## ui.controller

| 檔案 | 職責規則 |
|------|---------|
| `MainController.java` | 主控制器，協調 SidePanel／RQContentPanel／Toast／Confirm／各 Dialog。建場景（BorderPane 側邊欄 + 毛玻璃遮罩 + overlay）；負責 RQ 選取/渲染/新增/編輯/刪除/歸檔、側邊欄工具選單接線、磁碟掃描排程（5s 後每 180s）、自動備份排程（每 6 分）、啟動時更新檢查、Toast/glass 顯示、section 時間戳更新。 |

**controller 規則**：只做協調與事件接線，業務委派 service、畫面委派 component/dialog；背景任務結果用 `Platform.runLater` 回 UI。

---

## ui.component（可重用畫面元件）

| 檔案 | 職責規則 |
|------|---------|
| `CheckItemRow.java` | 單一任務勾選列。Canvas 自繪勾選方格（主題色硬寫，Canvas 讀不到 CSS 變數）；點列=勾選、點方格=勾選/確認取消；顯示 step/label/sub/warn/optional 標籤、檔案提示與複製鈕、時間戳；`setScanState()` 套掃描狀態圓點。 |
| `CollapsibleCard.java` | 通用可折疊卡片：標題列點擊展開/折疊，含淡入淡出動畫。提供 setExpanded/isExpanded/getContentBox。 |
| `ConfirmDialog.java` | 通用確認對話框（DANGER/WARNING/INFO）。透明無邊框、Enter 確認 Esc 取消、自動 focus 確認鈕。提供 `confirmDelete`/`confirmArchive` 便利方法。全靜態。 |
| `RQContentPanel.java` | RQ 內容主面板（對應 HTML renderRQ）：進度橫幅、備忘錄（debounce 600ms 自動存 + 可拖曳高度）、共用流程卡、版本卡（三欄 SplitPane：開發/SVN/交付，分隔位置持久化）、最終交付（雙欄）。處理勾選/取消（含連動與 section 時間戳）、折疊狀態持久化。`dispose()` 停掉 PauseTransition。 |
| `RQListItem.java` | 側邊欄單一 RQ 項目：ID（可換行）+百分比+迷你進度條+刪除鈕（hover 才顯示）。點列選取。 |
| `RQTabBar.java` | 水平可滾動 RQ 標籤列（舊版佈局；現 `TabEntry` record 仍被 SidePanel 沿用）。 |
| `SectionTimestamp.java` | 區段完成時間戳工具：某區段必填任務全勾則記錄完成時間，有未勾則清除。寫入 `rq.sectionDone`。全靜態。 |
| `SidePanel.java` | 左側邊欄：商標、新增 RQ 鈕、工具 ContextMenu（設定/主題/更新/歷史/匯入匯出/建資料夾/生清單）、RQ 列表、狀態列（備份狀態+掃描指示）、更新徽章。MenuItem 公開給 MainController 接線。 |
| `ToastNotification.java` | 淡入淡出 Toast（對應 HTML showSaveToast）。放場景最外層底部，`show(message)` 觸發 fadeIn→停留→fadeOut。滑鼠穿透。 |

**component 規則**：可重用、透過建構子回呼與外界溝通，不直接持 DataStore 全域狀態（RQContentPanel 例外，需讀寫資料）；自繪/動畫狀態自行清理。

---

## ui.dialog（彈出視窗）

| 檔案 | 職責規則 |
|------|---------|
| `FolderListDialog.java` | 資料夾清單視窗。按 folder 分組分「交付給客戶」「SVN 內部」兩段；每檔列可勾選/取消（含連動與 section 時間戳）、複製檔名/完整路徑。勾選後重建內容。 |
| `HistoryDialog.java` | 歷史紀錄（已歸檔 RQ）。列出每筆 + 完成度，提供復原（回呼 MainController）與永久刪除（二次確認）。操作後重建列表（index 會變）。 |
| `NewEditRQDialog.java` | 新增/編輯 RQ。欄位：RQ 名稱（編輯時鎖定）、專案代號、修復問題 checkbox、版本下拉（取 AppConfig.versionPresets）。送出回 `Result` record 給 MainController。Enter 送出 Esc 取消。 |
| `PlaceholderDialog.java` | 歷史佔位類，僅為早期保 package 非空。可刪除。 |
| `SettingsDialog.java` | 路徑設定：成品交付/SVN/備份資料夾（即時驗存在性圖示 + 瀏覽鈕）、更新 URL、版本管理 TableView（vid 必填且不可重複，可新增/移除/還原預設）。儲存前驗證並寫回 AppConfig。 |
| `UpdateDialog.java` | 軟體更新 5 狀態機：CHECKING→UP_TO_DATE/UPDATE_AVAILABLE→DOWNLOADING→READY_TO_INSTALL（+ERROR）。背景執行緒查版/下載；安裝倒數 3 秒後 `exitForUpdate`。可略過版本/取消下載。 |
| `VerFilesDialog.java` | 版本改動程式清單輸入。每行一相對路徑（從 src/ 起）；「查看清單」產生 `[版本]\nvid\路徑` 格式預覽視窗（可複製全部）。儲存寫回 `RQData.versionFiles`。 |
| `package-info.java` | dialog package 說明。 |

**dialog 規則**：一律透過 `DialogHelper` 做透明無邊框 + 主題 + 可移動/縮放；統一 Esc 關閉、主鈕 Enter；耗時操作走背景執行緒；以回呼通知 MainController 刷新。

---

## test（JUnit 5，鏡像 main 套件結構）

| 檔案 | 職責規則 |
|------|---------|
| `DiskScanServiceTest.java` | 驗 `autoCheck` 連動：交付欄全勾回填開發流程、del_code 檔案回填 dev_03/sf_01/sf_02。 |
| `PathUtilsTest.java` | 驗 rqNumber/versionId/versionDisplayName/sbomSystemFolder/winSafeName/todayDate 與 HTML 版行為一致。 |
| `ProgressCalcTest.java` | 驗整體/版本進度計算、optional 不計分、percent 四捨五入、null/越界防禦。 |
| `TaskFactoryTest.java` | 驗 6 任務函數生成的 key 順序/格式、warn/optional/checkHasFiles/checkModTime 旗標、路徑含 rqId/專案名與 HTML 完全一致。 |

**test 規則**：純邏輯層（model/service/util）才寫單元測試，鏈式 key 與路徑格式須與 HTML 版鎖死；UI 層不寫單元測試。

---

## 全域慣例（跨檔規則）

- **不可變優先**：getter 回防禦拷貝/不可修改 view（DataStore.getIndex、DiskScanService.getCacheCopy、AppConfig.getSplitPositions）。
- **JSON 相容**：所有 model 欄位須對齊 HTML localStorage 格式，新欄位用 `@JsonIgnoreProperties(ignoreUnknown)` 容錯。
- **執行緒**：IO/網路/掃描走背景執行緒（daemon），UI 更新一律 `Platform.runLater`。
- **路徑寫死**：`D:\Systex`、`C:\SVN\新系統開發` 等預設值集中於 AppConfig/TaskFactory，改動須同步。
- **改版流程**：見 `CLAUDE.md`「Post-Modification Checklist」（更新 BuildInfo+pom → jlink → jpackage → GitHub Release）。
- **語系**：所有 UI 文字、任務標籤、錯誤訊息為 zh-TW。
