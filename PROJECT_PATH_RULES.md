# RQ Tracker 路徑生成規則（全部）

> 來源：`TaskFactory.java`（6 個任務生成器）＋ `FileListGenerator.java` ＋ `FolderCreatorService.java`
> 產生日期：2026-06-05
> 例：`C:\SVN\新系統開發\10_增修維護階段\變更需求單\33333_33333\005_廠商弱掃報告\`

---

## 變數定義

| 變數 | 來源 | 範例值 |
|------|------|--------|
| `{downloadsRoot}` | AppConfig.downloadsRoot | `D:\Systex` |
| `{svnRoot}` | AppConfig.svnRoot | `C:\SVN\新系統開發` |
| `{rqId}` | RQData.id（經 winSafeName 過濾非法字元） | `RQ100051742_新增推薦員工欄位` |
| `{rqNum}` | PathUtils.rqNumber(id)，取 RQ\d+ | `RQ100051742` |
| `{proj}` | RQData.projectNum，空則 `{專案}` | `POSMS` |
| `{vid}` | PathUtils.versionId(版本名)，取最後一詞 | `pstID` |
| `{sbomFolder}` | AppConfig.lookupSbomFolder(vid)，無則 `{系統_vid}` | `1_網路郵局中文版` |
| `{td}` | PathUtils.todayDate()，今日 YYYYMMDD | `20260605` |

下方「範例」皆以上述範例值代入。

---

## 共同根路徑

```
ROOT(交付)   = {downloadsRoot}\{rqId}\
            = D:\Systex\RQ100051742_新增推薦員工欄位\

SVN(變更單)  = {svnRoot}\10_增修維護階段\變更需求單\{proj}_{rqId}\
            = C:\SVN\新系統開發\10_增修維護階段\變更需求單\POSMS_RQ100051742_新增推薦員工欄位\

ZAP_BASE    = {svnRoot}\9_文件\99_共用文件\19.弱掃及滲透測試(ZAP)\
SBOM_BASE   = {svnRoot}\9_文件\99_共用文件\18.軟體物料清單(SBOM)\2_系統盤點\
```

---

## 1. 共用流程 sharedFlowTasks（sf_01 / sf_02）

無路徑（純流程步驟：閱讀需求單、理解並規劃程式）。

## 2. 版本開發 versionDevTasks（v{n}_dev_03 ~ 09b）

無路徑（純開發步驟：撰寫程式、本機測試、Fortify、製版、上 SIT、SIT 測試、測試報告、ZAP、匯出弱點報告、滲透測試報告）。
> `bugFix=true` 時跳過「測試報告」(dev_07b)。

---

## 3. 版本交付物 versionDeliverables（每版本，folder 在 ROOT 下）

| key | 資料夾 folder | 檔名 filename |
|-----|--------------|--------------|
| `v{n}_del_code` | `ROOT\{td}_{rqNum}\{vid}\` | （checkHasFiles，比對改動程式清單） |
| `v{n}_del_fortify` | `ROOT\安全性文件\{vid}\` | `{rqNum}_Fortify_{vid}_OWASPAPITop10_{td}.docx` |
| `v{n}_del_testrpt` ⚠跳bugFix | `ROOT\測試報告\{vid}\` | `中華郵政_網路郵局系統_測試報告_{rqNum}_{vid}.xlsx` |
| `v{n}_del_vulnscan` ⚠當天 | `ROOT\安全性文件\{vid}\` | `廠商系統弱點掃描_{vid}_{td}.pdf` |
| `v{n}_del_zapexcl` ⚠當天 | `ROOT\安全性文件\{vid}\` | `ZAP弱點掃描排除說明_{vid}.xlsx` |
| `v{n}_del_pentest` ⚠當天 | `ROOT\安全性文件\{vid}\` | `滲透測試_{vid}_{td}.docx` |
| `v{n}_del_pentestcsv` ⚠當天 | `ROOT\安全性文件\{vid}\` | `滲透測試過程報告_{vid}_{td}.csv` |
| `v{n}_del_sbom` ⚠當天 | `ROOT\SBOM\{vid}\` | `file.json  +  manifest.spdx.json` |

**範例（vid=pstID, td=20260605）**

```
del_code      D:\Systex\RQ100051742_新增推薦員工欄位\20260605_RQ100051742\pstID\
del_fortify   D:\Systex\RQ100051742_新增推薦員工欄位\安全性文件\pstID\
              RQ100051742_Fortify_pstID_OWASPAPITop10_20260605.docx
del_testrpt   D:\Systex\RQ100051742_新增推薦員工欄位\測試報告\pstID\
              中華郵政_網路郵局系統_測試報告_RQ100051742_pstID.xlsx
del_vulnscan  D:\Systex\RQ100051742_新增推薦員工欄位\安全性文件\pstID\
              廠商系統弱點掃描_pstID_20260605.pdf
del_zapexcl   D:\Systex\RQ100051742_新增推薦員工欄位\安全性文件\pstID\
              ZAP弱點掃描排除說明_pstID.xlsx
del_pentest   D:\Systex\RQ100051742_新增推薦員工欄位\安全性文件\pstID\
              滲透測試_pstID_20260605.docx
del_pentestcsv D:\Systex\RQ100051742_新增推薦員工欄位\安全性文件\pstID\
              滲透測試過程報告_pstID_20260605.csv
del_sbom      D:\Systex\RQ100051742_新增推薦員工欄位\SBOM\pstID\
              file.json  +  manifest.spdx.json
```

---

## 4. 版本 SVN versionSVNTasks（每版本）

| key | 資料夾 folder | 檔名 filename |
|-----|--------------|--------------|
| `v{n}_svn_005` | `SVN\005_廠商弱掃報告\` | `{rqNum}_Fortify_{vid}_OWASPAPITop10_{td}.docx` |
| `v{n}_svn_004` ⚠跳bugFix | `SVN\004_測試報告\` | `中華郵政_網路郵局系統_測試報告_{rqNum}_{vid}.xlsx` |
| `v{n}_svn_zap1` ⚠當天 | `ZAP_BASE\1_中華郵政\{sbomFolder}\{td}\` | `中華郵政_廠商系統弱點掃描_{vid}_{td}.pdf` |
| `v{n}_svn_zap1_excl` ⚠當天 | `ZAP_BASE\1_中華郵政\{sbomFolder}\{td}\` | `ZAP弱點掃描排除說明_{vid}.xlsx` |
| `v{n}_svn_zap1_pentest` ⚠當天 | `ZAP_BASE\1_中華郵政\{sbomFolder}\{td}\` | `滲透測試_{vid}_{td}.docx` |
| `v{n}_svn_zap1_pentestcsv` ⚠當天 | `ZAP_BASE\1_中華郵政\{sbomFolder}\{td}\` | `滲透測試過程報告_{vid}_{td}.csv` |
| `v{n}_svn_zap2` ⚠當天 | `ZAP_BASE\2_內部系統盤點\{sbomFolder}\{td}\` | `內部盤點_廠商系統弱點掃描_{vid}.pdf` |
| `v{n}_svn_sbom` ⚠當天 | `SBOM_BASE\{sbomFolder}\{td}\_manifest\spdx_2.2\` | `file.json  +  manifest.spdx.json  +  manifest.spdx.json.sha256` |

**範例（proj=POSMS, vid=pstID, sbomFolder=1_網路郵局中文版, td=20260605）**

```
svn_005   C:\SVN\新系統開發\10_增修維護階段\變更需求單\POSMS_RQ100051742_新增推薦員工欄位\005_廠商弱掃報告\
          RQ100051742_Fortify_pstID_OWASPAPITop10_20260605.docx
svn_004   C:\SVN\新系統開發\10_增修維護階段\變更需求單\POSMS_RQ100051742_新增推薦員工欄位\004_測試報告\
          中華郵政_網路郵局系統_測試報告_RQ100051742_pstID.xlsx
svn_zap1  C:\SVN\新系統開發\9_文件\99_共用文件\19.弱掃及滲透測試(ZAP)\1_中華郵政\1_網路郵局中文版\20260605\
          中華郵政_廠商系統弱點掃描_pstID_20260605.pdf
svn_zap1_excl      …\1_中華郵政\1_網路郵局中文版\20260605\  ZAP弱點掃描排除說明_pstID.xlsx
svn_zap1_pentest   …\1_中華郵政\1_網路郵局中文版\20260605\  滲透測試_pstID_20260605.docx
svn_zap1_pentestcsv …\1_中華郵政\1_網路郵局中文版\20260605\ 滲透測試過程報告_pstID_20260605.csv
svn_zap2  C:\SVN\新系統開發\9_文件\99_共用文件\19.弱掃及滲透測試(ZAP)\2_內部系統盤點\1_網路郵局中文版\20260605\
          內部盤點_廠商系統弱點掃描_pstID.pdf
svn_sbom  C:\SVN\新系統開發\9_文件\99_共用文件\18.軟體物料清單(SBOM)\2_系統盤點\1_網路郵局中文版\20260605\_manifest\spdx_2.2\
          file.json  +  manifest.spdx.json  +  manifest.spdx.json.sha256
```

---

## 5. 最終交付 finalDeliveryTasks（folder 在 ROOT 下）

| key | 資料夾 folder | 檔名 filename |
|-----|--------------|--------------|
| `fin_sql` 選填 | `ROOT\SQL\` | `01_xxx_create.sql  /  02_xxx_update.sql` |
| `fin_doc` ⚠當天編輯 | `ROOT\` | `中華郵政_網路郵局系統_廠商交付程式說明({rqNum}).docx` |
| `fin_list` 選填 | `ROOT\` | `list.txt` |
| `fin_zip` | `ROOT\` | `{td}_{rqNum}.zip` |

**範例**

```
fin_sql   D:\Systex\RQ100051742_新增推薦員工欄位\SQL\  01_xxx_create.sql / 02_xxx_update.sql
fin_doc   D:\Systex\RQ100051742_新增推薦員工欄位\      中華郵政_網路郵局系統_廠商交付程式說明(RQ100051742).docx
fin_list  D:\Systex\RQ100051742_新增推薦員工欄位\      list.txt
fin_zip   D:\Systex\RQ100051742_新增推薦員工欄位\      20260605_RQ100051742.zip
```

---

## 6. 共用 SVN sharedSVNTasks（folder 在 SVN\003_維護服務紀錄單 下）

```
SVN3 = SVN\003_維護服務紀錄單\
     = C:\SVN\新系統開發\10_增修維護階段\變更需求單\POSMS_RQ100051742_新增推薦員工欄位\003_維護服務紀錄單\
```

| key | 資料夾 folder | 檔名 filename |
|-----|--------------|--------------|
| `svn_003_doc` checkModTime | `SVN3` | `中華郵政_網路郵局系統_廠商交付程式說明({rqNum}).docx` |
| `svn_003_updrec` ⚠跳bugFix | `SVN3` | `{proj}_{rqNum}_開放系統程式更新紀錄單_Vx.x.docx` |
| `svn_003_testrec` ⚠跳bugFix | `SVN3` | `{proj}_{rqNum}_開放系統程式測試報告單_Vx.x.docx` |
| `svn_003_sbom` checkModTime | `SVN3` | `軟體物料清單_{rqNum}.docx` |
| `svn_003_security` checkModTime | `SVN3` | `安全測試報告_{rqNum}.docx` |

**範例**

```
svn_003_doc     …\003_維護服務紀錄單\  中華郵政_網路郵局系統_廠商交付程式說明(RQ100051742).docx
svn_003_updrec  …\003_維護服務紀錄單\  POSMS_RQ100051742_開放系統程式更新紀錄單_Vx.x.docx
svn_003_testrec …\003_維護服務紀錄單\  POSMS_RQ100051742_開放系統程式測試報告單_Vx.x.docx
svn_003_sbom    …\003_維護服務紀錄單\  軟體物料清單_RQ100051742.docx
svn_003_security …\003_維護服務紀錄單\ 安全測試報告_RQ100051742.docx
```

---

## 7. 程式碼清單 FileListGenerator（list.txt）

```
寫入路徑：D:\Systex\{rqId}\list.txt
```

內容組成：
1. 各版本改動程式段落：`[版本名]` + 每行 `{vid}\相對路徑`
2. 掃描 `D:\Systex\{rqId}\` 下其他子目錄，依序 `安全性文件` → `SBOM` → `測試報告` → 其餘字母序，列出各目錄內所有檔案。

---

## 旗標說明

| 標記 | 意義 |
|------|------|
| ⚠當天 | 檔名含 `{td}`，須當天產生（warn 標籤） |
| ⚠當天編輯 | 檔案須當天編輯（checkModTime） |
| ⚠跳bugFix | `RQData.bugFix=true` 時此任務不生成 |
| 選填 | optional，不計入進度 |
| checkHasFiles | 比對 versionFiles 改動程式清單，非單一檔名 |
| checkModTime | 掃描時檢查檔案修改時間 > RQ 建立時間 |

## 通配規則（DiskScanService 掃描比對）

- 檔名中連續 8 位數字 → 比對 `\d{8}`（任意日期）
- 檔名中 `Vx.x` → 比對 `V\d+\.\d+`（任意版號）
- 多檔以 ` + ` 分隔，全部存在才算完整（FILE）
