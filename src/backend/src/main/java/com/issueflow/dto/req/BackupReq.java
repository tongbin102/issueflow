package com.issueflow.dto.req;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 备份导出请求
 * <p><b>安全约束</b>：前端只能选择范围与格式，<b>不可传表名</b>；
 * 实际表清单来自后端白名单常量。</p>
 */
@Data
public class BackupReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 范围：ALL 全量 / CORE 核心配置 */
    @Pattern(regexp = "^(ALL|CORE)$", message = "备份范围只能是 ALL 或 CORE")
    private String scope = "CORE";

    /** 格式：JSON / SQL */
    @Pattern(regexp = "^(JSON|SQL)$", message = "备份格式只能是 JSON 或 SQL")
    private String format = "JSON";
}
