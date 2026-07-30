package com.issueflow.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 数据初始化请求（R7）：必须输入确认文本 RESET
 */
@Data
public class DataResetReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 确认文本，必须为 RESET */
    @NotBlank(message = "确认文本不能为空")
    private String confirmText;
}
