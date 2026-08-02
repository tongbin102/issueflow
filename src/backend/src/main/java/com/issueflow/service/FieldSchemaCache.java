package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.issueflow.common.Constants;
import com.issueflow.dto.fieldconfig.resp.FieldConfigVO;
import com.issueflow.dto.fieldconfig.resp.FieldSchemaSectionVO;
import com.issueflow.dto.fieldconfig.resp.FieldSchemaVO;
import com.issueflow.entity.FieldConfig;
import com.issueflow.entity.FieldSection;
import com.issueflow.mapper.FieldConfigMapper;
import com.issueflow.mapper.FieldSectionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 字段 schema 两级缓存：本地 {@link ConcurrentHashMap} + Redis（照抄 {@code DictCache} 范式，ARCH §7.3）。
 *
 * <p>设计要点：</p>
 * <ol>
 *   <li>key = {@code field:schema:{typeScope}}（本期恒 {@code field:schema:GLOBAL}），Redis TTL 30min；
 *       本地 Map 无 TTL，但写操作后由 {@link #evict(String)} 同步清理。</li>
 *   <li>Redis 中存自序列化 JSON（独立 ObjectMapper，注册 JavaTimeModule），异常降级直读 DB。</li>
 *   <li>任何 field_config / field_section 写操作后，调用方必须 {@link #evict(String)}。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FieldSchemaCache {

    private final RedisTemplate<String, Object> redisTemplate;
    private final FieldSectionMapper sectionMapper;
    private final FieldConfigMapper configMapper;

    /** 本地一级缓存：typeScope -> schema */
    private final Map<String, FieldSchemaVO> local = new ConcurrentHashMap<>();

    private static final ObjectMapper MAPPER = buildMapper();

    private static ObjectMapper buildMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    /** 系统固定页签（不入库，恒定追加，ARCH §2.1） */
    private static final List<String> SYSTEM_TABS = List.of("relation", "history", "attachment");

    /**
     * 取某 scope 下的表单 schema（含系统固定页签），本地 → Redis → DB 三级回源。
     *
     * @param typeScope 生效范围，本期恒 GLOBAL
     * @return schema 视图（含 sections + systemTabs + version）
     */
    public FieldSchemaVO getSchema(String typeScope) {
        if (typeScope == null || typeScope.isBlank()) {
            typeScope = "GLOBAL";
        }
        FieldSchemaVO cached = local.get(typeScope);
        if (cached != null) {
            return cached;
        }
        FieldSchemaVO fromRedis = readRedis(typeScope);
        if (fromRedis != null) {
            local.put(typeScope, fromRedis);
            return fromRedis;
        }
        FieldSchemaVO fromDb = loadFromDb(typeScope);
        local.put(typeScope, fromDb);
        writeRedis(typeScope, fromDb);
        return fromDb;
    }

    /**
     * 失效指定 scope 的 schema 缓存（任何字段/区域写操作后调用）。
     */
    public void evict(String typeScope) {
        if (typeScope == null || typeScope.isBlank()) {
            typeScope = "GLOBAL";
        }
        local.remove(typeScope);
        try {
            redisTemplate.delete(Constants.REDIS_FIELD_SCHEMA_PREFIX + typeScope);
        } catch (Exception e) {
            log.warn("[FieldSchemaCache] evict redis failed, typeScope={}, msg={}", typeScope, e.getMessage());
        }
    }

    private FieldSchemaVO readRedis(String typeScope) {
        try {
            Object raw = redisTemplate.opsForValue().get(Constants.REDIS_FIELD_SCHEMA_PREFIX + typeScope);
            if (raw == null) {
                return null;
            }
            String json = raw.toString();
            if (json.isBlank() || "null".equals(json)) {
                return null;
            }
            return MAPPER.readValue(json, new TypeReference<FieldSchemaVO>() {
            });
        } catch (Exception e) {
            log.warn("[FieldSchemaCache] read redis failed, typeScope={}, msg={}", typeScope, e.getMessage());
            return null;
        }
    }

    private void writeRedis(String typeScope, FieldSchemaVO schema) {
        try {
            String json = MAPPER.writeValueAsString(schema);
            redisTemplate.opsForValue().set(Constants.REDIS_FIELD_SCHEMA_PREFIX + typeScope, json,
                    Constants.FIELD_SCHEMA_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[FieldSchemaCache] write redis failed, typeScope={}, msg={}", typeScope, e.getMessage());
        }
    }

    /**
     * DB 回源：按 scope 取全部未删除区域与字段，组装 schema（sections/fields 均已按 sort 升序）。
     */
    private FieldSchemaVO loadFromDb(String typeScope) {
        List<FieldSection> sections = sectionMapper.selectList(new LambdaQueryWrapper<FieldSection>()
                .eq(FieldSection::getTypeScope, typeScope)
                .orderByAsc(FieldSection::getSort).orderByAsc(FieldSection::getId));

        List<FieldConfig> fields = configMapper.selectList(new LambdaQueryWrapper<FieldConfig>()
                .eq(FieldConfig::getTypeScope, typeScope)
                .orderByAsc(FieldConfig::getSort).orderByAsc(FieldConfig::getId));

        Map<Long, List<FieldConfig>> bySection = fields.stream()
                .collect(Collectors.groupingBy(FieldConfig::getSectionId, LinkedHashMap::new, Collectors.toList()));

        long version = 0L;
        List<FieldSchemaSectionVO> sectionVos = new ArrayList<>();
        for (FieldSection s : sections) {
            FieldSchemaSectionVO sv = new FieldSchemaSectionVO();
            sv.setCode(s.getCode());
            sv.setName(s.getName());
            sv.setI18nKey(s.getI18nKey());
            sv.setSort(s.getSort());
            List<FieldConfigVO> fv = bySection.getOrDefault(s.getId(), Collections.emptyList()).stream()
                    .map(this::toConfigVO).collect(Collectors.toList());
            sv.setFields(fv);
            sectionVos.add(sv);
            version = Math.max(version, toMillis(s.getUpdatedAt()));
        }
        for (FieldConfig f : fields) {
            version = Math.max(version, toMillis(f.getUpdatedAt()));
        }

        FieldSchemaVO schema = new FieldSchemaVO();
        schema.setTypeScope(typeScope);
        schema.setVersion(version == 0L ? System.currentTimeMillis() : version);
        schema.setSections(sectionVos);
        schema.setSystemTabs(SYSTEM_TABS);
        return schema;
    }

    private FieldConfigVO toConfigVO(FieldConfig f) {
        FieldConfigVO vo = new FieldConfigVO();
        vo.setId(f.getId());
        vo.setSectionId(f.getSectionId());
        vo.setCode(f.getCode());
        vo.setName(f.getName());
        vo.setI18nKey(f.getI18nKey());
        vo.setType(f.getType());
        vo.setRequired(toBool(f.getRequired()));
        vo.setPlaceholder(f.getPlaceholder());
        vo.setDefaultValue(f.getDefaultValue());
        vo.setSpan(f.getSpan());
        vo.setMultiline(toBool(f.getMultiline()));
        vo.setMaxLength(f.getMaxLength());
        vo.setMinVal(f.getMinVal());
        vo.setMaxVal(f.getMaxVal());
        vo.setDecimalScale(f.getDecimalScale());
        vo.setDictCode(f.getDictCode());
        vo.setRefSource(f.getRefSource());
        vo.setDisplayType(f.getDisplayType());
        vo.setMultiSelect(toBool(f.getMultiSelect()));
        vo.setDependsOn(f.getDependsOn());
        vo.setDependsParam(f.getDependsParam());
        vo.setSystem(toBool(f.getIsSystem()));
        vo.setEnabled(toBool(f.getEnabled()));
        vo.setVisibleInList(toBool(f.getVisibleInList()));
        vo.setSearchable(toBool(f.getSearchable()));
        vo.setTypeScope(f.getTypeScope());
        vo.setSort(f.getSort());
        return vo;
    }

    private Boolean toBool(Integer v) {
        return v != null && v == 1;
    }

    private long toMillis(LocalDateTime t) {
        return t == null ? 0L : t.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
