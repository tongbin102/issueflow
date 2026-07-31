package com.issueflow.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 字典类型启停切换请求
 */
@Data
public class DictTypeStatusReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标状态：true 启用 / false 停用 */
    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;
}
