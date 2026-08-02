package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.fieldconfig.req.FieldConfigReq;
import com.issueflow.dto.fieldconfig.resp.FieldConfigVO;
import com.issueflow.dto.fieldconfig.resp.FieldNodeVO;
import com.issueflow.dto.fieldconfig.resp.FieldSchemaVO;
import com.issueflow.entity.FieldConfig;
import com.issueflow.entity.FieldSection;
import com.issueflow.enums.FieldType;
import com.issueflow.mapper.FieldConfigMapper;
import com.issueflow.mapper.FieldSectionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 字段配置服务（ARCH §3.4 / §3.2，T03）。
 * <p>职责：区域/字段 CRUD、schema 组装（走 {@link FieldSchemaCache}）、按类型清洗非生效属性、
 * 单级依赖强约束 + 多级环检测、写后缓存失效。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FieldConfigService {

    private final FieldConfigMapper configMapper;
    private final FieldSectionMapper sectionMapper;
    private final FieldSchemaCache schemaCache;
    private final PermissionService permissionService;

    // ============================ 查询 ============================

    /**
     * 表单渲染契约（登录即可，走两级缓存）。
     */
    public FieldSchemaVO getSchema(String typeScope) {
        if (typeScope == null || typeScope.isBlank()) {
            typeScope = "GLOBAL";
        }
        return schemaCache.getSchema(typeScope);
    }

    /**
     * 管理页树形表格：区域为父、字段为子（row-key 前缀防撞号由前端处理）。
     */
    public List<FieldNodeVO> listTree() {
        permissionService.requirePermission("field:config:list");
        List<FieldSection> sections = sectionMapper.selectList(
                new LambdaQueryWrapper<FieldSection>().orderByAsc(FieldSection::getSort).orderByAsc(FieldSection::getId));
        List<FieldConfig> fields = configMapper.selectList(
                new LambdaQueryWrapper<FieldConfig>().orderByAsc(FieldConfig::getSort).orderByAsc(FieldConfig::getId));

        Map<Long, List<FieldConfig>> bySection = fields.stream()
                .collect(java.util.stream.Collectors.groupingBy(FieldConfig::getSectionId,
                        LinkedHashMap::new, java.util.stream.Collectors.toList()));

        List<FieldNodeVO> nodes = new ArrayList<>();
        for (FieldSection s : sections) {
            FieldNodeVO sec = new FieldNodeVO();
            sec.setId(s.getId());
            sec.setParentId(null);
            sec.setCode(s.getCode());
            sec.setName(s.getName());
            sec.setNodeType("section");
            sec.setSort(s.getSort());
            sec.setEnabled(toBool(s.getEnabled()));
            sec.setSystem(toBool(s.getIsSystem()));
            nodes.add(sec);
            for (FieldConfig f : bySection.getOrDefault(s.getId(), new ArrayList<>())) {
                nodes.add(toNode(f, s.getId()));
            }
        }
        return nodes;
    }

    /**
     * 字段详情（管理页编辑回显）。
     */
    public FieldConfigVO detail(Long id) {
        permissionService.requirePermission("field:config:list");
        FieldConfig f = requireExists(id);
        return toVO(f);
    }

    /**
     * 取「生效 + 必填 + 自定义」字段集合（code → 配置），供问题落库前做必填校验。
     * <p>逻辑删除由 MyBatis-Plus 自动追加 {@code deleted=0} 过滤；NULL 的 required/enabled
     * 视为非必填/停用，不会被命中。</p>
     *
     * @return field_code → FieldConfig，可为空 Map
     */
    public Map<String, FieldConfig> listRequiredCustomEnabled() {
        List<FieldConfig> list = configMapper.selectList(new LambdaQueryWrapper<FieldConfig>()
                .eq(FieldConfig::getIsSystem, 0)
                .eq(FieldConfig::getRequired, 1)
                .eq(FieldConfig::getEnabled, 1));
        Map<String, FieldConfig> map = new LinkedHashMap<>();
        for (FieldConfig c : list) {
            map.put(c.getCode(), c);
        }
        return map;
    }

    // ============================ 写操作 ============================

    @Transactional
    public Long create(FieldConfigReq req) {
        permissionService.requirePermission("field:config:save");
        assertCodeUnique(req.getCode(), null);
        requireSectionExists(req.getSectionId());

        FieldType type = FieldType.fromCode(req.getType());
        FieldConfig entity = toEntity(req);
        validateByType(entity);

        FieldConfig draft = new FieldConfig();
        draft.setCode(entity.getCode());
        draft.setDependsOn(entity.getDependsOn());
        draft.setTypeScope(entity.getTypeScope());
        validateDepends(draft);

        configMapper.insert(entity);
        schemaCache.evict(entity.getTypeScope());
        return entity.getId();
    }

    @Transactional
    public void update(Long id, FieldConfigReq req) {
        permissionService.requirePermission("field:config:save");
        FieldConfig entity = requireExists(id);

        boolean system = entity.getIsSystem() != null && entity.getIsSystem() == 1;

        // Q4 铁律：type 不可改；不一致直接抛 FIELD_TYPE_IMMUTABLE
        if (StringUtils.hasText(req.getType()) && !req.getType().equalsIgnoreCase(entity.getType())) {
            throw new BizException(ResultCode.FIELD_TYPE_IMMUTABLE);
        }
        // code 创建后不可改（始终忽略入参 code），同时防止绕过系统字段编码
        if (system) {
            // F12：内置字段仅放行白名单属性
            if (req.getName() != null) {
                entity.setName(req.getName());
            }
            if (req.getI18nKey() != null) {
                entity.setI18nKey(req.getI18nKey());
            }
            if (req.getRequired() != null) {
                entity.setRequired(req.getRequired());
            }
            if (req.getSort() != null) {
                entity.setSort(req.getSort());
            }
            if (req.getPlaceholder() != null) {
                entity.setPlaceholder(req.getPlaceholder());
            }
            if (req.getSpan() != null) {
                entity.setSpan(req.getSpan());
            }
            if (req.getEnabled() != null) {
                entity.setEnabled(req.getEnabled());
            }
            // 其余（code/type/section/dictCode/refSource/displayType/dependsOn/...）硬拦截修改
            schemaCache.evict(entity.getTypeScope());
            configMapper.updateById(entity);
            return;
        }

        // 非系统字段：可改全部可写属性（code/type 除外）
        if (req.getSectionId() != null) {
            requireSectionExists(req.getSectionId());
            entity.setSectionId(req.getSectionId());
        }
        if (req.getName() != null) {
            entity.setName(req.getName());
        }
        if (req.getI18nKey() != null) {
            entity.setI18nKey(req.getI18nKey());
        }
        if (req.getRequired() != null) {
            entity.setRequired(req.getRequired());
        }
        if (req.getPlaceholder() != null) {
            entity.setPlaceholder(req.getPlaceholder());
        }
        if (req.getDefaultValue() != null) {
            entity.setDefaultValue(req.getDefaultValue());
        }
        if (req.getSpan() != null) {
            entity.setSpan(req.getSpan());
        }
        if (req.getMultiline() != null) {
            entity.setMultiline(req.getMultiline());
        }
        if (req.getMaxLength() != null) {
            entity.setMaxLength(req.getMaxLength());
        }
        if (req.getMinVal() != null) {
            entity.setMinVal(req.getMinVal());
        }
        if (req.getMaxVal() != null) {
            entity.setMaxVal(req.getMaxVal());
        }
        if (req.getDecimalScale() != null) {
            entity.setDecimalScale(req.getDecimalScale());
        }
        if (req.getDictCode() != null) {
            entity.setDictCode(req.getDictCode());
        }
        if (req.getRefSource() != null) {
            entity.setRefSource(req.getRefSource());
        }
        if (req.getDisplayType() != null) {
            entity.setDisplayType(req.getDisplayType());
        }
        if (req.getMultiSelect() != null) {
            entity.setMultiSelect(req.getMultiSelect());
        }
        if (req.getDependsOn() != null) {
            entity.setDependsOn(req.getDependsOn());
        }
        if (req.getDependsParam() != null) {
            entity.setDependsParam(req.getDependsParam());
        }
        if (req.getTypeScope() != null) {
            entity.setTypeScope(req.getTypeScope());
        }
        if (req.getSort() != null) {
            entity.setSort(req.getSort());
        }
        if (req.getEnabled() != null) {
            entity.setEnabled(req.getEnabled());
        }
        if (req.getVisibleInList() != null) {
            entity.setVisibleInList(req.getVisibleInList());
        }
        if (req.getSearchable() != null) {
            entity.setSearchable(req.getSearchable());
        }

        validateByType(entity);
        validateDepends(entity);
        configMapper.updateById(entity);
        schemaCache.evict(entity.getTypeScope());
    }

    @Transactional
    public void delete(Long id) {
        permissionService.requirePermission("field:config:delete");
        FieldConfig entity = requireExists(id);
        if (entity.getIsSystem() != null && entity.getIsSystem() == 1) {
            throw new BizException(ResultCode.FIELD_SYSTEM_PROTECTED);
        }
        configMapper.deleteById(id);
        // 字段值软删保留（Q5）：issue_field_value 由 IssueFieldValueService 负责失效，此处不物理清理
        schemaCache.evict(entity.getTypeScope());
    }

    @Transactional
    public void toggleStatus(Long id, Boolean enabled) {
        permissionService.requirePermission("field:config:save");
        FieldConfig entity = requireExists(id);
        entity.setEnabled(Boolean.TRUE.equals(enabled) ? 1 : 0);
        configMapper.updateById(entity);
        schemaCache.evict(entity.getTypeScope());
    }

    // ============================ 类型 / 依赖校验 ============================

    /**
     * 按字段类型清理非生效属性（强制置 NULL，避免脏配置，ARCH §2.2 矩阵）。
     */
    public void validateByType(FieldConfig f) {
        FieldType type = FieldType.fromCode(f.getType());
        switch (type) {
            case TEXT:
                f.setMinVal(null);
                f.setMaxVal(null);
                f.setDecimalScale(null);
                f.setDictCode(null);
                f.setRefSource(null);
                f.setDisplayType(null);
                f.setDependsOn(cleanDepends(f.getDependsOn()));
                break;
            case NUMBER:
                f.setMultiline(null);
                f.setMaxLength(null);
                f.setDictCode(null);
                f.setRefSource(null);
                f.setDisplayType(null);
                f.setDependsOn(cleanDepends(f.getDependsOn()));
                break;
            case DATE:
            case DATETIME:
                f.setMultiline(null);
                f.setMaxLength(null);
                f.setMinVal(null);
                f.setMaxVal(null);
                f.setDecimalScale(null);
                f.setDictCode(null);
                f.setRefSource(null);
                f.setDisplayType(null);
                f.setDependsOn(cleanDepends(f.getDependsOn()));
                break;
            case DICT:
                f.setMinVal(null);
                f.setMaxVal(null);
                f.setDecimalScale(null);
                f.setMultiline(null);
                f.setMaxLength(null);
                f.setRefSource(null);
                f.setDisplayType(null);
                if (!StringUtils.hasText(f.getDictCode())) {
                    throw new BizException(ResultCode.VALID_ERROR, "DICT 类型必须指定 dictCode");
                }
                f.setDependsOn(cleanDepends(f.getDependsOn()));
                break;
            case REF:
                f.setMinVal(null);
                f.setMaxVal(null);
                f.setDecimalScale(null);
                f.setMultiline(null);
                f.setMaxLength(null);
                f.setDictCode(null);
                if (!StringUtils.hasText(f.getRefSource())) {
                    throw new BizException(ResultCode.VALID_ERROR, "REF 类型必须指定 refSource");
                }
                if (!StringUtils.hasText(f.getDisplayType())) {
                    // 兜底取 registry.query_type（service 层不再查 DB，交由前端按 refSource 决定；此处留空由前端兜底）
                    f.setDisplayType(null);
                }
                f.setDependsOn(cleanDepends(f.getDependsOn()));
                break;
            default:
                break;
        }
    }

    /**
     * 依赖校验：本期单级强约束 + 多级环检测（ARCH §3.4）。
     */
    public void validateDepends(FieldConfig current) {
        String self = current.getCode();
        String dep = current.getDependsOn();
        if (!StringUtils.hasText(dep)) {
            return;
        }
        if (dep.equals(self)) {
            throw new BizException(ResultCode.FIELD_DEPENDS_SELF);
        }
        FieldConfig src = configMapper.selectOne(new LambdaQueryWrapper<FieldConfig>().eq(FieldConfig::getCode, dep));
        if (src == null || src.getEnabled() == null || src.getEnabled() == 0) {
            throw new BizException(ResultCode.FIELD_DEPENDS_SOURCE_INVALID);
        }
        if (!src.getTypeScope().equals(current.getTypeScope())) {
            throw new BizException(ResultCode.FIELD_DEPENDS_SCOPE_MISMATCH);
        }
        if (src.getMultiSelect() != null && src.getMultiSelect() == 1) {
            throw new BizException(ResultCode.FIELD_DEPENDS_MULTI_NOT_ALLOWED);
        }
        // 本期单级约束：依赖源自身不可再有依赖
        if (StringUtils.hasText(src.getDependsOn())) {
            throw new BizException(ResultCode.FIELD_DEPENDS_LEVEL_EXCEEDED);
        }
        // 多级环检测（算法就绪）
        List<String> cycle = detectCycle(self, dep, buildDependsMap(current));
        if (cycle != null) {
            throw new BizException(ResultCode.FIELD_DEPENDS_CYCLE, String.join(" → ", cycle));
        }
    }

    private String cleanDepends(String dependsOn) {
        return StringUtils.hasText(dependsOn) ? dependsOn : null;
    }

    /** 依赖图：code -> dependsOn（仅取同 typeScope 且未删除、有依赖的字段）。含当前待保存边由 detectCycle 并入。 */
    private Map<String, String> buildDependsMap(FieldConfig current) {
        Map<String, String> graph = new LinkedHashMap<>();
        List<FieldConfig> all = configMapper.selectList(
                new LambdaQueryWrapper<FieldConfig>().eq(FieldConfig::getTypeScope, current.getTypeScope()));
        for (FieldConfig f : all) {
            if (StringUtils.hasText(f.getDependsOn())) {
                graph.put(f.getCode(), f.getDependsOn());
            }
        }
        return graph;
    }

    /** 以「假设本次保存已生效」的依赖图做 DFS，返回环路径（如 [A,B,C,A]），无环返回 null */
    private List<String> detectCycle(String self, String dep, Map<String, String> graph) {
        graph.put(self, dep);
        Set<String> visiting = new LinkedHashSet<>();
        return dfs(self, graph, visiting);
    }

    private List<String> dfs(String node, Map<String, String> graph, Set<String> visiting) {
        if (node == null) {
            return null;
        }
        if (visiting.contains(node)) {
            List<String> path = new ArrayList<>(visiting);
            path = path.subList(path.indexOf(node), path.size());
            path.add(node);
            return path;
        }
        visiting.add(node);
        List<String> r = dfs(graph.get(node), graph, visiting);
        visiting.remove(node);
        return r;
    }

    // ============================ 私有 ============================

    private FieldConfig toEntity(FieldConfigReq req) {
        FieldConfig e = new FieldConfig();
        e.setSectionId(req.getSectionId());
        e.setCode(req.getCode());
        e.setName(req.getName());
        e.setI18nKey(req.getI18nKey());
        e.setType(req.getType().toUpperCase());
        e.setRequired(req.getRequired() == null ? 0 : req.getRequired());
        e.setPlaceholder(req.getPlaceholder());
        e.setDefaultValue(req.getDefaultValue());
        e.setSpan(req.getSpan() == null ? 12 : req.getSpan());
        e.setMultiline(req.getMultiline());
        e.setMaxLength(req.getMaxLength());
        e.setMinVal(req.getMinVal());
        e.setMaxVal(req.getMaxVal());
        e.setDecimalScale(req.getDecimalScale());
        e.setDictCode(req.getDictCode());
        e.setRefSource(req.getRefSource());
        e.setDisplayType(req.getDisplayType());
        e.setMultiSelect(req.getMultiSelect() == null ? 0 : req.getMultiSelect());
        e.setDependsOn(req.getDependsOn());
        e.setDependsParam(req.getDependsParam());
        e.setTypeScope(req.getTypeScope() == null || req.getTypeScope().isBlank() ? "GLOBAL" : req.getTypeScope());
        e.setSort(req.getSort() == null ? 0 : req.getSort());
        e.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        e.setVisibleInList(req.getVisibleInList() == null ? 0 : req.getVisibleInList());
        e.setSearchable(req.getSearchable() == null ? 0 : req.getSearchable());
        e.setIsSystem(0);
        return e;
    }

    private FieldConfig requireExists(Long id) {
        FieldConfig e = configMapper.selectById(id);
        if (e == null) {
            throw new BizException(ResultCode.NOT_FOUND, "字段配置不存在");
        }
        return e;
    }

    private void requireSectionExists(Long sectionId) {
        if (sectionId == null) {
            throw new BizException(ResultCode.VALID_ERROR, "所属区域不能为空");
        }
        if (sectionMapper.selectById(sectionId) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "所属区域不存在");
        }
    }

    private void assertCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<FieldConfig> wrapper = new LambdaQueryWrapper<FieldConfig>().eq(FieldConfig::getCode, code);
        if (excludeId != null) {
            wrapper.ne(FieldConfig::getId, excludeId);
        }
        if (configMapper.selectCount(wrapper) > 0) {
            throw new BizException(ResultCode.FIELD_CODE_DUPLICATE);
        }
    }

    private FieldNodeVO toNode(FieldConfig f, Long sectionId) {
        FieldNodeVO n = new FieldNodeVO();
        n.setId(f.getId());
        n.setParentId(sectionId);
        n.setCode(f.getCode());
        n.setName(f.getName());
        n.setNodeType("field");
        n.setType(f.getType());
        n.setRefSource(f.getRefSource());
        n.setDependsOn(f.getDependsOn());
        n.setSort(f.getSort());
        n.setEnabled(toBool(f.getEnabled()));
        n.setSystem(toBool(f.getIsSystem()));
        return n;
    }

    private FieldConfigVO toVO(FieldConfig f) {
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
}
