package com.issueflow.dto.resp;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 备份预估视图对象
 */
@Data
public class BackupEstimateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 范围 ALL / CORE */
    private String scope;

    /** 表数量 */
    private Integer tableCount = 0;

    /** 总行数 */
    private Long totalRows = 0L;

    /** 逐表行数 */
    private List<TableRows> tables = new ArrayList<>();

    /** 建议文件名 */
    private String suggestedFileName;

    /** 超限告警（数据量过大时提示导出耗时） */
    private String warning;

    /** 是否包含附件二进制（恒为 false） */
    private Boolean attachmentBinaryIncluded = Boolean.FALSE;

    /** 排除的表 */
    private List<String> excludedTables = new ArrayList<>();

    /**
     * 单表行数
     */
    @Data
    public static class TableRows implements Serializable {

        private static final long serialVersionUID = 1L;

        private String name;

        private Long rows = 0L;

        public TableRows() {
        }

        public TableRows(String name, Long rows) {
            this.name = name;
            this.rows = rows;
        }
    }
}
