package com.issueflow.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 流程配置（回退 / 重开开关）写入请求
 */
@Data
public class FlowConfigReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否允许验证回退 PENDING_VERIFY -> IN_PROGRESS */
    private Boolean rejectEnabled;

    /** 是否允许已关闭问题重开 CLOSED -> OPEN */
    private Boolean reopenEnabled;
}
