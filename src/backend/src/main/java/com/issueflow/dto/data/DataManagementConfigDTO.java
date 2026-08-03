package com.issueflow.dto.data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 备份保留策略配置（Phase10 数据管理）。
 *
 * <p>读写 {@code GET|PUT /api/admin/data-management/config}，
 * 落库到 {@code sys_config} 表的 {@code data.management.*} 键：</p>
 * <ul>
 *   <li>{@code maxCopies}   → {@code data.management.backup.retain.count}</li>
 *   <li>{@code defaultDays} → {@code data.management.backup.retain.days}</li>
 *   <li>{@code sizeLimitMB} → {@code data.management.upload.max.size.mb}</li>
 * </ul>
 *
 * <p><b>为什么加上下限校验</b>：{@code maxCopies=0} 会让保留策略把刚生成的备份
 * 立刻删掉；{@code sizeLimitMB} 无上限则可被用来打满磁盘。这两项都是
 * 「管理员一次误填 → 数据全丢 / 服务不可用」的高危项，必须在入口卡死。</p>
 */
@Data
public class DataManagementConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 最多保留的备份份数，超出后按时间从旧到新清理 */
    @NotNull(message = "保留份数不能为空")
    @Min(value = 1, message = "保留份数至少为 1")
    @Max(value = 1000, message = "保留份数最多为 1000")
    private Integer maxCopies = 20;

    /** 备份默认保留天数，超期自动清理 */
    @NotNull(message = "保留天数不能为空")
    @Min(value = 1, message = "保留天数至少为 1")
    @Max(value = 3650, message = "保留天数最多为 3650")
    private Integer defaultDays = 30;

    /** 上传备份包的体积上限（MB） */
    @NotNull(message = "上传体积上限不能为空")
    @Min(value = 1, message = "上传体积上限至少为 1 MB")
    @Max(value = 10240, message = "上传体积上限最多为 10240 MB")
    private Integer sizeLimitMB = 512;
}
