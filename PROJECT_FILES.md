# RQ_Task 專案檔案清單

> 產生日期：2026-06-05
> 來源：`git ls-files`（追蹤中）＋ `git ls-files --others --exclude-standard`（未追蹤）

## 根目錄

```
.gitignore
CLAUDE.md
RQ_TODO_Tracker.html
package.bat
pom.xml
```

## 設定

```
.claude/settings.local.json
```

## 備份（v1-ui）

```
backup/v1-ui/src/main/java/com/rqtracker/RQTrackerApp.java
backup/v1-ui/src/main/java/com/rqtracker/service/AppConfig.java
backup/v1-ui/src/main/java/com/rqtracker/ui/component/RQContentPanel.java
backup/v1-ui/src/main/java/com/rqtracker/ui/component/RQTabBar.java
backup/v1-ui/src/main/java/com/rqtracker/ui/controller/MainController.java
backup/v1-ui/src/main/resources/css/rq-theme.css
```

## 主程式 (src/main/java)

### 進入點
```
src/main/java/com/rqtracker/BuildInfo.java
src/main/java/com/rqtracker/RQTrackerApp.java
src/main/java/module-info.java
```

### model
```
src/main/java/com/rqtracker/model/AppData.java
src/main/java/com/rqtracker/model/RQData.java
src/main/java/com/rqtracker/model/RQVersion.java
src/main/java/com/rqtracker/model/TaskDef.java
src/main/java/com/rqtracker/model/TaskResult.java
src/main/java/com/rqtracker/model/VersionPreset.java          (未追蹤)
```

### service
```
src/main/java/com/rqtracker/service/AppConfig.java
src/main/java/com/rqtracker/service/BackupService.java
src/main/java/com/rqtracker/service/DataStore.java
src/main/java/com/rqtracker/service/DiskScanService.java
src/main/java/com/rqtracker/service/FileListGenerator.java
src/main/java/com/rqtracker/service/FolderCreatorService.java
src/main/java/com/rqtracker/service/ImportExportService.java
src/main/java/com/rqtracker/service/ProgressCalc.java
src/main/java/com/rqtracker/service/TaskCascadeService.java
src/main/java/com/rqtracker/service/TaskFactory.java
src/main/java/com/rqtracker/service/UpdateService.java
```

### ui/component
```
src/main/java/com/rqtracker/ui/component/CheckItemRow.java
src/main/java/com/rqtracker/ui/component/CollapsibleCard.java
src/main/java/com/rqtracker/ui/component/ConfirmDialog.java
src/main/java/com/rqtracker/ui/component/RQContentPanel.java
src/main/java/com/rqtracker/ui/component/RQListItem.java
src/main/java/com/rqtracker/ui/component/RQTabBar.java
src/main/java/com/rqtracker/ui/component/SectionTimestamp.java
src/main/java/com/rqtracker/ui/component/SidePanel.java
src/main/java/com/rqtracker/ui/component/ToastNotification.java
```

### ui/controller
```
src/main/java/com/rqtracker/ui/controller/MainController.java
```

### ui/dialog
```
src/main/java/com/rqtracker/ui/dialog/FolderListDialog.java
src/main/java/com/rqtracker/ui/dialog/HistoryDialog.java
src/main/java/com/rqtracker/ui/dialog/NewEditRQDialog.java
src/main/java/com/rqtracker/ui/dialog/PlaceholderDialog.java
src/main/java/com/rqtracker/ui/dialog/SettingsDialog.java
src/main/java/com/rqtracker/ui/dialog/UpdateDialog.java
src/main/java/com/rqtracker/ui/dialog/VerFilesDialog.java
src/main/java/com/rqtracker/ui/dialog/package-info.java
```

### util
```
src/main/java/com/rqtracker/util/ClipboardUtils.java
src/main/java/com/rqtracker/util/DateTimeUtils.java
src/main/java/com/rqtracker/util/DialogHelper.java
src/main/java/com/rqtracker/util/PathUtils.java
src/main/java/com/rqtracker/util/ThemeManager.java              (未追蹤)
```

## 資源 (src/main/resources)

```
src/main/resources/css/rq-theme.css
```

## 測試 (src/test/java)

```
src/test/java/com/rqtracker/DiskScanServiceTest.java
src/test/java/com/rqtracker/PathUtilsTest.java
src/test/java/com/rqtracker/ProgressCalcTest.java
src/test/java/com/rqtracker/TaskFactoryTest.java
```

---

**統計**：追蹤 56 檔 ＋ 未追蹤 2 檔 ＝ 共 58 檔
