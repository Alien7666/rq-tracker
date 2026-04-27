package com.rqtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rqtracker.model.AppData;
import com.rqtracker.model.RQData;
import com.rqtracker.util.DateTimeUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * 主資料儲存（對應 HTML 的 localStorage rqtracker_* 系列操作）。
 *
 * 儲存位置：%APPDATA%\RQTracker\data.json
 * 格式與 HTML 版 getAllData() 匯出格式完全相容。
 *
 * 設計原則：
 * - 讀取在應用程式啟動時一次性載入至記憶體
 * - 每次寫入操作後透過 CompletableFuture 非同步寫入磁碟，避免 UI 卡頓
 * - 提供與 HTML 版相同語意的操作方法
 */
public class DataStore {

    private static final Logger LOG = Logger.getLogger(DataStore.class.getName());
    private static final String DATA_FILENAME = "data.json";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Path dataPath;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DataStore-IO");
        t.setDaemon(true);
        return t;
    });

    // ── 記憶體快取 ───────────────────────────────────────────────────────────

    private List<String> index = new ArrayList<>();
    private final Map<String, RQData> rqs = new LinkedHashMap<>();
    private List<RQData> history = new ArrayList<>();
    private volatile CompletableFuture<Void> pendingSave = CompletableFuture.completedFuture(null);

    // ── 初始化 ────────────────────────────────────────────────────────────────

    public DataStore(Path appDataDir) {
        this.dataPath = appDataDir.resolve(DATA_FILENAME);
        load();
    }

    private void load() {
        if (!Files.exists(dataPath)) {
            LOG.info("data.json 不存在，從空資料開始");
            return;
        }
        try {
            AppData data = MAPPER.readValue(dataPath.toFile(), AppData.class);
            if (data.getIndex() != null) index = new ArrayList<>(data.getIndex());
            if (data.getRqs() != null) {
                rqs.clear();
                // 依 index 順序填入
                for (String id : index) {
                    RQData rq = data.getRqs().get(id);
                    if (rq != null) rqs.put(id, rq);
                }
                // 補上 index 沒有的（容錯）
                data.getRqs().forEach((id, rq) -> rqs.putIfAbsent(id, rq));
            }
            if (data.getHistory() != null) history = new ArrayList<>(data.getHistory());
            LOG.info("已載入 " + index.size() + " 個 RQ，" + history.size() + " 筆歷史紀錄");
        } catch (IOException e) {
            LOG.warning("讀取 data.json 失敗：" + e.getMessage());
        }
    }

    // ── 非同步儲存 ────────────────────────────────────────────────────────────

    public synchronized CompletableFuture<Void> saveAsync() {
        AppData snapshot = buildSnapshot();
        pendingSave = CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(dataPath.getParent());
                MAPPER.writeValue(dataPath.toFile(), snapshot);
            } catch (IOException e) {
                LOG.warning("儲存 data.json 失敗：" + e.getMessage());
            }
        }, ioExecutor);
        return pendingSave;
    }

    private AppData buildSnapshot() {
        AppData data = new AppData();
        data.setVersion(1);
        data.setSavedAt(DateTimeUtils.nowIso());
        data.setIndex(new ArrayList<>(index));
        Map<String, RQData> rqsCopy = new LinkedHashMap<>();
        index.forEach(id -> { RQData rq = rqs.get(id); if (rq != null) rqsCopy.put(id, rq); });
        data.setRqs(rqsCopy);
        data.setHistory(new ArrayList<>(history));
        return data;
    }

    // ── Index 操作（對應 HTML 的 loadIndex / saveIndex）───────────────────────

    public List<String> getIndex() { return Collections.unmodifiableList(index); }

    // ── RQ 操作（對應 HTML 的 loadRQ / saveRQ / deleteRQ）────────────────────

    public RQData getRQ(String id) {
        return id != null ? rqs.get(id) : null;
    }

    /**
     * 新增或更新 RQ（對應 HTML 的 saveRQ(id, data)）。
     * 若 ID 不在 index 中則加入末尾。
     */
    public void saveRQ(String id, RQData data) {
        rqs.put(id, data);
        if (!index.contains(id)) index.add(id);
        saveAsync();
    }

    /**
     * 刪除 RQ（對應 HTML 的 deleteRQ(id)）。
     */
    public void deleteRQ(String id) {
        rqs.remove(id);
        index.remove(id);
        saveAsync();
    }

    /**
     * 重命名 RQ（更新 ID，保留資料與 index 位置）。
     */
    public void renameRQ(String oldId, String newId, RQData data) {
        int pos = index.indexOf(oldId);
        rqs.remove(oldId);
        rqs.put(newId, data);
        if (pos >= 0) {
            index.set(pos, newId);
        } else {
            index.add(newId);
        }
        saveAsync();
    }

    // ── History 操作（對應 HTML 的 loadHistory / saveHistory）───────────────

    public List<RQData> getHistory() { return Collections.unmodifiableList(history); }

    /**
     * 歸檔 RQ（將 RQ 加入 history 最前，從 index/rqs 移除）。
     */
    public void archiveRQ(String id) {
        RQData rq = rqs.get(id);
        if (rq == null) return;
        rq.setArchivedAt(DateTimeUtils.nowIso());
        history.add(0, rq);
        deleteRQ(id); // 內含 saveAsync
    }

    /**
     * 從歷史紀錄復原 RQ。
     */
    public void restoreFromHistory(int historyIndex) {
        if (historyIndex < 0 || historyIndex >= history.size()) return;
        RQData rq = history.remove(historyIndex);
        rq.setArchivedAt(null);
        saveRQ(rq.getId(), rq); // 內含 saveAsync
    }

    /**
     * 永久刪除歷史紀錄項目。
     */
    public void deleteHistory(int historyIndex) {
        if (historyIndex < 0 || historyIndex >= history.size()) return;
        history.remove(historyIndex);
        saveAsync();
    }

    /**
     * 更新歷史紀錄中的 RQ（編輯歸檔 RQ）。
     */
    public void updateHistory(int historyIndex, RQData data) {
        if (historyIndex < 0 || historyIndex >= history.size()) return;
        history.set(historyIndex, data);
        saveAsync();
    }

    // ── 匯入/匯出支援 ─────────────────────────────────────────────────────────

    /**
     * 取得完整資料快照（用於匯出 JSON）。
     */
    public AppData exportAll() {
        return buildSnapshot();
    }

    /**
     * 從 AppData 完整覆蓋現有資料（對應 HTML 的 importData()）。
     */
    public void importAll(AppData data) {
        index.clear();
        rqs.clear();
        history.clear();
        if (data.getIndex() != null) index.addAll(data.getIndex());
        if (data.getRqs() != null) {
            for (String id : index) {
                RQData rq = data.getRqs().get(id);
                if (rq != null) rqs.put(id, rq);
            }
        }
        if (data.getHistory() != null) history.addAll(data.getHistory());
        saveAsync();
    }

    /** 關閉 IO 執行緒（應用程式結束時呼叫） */
    public void shutdown() {
        ioExecutor.shutdown();
        try {
            pendingSave.get(5, TimeUnit.SECONDS);
            if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ioExecutor.shutdownNow();
        } catch (ExecutionException | TimeoutException e) {
            LOG.warning("等待 data.json 儲存完成失敗：" + e.getMessage());
            ioExecutor.shutdownNow();
        }
    }
}
