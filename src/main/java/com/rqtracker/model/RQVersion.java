package com.rqtracker.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 代表一個 RQ 版本（對應 HTML 的 {name: String}）。
 * 版本 ID 由 versionId(name) 計算得出（取名稱最後一個空白分隔詞）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RQVersion {

    private String name;

    public RQVersion() {}

    public RQVersion(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @Override
    public String toString() { return name; }
}
