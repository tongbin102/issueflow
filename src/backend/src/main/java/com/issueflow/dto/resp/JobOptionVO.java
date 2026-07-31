package com.issueflow.dto.resp;

import lombok.Data;

import java.io.Serializable;

/**
 * 定时任务可选执行目标（来自后端 jobRegistry 白名单）
 */
@Data
public class JobOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 执行目标 key */
    private String jobKey;

    /** 展示名 */
    private String displayName;

    public JobOptionVO() {
    }

    public JobOptionVO(String jobKey, String displayName) {
        this.jobKey = jobKey;
        this.displayName = displayName;
    }
}
