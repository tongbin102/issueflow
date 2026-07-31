package com.issueflow.dto.resp;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis 监控信息视图对象
 * <p>Redis 不可用时 HTTP 仍为 200，{@code available=false} + {@code errorMessage}，
 * 由前端渲染错误态，避免监控页把 500 抛给用户。</p>
 */
@Data
public class RedisInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Redis 是否可用 */
    private Boolean available = Boolean.FALSE;

    /** 不可用时的错误摘要 */
    private String errorMessage;

    /** 服务器信息：version / mode / os / uptimeDays / role */
    private Map<String, String> server = new LinkedHashMap<>();

    /** 内存信息：usedMemory / usedMemoryHuman / usedMemoryPeakHuman / maxMemory / fragmentationRatio */
    private Map<String, String> memory = new LinkedHashMap<>();

    /** 统计信息：connectedClients / totalCommands / opsPerSec / hits / misses / hitRate */
    private Map<String, String> stats = new LinkedHashMap<>();

    /** 键空间分布 */
    private List<KeyspaceItem> keyspace = new ArrayList<>();

    /** 当前库 key 总数 */
    private Long dbSize = 0L;

    /**
     * 单个 db 的键空间统计
     */
    @Data
    public static class KeyspaceItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 库名，如 db0 */
        private String db;

        /** key 数量 */
        private Long keys = 0L;

        /** 设置了过期时间的 key 数量 */
        private Long expires = 0L;

        /** 平均 TTL */
        private Long avgTtl = 0L;
    }
}
