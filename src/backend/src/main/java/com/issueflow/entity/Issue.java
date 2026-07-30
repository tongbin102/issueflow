package com.issueflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.issueflow.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 问题主表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("issue")
public class Issue extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 问题编号 IS-YYYYMMDD-0001 */
    private String issueNo;

    /** 标题 */
    private String title;

    /** 详细描述 */
    private String description;

    /** 严重等级：0致命 1严重 2一般 3轻微 */
    private Integer severity;

    /** 标签（逗号分隔名称） */
    private String tags;

    /** 复现步骤 */
    private String reproduceSteps;

    /** 操作系统 */
    private String envOs;

    /** 浏览器 */
    private String envBrowser;

    /** 应用版本 */
    private String envAppVersion;

    /** 设备型号 */
    private String envDevice;

    /** 状态：0待处理 1处理中 2待验证 3验证通过 4已关闭 */
    private Integer status;

    /** 提交者 id */
    private Long reporterId;

    /** 处理人/认领人 id */
    private Long assigneeId;

    /** 关联项目 id（关联 project.id，逻辑删除下不加外键） */
    private Long projectId;

    /** 关闭时间（解决周期计算） */
    private LocalDateTime closedAt;
}
