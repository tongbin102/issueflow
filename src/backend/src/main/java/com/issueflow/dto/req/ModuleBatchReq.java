package com.issueflow.dto.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 模块批量操作请求（batch-delete / batch-move 共用）。
 *
 * <p>batch-delete 不读 {@code targetParentId}；batch-move 必填（空或 0 表示移到根级）。</p>
 */
@Data
public class ModuleBatchReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属项目 id（用于范围校验） */
    @NotNull(message = "projectId 不能为空")
    private Long projectId;

    /** 被操作的模块 id 列表 */
    @NotEmpty(message = "请至少选择一个模块")
    private List<Long> ids;

    /** 目标父模块 id（仅 batch-move 使用），空或 0 表示根级 */
    private Long targetParentId;
}
