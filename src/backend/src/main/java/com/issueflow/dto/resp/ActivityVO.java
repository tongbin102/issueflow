package com.issueflow.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 个人活动记录条目（登录日志 + 本人问题操作记录归并后的统一时间线结构）
 */
@Data
public class ActivityVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 类型：LOGIN / ISSUE */
    private String type;

    /** 发生时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime time;

    /** 标题（如「登录成功」「提交修复」） */
    private String title;

    /** 明细描述 */
    private String detail;

    /** 来源 IP（LOGIN 有值） */
    private String ip;

    /** 设备信息「浏览器 / 操作系统」（LOGIN 有值） */
    private String device;

    /** 是否成功（LOGIN 有值） */
    private Boolean success;

    /** 关联问题 id（ISSUE 有值） */
    private Long issueId;

    /** 关联问题编号（ISSUE 有值） */
    private String issueNo;
}
