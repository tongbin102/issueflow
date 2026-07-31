package com.issueflow.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录日志视图对象
 */
@Data
public class LoginLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 登录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime time;

    /** 来源 IP */
    private String ip;

    /** 浏览器 */
    private String browser;

    /** 操作系统 */
    private String os;

    /** 是否成功 */
    private Boolean success;

    /** 失败原因 */
    private String failReason;
}
