package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 登录日志（Phase 7 新增）
 * <p>成功与失败均记录；失败且用户名不存在时 {@code userId} 为 null。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("login_log")
public class LoginLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 登录用户 id，失败且用户不存在时为 null */
    private Long userId;

    /** 冗余用户名，便于失败场景追溯 */
    private String username;

    /** 客户端 IP */
    private String ip;

    /** 原始 User-Agent（截断至 512） */
    private String userAgent;

    /** 解析出的浏览器 */
    private String browser;

    /** 解析出的操作系统 */
    private String os;

    /** 1 成功 / 0 失败 */
    private Integer success;

    /** 失败原因：密码错误 / 账号已禁用 / 用户不存在 */
    private String failReason;

    /** 登录时间 */
    private LocalDateTime loginAt;
}
