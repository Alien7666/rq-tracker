# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`RQ_TODO_Tracker.html` is a **single-file, zero-dependency HTML application** (Traditional Chinese UI) for tracking software development progress of "RQ" (變更需求單 / Change Requirement) items. It runs entirely in the browser — no build step, no server, no npm.

## Running the App

Open `RQ_TODO_Tracker.html` directly in a browser. No build or install is needed.

For development, use a local HTTP server to avoid browser security restrictions on the File System Access API:

```bash
# Python
python -m http.server 8080

# Node.js
npx serve .
```

## Architecture

All HTML, CSS, and JavaScript lives in one file (~2500+ lines). Sections are ordered:

1. **`<head>` / inline SVG favicon** — dynamic favicon injection via `<script>` before styles
2. **`<style>`** — all CSS, CSS custom properties (`--accent`, `--bg`, `--text-*`, etc.)
3. **`<body>` HTML structure** — app shell: header, main content area, and all modal overlays
4. **`<script>`** — all application logic (starts around line 1470)

### Data Model

Data is persisted in **`localStorage`** only. There is no backend.

| Key | Content |
|-----|---------|
| `rqtracker_index` | `string[]` — ordered list of RQ IDs |
| `rqtracker_rq_<id>` | `RQData` object for each RQ |

**`RQData` shape:**
```js
{
  id: string,           // e.g. "RQ100051742_..."
  projectNum: string,   // e.g. "POSMS"
  versions: [{ name: string }],
  checks: { [taskKey: string]: true },   // completed task keys
  note: string,         // freeform markdown-style note
  collapseState: {},    // UI collapse state per section
  // timestamps managed by updateSectionTimestamps()
}
```

### Task System

Tasks are generated dynamically — not stored — by pure functions:

| Function | Generates |
|----------|-----------|
| `sharedFlowTasks()` | Tasks shared across all RQs (flow/process steps) |
| `versionDevTasks(vIdx)` | Per-version development checklist |
| `versionDeliverables(vIdx, rq, vName)` | Deliverable files with hardcoded `D:\Systex\…` paths |
| `versionSVNTasks(vIdx, vName, rq)` | SVN commit/document tasks with `C:\SVN\…` paths |
| `finalDeliveryTasks(rq)` | Final delivery checklist |
| `sharedSVNTasks(rq)` | Shared SVN maintenance record tasks |

Task keys (used as `checks` object keys) encode version index + task identity. `calcProgress()` and `calcVersionProgress()` re-derive all tasks from these functions to compute completion %.

### File System Access (Browser API)

The app uses the **File System Access API** (`showDirectoryPicker`) for:
- **Backup folder** (`setupBackupDir`) — auto-saves JSON to a user-chosen directory
- **Downloads/SVN folder scanning** (`setupDiskFolder`) — scans for files matching RQ IDs

These features are gated and degrade gracefully when the API is unavailable.

### Hardcoded Paths

Several Windows paths are hardcoded in task generators. When modifying, search for:
- `D:\\Systex\\` — deliverable output root
- `C:\\SVN\\新系統開發\\` — SVN repository root
- `C:\\SVN\\新系統開發\\9_文件\\99_共用文件\\` — shared documents root

### Key UI Functions

| Function | Purpose |
|----------|---------|
| `renderTabs()` | Renders the RQ tab bar |
| `renderRQ(id)` | Renders the entire active RQ view |
| `renderVersionCard(rq, vIdx)` | Renders one version's card |
| `renderCheckList(tasks, rq, ...)` | Renders a checklist of tasks |
| `checkBoxClick(rqId, key)` | Handles checkbox toggling with confirm/uncheck logic |
| `openModal(mode, ...)` | Opens the new/edit RQ modal |
| `exportData()` / `triggerImport()` | JSON export and import |

### Localization

All UI text is Traditional Chinese (`zh-TW`). Task labels, folder names, and error messages are in Chinese.

## Post-Modification Checklist（每次修改完成後必須執行）

**每次完成程式修改，Claude 必須依序自動執行以下所有步驟，不得跳過：**

1. 更新 `BuildInfo.VERSION`（`src/main/java/com/rqtracker/BuildInfo.java`）
2. 更新 `pom.xml` 中的 `<version>`（與 BuildInfo.VERSION 保持一致）
3. 執行 jlink（產生 Runtime Image）：
   ```bash
   mvn clean javafx:jlink
   ```
4. 執行 jpackage（產生 MSI 安裝檔）：
   ```bash
   mvn exec:exec@jpackage
   ```
   > MSI 輸出至 `target/installer/RQTracker-{VERSION}.msi`
5. 建立 GitHub Release 並上傳 MSI：
   ```bash
   gh release create v{VERSION} \
     --title "RQ Tracker {VERSION}" \
     --notes "{本次變更說明}" \
     target/installer/RQTracker-{VERSION}.msi
   ```
6. 確認 Release URL 已產生，所有使用者下次啟動將自動偵測到新版。

> GitHub Repo：`https://github.com/Alien7666/rq-tracker`
> Update API：`https://api.github.com/repos/Alien7666/rq-tracker/releases/latest`
> MSI 說明：`--description` 請使用純 ASCII（WiX 不支援中文 description 欄位）