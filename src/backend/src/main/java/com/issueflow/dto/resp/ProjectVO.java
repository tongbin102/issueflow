package com.issueflow.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目视图对象
 */
@Data
public class ProjectVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String description;

    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /** 负责人 id */
    private Long leaderId;

    /** 负责人姓名（由 userService 回查，缺省 null） */
    private String leaderName;

    /** 项目成员 id 原始逗号串 */
    private String memberIds;

    /** 项目成员明细（由 memberIds 切分后批量查 user 封装，按原顺序，丢弃无效 id） */
    private List<UserBriefVO> members;
}
