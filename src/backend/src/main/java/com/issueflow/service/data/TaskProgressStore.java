package com.issueflow.service.data;

import com.issueflow.common.Constants;
import com.issueflow.dto.data.TaskProgressDTO;
import com.issueflow.enums.TaskPhaseEnum;
import com.issueflow.enums.TaskStatusEnum;
import com.issueflow.util.MaskUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 备份 / 恢复任务进度存储（Phase10）。
 *
 * <p>进度只写 Redis（{@code dm:task:{taskId}}，TTL 2h），不写库 —— 高频更新落库无意义；
 * 终态时才由 Service 同步刷一次 {@code backup_record} / {@code restore_record}。</p>
 *
 * <p><b>脱敏</b>：所有对外文案统一经 {@link #sanitize(String)} 处理，
 * 剥掉可能夹带的绝对路径与密码片段，这是安全红线的最后一道闸。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskProgressStore {

    private final RedisTemplate<String, Object> redisTemplate;

    /** 任务种类：备份 */
    public static final String TYPE_BACKUP = "BACKUP";
    /** 任务种类：恢复 */
    public static final String TYPE_RESTORE = "RESTORE";

    /**
     * 初始化一个任务进度。
     *
     * @param taskId   任务号，不可为空
     * @param taskType {@link #TYPE_BACKUP} 或 {@link #TYPE_RESTORE}
     * @param recordId 关联业务记录 id，可为 null
     * @return 初始化后的进度对象
     */
    public TaskProgressDTO init(String taskId, String taskType, Long recordId) {
        TaskProgressDTO dto = new TaskProgressDTO();
        dto.setTaskId(taskId);
        dto.setTaskType(taskType);
        dto.setStatus(TaskStatusEnum.PENDING.getCode());
        dto.setPhase(TaskPhaseEnum.INIT.getCode());
        dto.setPhaseDesc(TaskPhaseEnum.INIT.getDesc());
        dto.setProgress(TaskPhaseEnum.INIT.getWeight());
        dto.setMessage("");
        dto.setErrorMsg("");
        dto.setRecordId(recordId);
        dto.setFileName("");
        long now = System.currentTimeMillis();
        dto.setStartedAt(now);
        dto.setUpdatedAt(now);
        dto.setFinished(Boolean.FALSE);
        save(dto);
        return dto;
    }

    /**
     * 推进到指定阶段（进度百分比取该阶段的锚点权重）。
     *
     * @param taskId  任务号
     * @param phase   目标阶段，不可为空
     * @param message 附加提示，可为 null
     */
    public void advance(String taskId, TaskPhaseEnum phase, String message) {
        TaskProgressDTO dto = get(taskId);
        if (dto == null) {
            log.warn("[TaskProgressStore] 进度不存在，忽略推进: taskId={}", taskId);
            return;
        }
        if (Boolean.TRUE.equals(dto.getFinished())) {
            // 终态不可回退
            return;
        }
        dto.setStatus(TaskStatusEnum.RUNNING.getCode());
        dto.setPhase(phase.getCode());
        dto.setPhaseDesc(phase.getDesc());
        dto.setProgress(phase.getWeight());
        dto.setMessage(sanitize(message));
        dto.setUpdatedAt(System.currentTimeMillis());
        save(dto);
    }

    /**
     * 仅更新百分比（同一阶段内的细粒度进度，例如 dump 到第 N 张表）。
     *
     * @param taskId   任务号
     * @param progress 目标百分比，会被夹取到 [0,100]
     * @param message  附加提示，可为 null
     */
    public void updateProgress(String taskId, int progress, String message) {
        TaskProgressDTO dto = get(taskId);
        if (dto == null || Boolean.TRUE.equals(dto.getFinished())) {
            return;
        }
        int safe = Math.max(0, Math.min(100, progress));
        // 进度只增不减，避免并发写导致条形图回跳
        if (dto.getProgress() != null && safe < dto.getProgress()) {
            safe = dto.getProgress();
        }
        dto.setProgress(safe);
        dto.setMessage(sanitize(message));
        dto.setUpdatedAt(System.currentTimeMillis());
        save(dto);
    }

    /**
     * 标记任务成功（终态）。
     *
     * @param taskId   任务号
     * @param recordId 关联业务记录 id，可为 null 表示不更新
     * @param fileName 备份文件名，可为 null
     */
    public void success(String taskId, Long recordId, String fileName) {
        TaskProgressDTO dto = get(taskId);
        if (dto == null) {
            return;
        }
        dto.setStatus(TaskStatusEnum.SUCCESS.getCode());
        dto.setPhase(TaskPhaseEnum.DONE.getCode());
        dto.setPhaseDesc(TaskPhaseEnum.DONE.getDesc());
        dto.setProgress(100);
        dto.setErrorMsg("");
        if (recordId != null) {
            dto.setRecordId(recordId);
        }
        if (fileName != null) {
            dto.setFileName(fileName);
        }
        dto.setUpdatedAt(System.currentTimeMillis());
        dto.setFinished(Boolean.TRUE);
        save(dto);
    }

    /**
     * 标记任务失败（终态）。
     *
     * @param taskId   任务号
     * @param errorMsg 失败原因（调用方须已脱敏，此处再兜一层）
     */
    public void fail(String taskId, String errorMsg) {
        TaskProgressDTO dto = get(taskId);
        if (dto == null) {
            return;
        }
        dto.setStatus(TaskStatusEnum.FAILED.getCode());
        dto.setErrorMsg(sanitize(errorMsg));
        dto.setUpdatedAt(System.currentTimeMillis());
        dto.setFinished(Boolean.TRUE);
        save(dto);
    }

    /**
     * 标记任务取消（终态）。
     *
     * @param taskId 任务号
     * @param reason 取消原因
     */
    public void cancel(String taskId, String reason) {
        TaskProgressDTO dto = get(taskId);
        if (dto == null) {
            return;
        }
        dto.setStatus(TaskStatusEnum.CANCELED.getCode());
        dto.setErrorMsg(sanitize(reason));
        dto.setUpdatedAt(System.currentTimeMillis());
        dto.setFinished(Boolean.TRUE);
        save(dto);
    }

    /**
     * 读取任务进度。
     *
     * @param taskId 任务号
     * @return 进度对象；不存在或已过期返回 null
     */
    public TaskProgressDTO get(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return null;
        }
        Object raw = redisTemplate.opsForValue().get(key(taskId));
        if (raw == null) {
            return null;
        }
        if (raw instanceof TaskProgressDTO) {
            return (TaskProgressDTO) raw;
        }
        // Jackson 反序列化为 LinkedHashMap 的兜底路径
        return convert(raw);
    }

    /**
     * 删除任务进度（用于测试或手动清理）。
     *
     * @param taskId 任务号
     */
    public void remove(String taskId) {
        if (taskId != null && !taskId.trim().isEmpty()) {
            redisTemplate.delete(key(taskId));
        }
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    /**
     * 写回 Redis 并续期 TTL。
     *
     * @param dto 进度对象
     */
    private void save(TaskProgressDTO dto) {
        try {
            redisTemplate.opsForValue().set(
                    key(dto.getTaskId()), dto, Constants.DM_TASK_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 进度写失败不能影响主流程（备份本身可能已成功）
            log.warn("[TaskProgressStore] 进度写入 Redis 失败: taskId={}, err={}",
                    dto.getTaskId(), e.getClass().getSimpleName());
        }
    }

    /**
     * 拼接 Redis key。
     *
     * @param taskId 任务号
     * @return 完整 key
     */
    private String key(String taskId) {
        return Constants.REDIS_DM_TASK_PREFIX + taskId;
    }

    /**
     * 把 Redis 反序列化出的 Map 转成 DTO（Jackson 未带类型信息时的兜底）。
     *
     * @param raw Redis 原始值
     * @return DTO；转换失败返回 null
     */
    @SuppressWarnings("unchecked")
    private TaskProgressDTO convert(Object raw) {
        if (!(raw instanceof java.util.Map)) {
            return null;
        }
        java.util.Map<String, Object> map = (java.util.Map<String, Object>) raw;
        TaskProgressDTO dto = new TaskProgressDTO();
        dto.setTaskId(str(map.get("taskId")));
        dto.setTaskType(str(map.get("taskType")));
        dto.setStatus(str(map.get("status")));
        dto.setPhase(str(map.get("phase")));
        dto.setPhaseDesc(str(map.get("phaseDesc")));
        dto.setProgress(intOf(map.get("progress")));
        dto.setMessage(str(map.get("message")));
        dto.setErrorMsg(str(map.get("errorMsg")));
        Object recordId = map.get("recordId");
        dto.setRecordId(recordId == null ? null : Long.valueOf(String.valueOf(recordId)));
        dto.setFileName(str(map.get("fileName")));
        dto.setStartedAt(longOf(map.get("startedAt")));
        dto.setUpdatedAt(longOf(map.get("updatedAt")));
        dto.setFinished(Boolean.TRUE.equals(map.get("finished"))
                || "true".equalsIgnoreCase(String.valueOf(map.get("finished"))));
        return dto;
    }

    /**
     * 空安全的字符串转换。
     *
     * @param v 原始值
     * @return 字符串，null 转空串
     */
    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /**
     * 空安全的 int 转换。
     *
     * @param v 原始值
     * @return 数值，异常或 null 返回 0
     */
    private Integer intOf(Object v) {
        if (v == null) {
            return 0;
        }
        try {
            return Integer.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 空安全的 long 转换。
     *
     * @param v 原始值
     * @return 数值，异常或 null 返回 0
     */
    private Long longOf(Object v) {
        if (v == null) {
            return 0L;
        }
        try {
            return Long.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 文案脱敏：剥掉绝对路径与疑似密码片段。
     *
     * @param text 原始文案，可为 null
     * @return 脱敏后的文案，null 转空串
     */
    private String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return MaskUtils.maskSensitivePath(text);
    }
}
