package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.issueflow.common.Constants;
import com.issueflow.entity.Dict;
import com.issueflow.entity.DictItem;
import com.issueflow.mapper.DictItemMapper;
import com.issueflow.mapper.DictMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 字典项两级缓存：本地 {@link ConcurrentHashMap} + Redis。
 *
 * <p>设计要点：</p>
 * <ol>
 *   <li>key = {@code dict:items:{typeCode}}，Redis TTL 1 小时；本地 Map 不设 TTL，
 *       但任何写操作后由 {@link #evict(String)} 同步清理，单机部署下与 DB 强一致。</li>
 *   <li>Redis 中存的是<b>自序列化的 JSON 字符串</b>（本类持有独立 ObjectMapper 并注册
 *       {@code JavaTimeModule}），不依赖全局 {@code RedisConfig} 的 Object 序列化器 ——
 *       后者未注册 JSR-310 模块，直接写入含 {@code LocalDateTime} 的实体会抛异常。</li>
 *   <li>Redis 任何异常都<b>降级为直读 DB</b>，绝不让缓存故障影响主流程。</li>
 *   <li>缓存内元素已按「启用优先 → sort 升序 → id 升序」排好序，
 *       调用方按需过滤 enabled 即可保持顺序语义（停用项置底）。</li>
 * </ol>
 *
 * <p>多实例部署下需改为 Redis Pub/Sub 广播失效，已记入 ARCH §8.7。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DictCache {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DictMapper dictMapper;
    private final DictItemMapper dictItemMapper;

    /** 本地一级缓存：typeCode -> 已排序的字典项列表 */
    private final Map<String, List<DictItem>> local = new ConcurrentHashMap<>();

    /** 专用 ObjectMapper：注册 JSR-310，日期以 ISO 字符串输出，忽略未知字段 */
    private static final ObjectMapper MAPPER = buildMapper();

    private static ObjectMapper buildMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    /**
     * 取某类型下的全部字典项（含停用项），本地 → Redis → DB 三级回源。
     *
     * @param typeCode 字典类型编码，如 {@code ISSUE_SOURCE}
     * @return 已排序的不可变列表；类型不存在时返回空列表
     */
    public List<DictItem> items(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            return Collections.emptyList();
        }
        List<DictItem> cached = local.get(typeCode);
        if (cached != null) {
            return cached;
        }
        List<DictItem> fromRedis = readRedis(typeCode);
        if (fromRedis != null) {
            local.put(typeCode, fromRedis);
            return fromRedis;
        }
        List<DictItem> fromDb = Collections.unmodifiableList(loadFromDb(typeCode));
        local.put(typeCode, fromDb);
        writeRedis(typeCode, fromDb);
        return fromDb;
    }

    /**
     * 失效单个类型缓存（任何字典写操作后必须调用）。
     *
     * @param typeCode 字典类型编码，为空时静默返回
     */
    public void evict(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            return;
        }
        local.remove(typeCode);
        try {
            redisTemplate.delete(Constants.REDIS_DICT_PREFIX + typeCode);
        } catch (Exception e) {
            log.warn("[DictCache] evict redis failed, typeCode={}, msg={}", typeCode, e.getMessage());
        }
    }

    /**
     * 失效全部字典缓存（类型删除 / 批量导入 / 数据初始化后调用）。
     */
    public void evictAll() {
        local.clear();
        try {
            Set<String> keys = redisTemplate.keys(Constants.REDIS_DICT_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("[DictCache] evictAll redis failed, msg={}", e.getMessage());
        }
    }

    /**
     * 从 Redis 读取并反序列化。
     *
     * @param typeCode 类型编码
     * @return 命中返回列表；未命中或异常返回 {@code null}（由调用方回源 DB）
     */
    private List<DictItem> readRedis(String typeCode) {
        try {
            Object raw = redisTemplate.opsForValue().get(Constants.REDIS_DICT_PREFIX + typeCode);
            if (raw == null) {
                return null;
            }
            String json = raw.toString();
            if (json.isBlank() || "null".equals(json)) {
                return null;
            }
            List<DictItem> list = MAPPER.readValue(json, new TypeReference<List<DictItem>>() {
            });
            return Collections.unmodifiableList(list);
        } catch (Exception e) {
            log.warn("[DictCache] read redis failed, typeCode={}, msg={}", typeCode, e.getMessage());
            return null;
        }
    }

    /**
     * 序列化写入 Redis（失败仅告警，不影响主流程）。
     *
     * @param typeCode 类型编码
     * @param items    字典项列表
     */
    private void writeRedis(String typeCode, List<DictItem> items) {
        try {
            String json = MAPPER.writeValueAsString(items);
            redisTemplate.opsForValue().set(Constants.REDIS_DICT_PREFIX + typeCode, json,
                    Constants.DICT_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[DictCache] write redis failed, typeCode={}, msg={}", typeCode, e.getMessage());
        }
    }

    /**
     * DB 回源：按 typeCode（即 dict_code）取该字典下全部未删除项。
     *
     * @param typeCode 字典编码（dict_code），如 {@code ISSUE_SOURCE}
     * @return 排序后的字典项列表，类型不存在时为空列表
     */
    private List<DictItem> loadFromDb(String typeCode) {
        Dict type = dictMapper.selectOne(
                new LambdaQueryWrapper<Dict>().eq(Dict::getDictCode, typeCode).last("LIMIT 1"));
        if (type == null) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getDictCode, typeCode)
                .orderByDesc(DictItem::getEnabled)
                .orderByAsc(DictItem::getSort)
                .orderByAsc(DictItem::getId);
        List<DictItem> rows = dictItemMapper.selectList(wrapper);
        return rows == null ? new ArrayList<>() : rows;
    }
}
