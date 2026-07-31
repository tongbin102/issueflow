package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.DictItemReq;
import com.issueflow.dto.req.DictTypeReq;
import com.issueflow.dto.resp.DictItemVO;
import com.issueflow.dto.resp.DictOptionVO;
import com.issueflow.dto.resp.DictTypeVO;
import com.issueflow.entity.Dict;
import com.issueflow.entity.DictItem;
import com.issueflow.enums.DictTypeCodeEnum;
import com.issueflow.mapper.DictItemMapper;
import com.issueflow.mapper.DictMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 字典配置服务（Phase 7 完整实现）
 * <p>字典类型 CRUD + 字典项 CRUD + 下拉选项 + 预设保护 + 两级缓存。</p>
 *
 * <p>设计要点：
 * <ul>
 *   <li>权限：所有管理接口首行 {@code requirePermission}；options 下拉为登录即可（无权限码）。</li>
 *   <li>预设保护：is_system=1 的类型/选项禁止删除，仅可停用（enabled=0），否则抛业务异常。</li>
 *   <li>镜像类型（ISSUE_STATUS / ISSUE_PRIORITY / ISSUE_SEVERITY）禁止新增选项（页面只读）。</li>
 *   <li>字典编码创建后不可改；选项编码同字典内唯一（由生成列 uk_dict_item_active 兜底）。</li>
 *   <li><b>读走缓存</b>：{@link #options} / {@link #itemNameMap} / {@link #requireEnabledSource}
 *       一律经 {@link DictCache}，问题列表回填 0 次额外 DB 查询；
 *       <b>写必失效</b>：任何字典项写操作后调用 {@link DictCache#evict(String)}。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DictService {

    private final DictMapper dictMapper;
    private final DictItemMapper dictItemMapper;
    private final PermissionService permissionService;
    private final DictCache dictCache;

    // ============================ 字典类型 ============================

    /**
     * 字典类型管理列表（含停用项、镜像标记、选项计数）
     *
     * @param keyword 关键字，匹配编码或名称，可为空
     * @param enabled 启用态过滤，1 启用 / 0 停用，可为空
     * @return 类型视图列表，按 sort 升序
     */
    public List<DictTypeVO> listTypes(String keyword, Integer enabled) {
        permissionService.requirePermission("dict:list");
        LambdaQueryWrapper<Dict> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(q -> q.like(Dict::getDictCode, kw).or().like(Dict::getName, kw));
        }
        if (enabled != null) {
            wrapper.eq(Dict::getEnabled, enabled);
        }
        wrapper.orderByAsc(Dict::getSort).orderByAsc(Dict::getId);
        List<Dict> rows = dictMapper.selectList(wrapper);

        Map<String, Long> countMap = countItemsByDictCode(
                rows.stream().map(Dict::getDictCode).collect(Collectors.toList()));
        return rows.stream().map(row -> {
            DictTypeVO vo = new DictTypeVO();
            vo.setId(row.getId());
            vo.setCode(row.getDictCode());
            vo.setName(row.getName());
            vo.setDescription(row.getDescription());
            vo.setSort(row.getSort());
            vo.setEnabled(row.getEnabled() != null && row.getEnabled() == 1);
            vo.setIsSystem(row.getIsSystem() != null && row.getIsSystem() == 1);
            vo.setMirror(DictTypeCodeEnum.isMirrorType(row.getDictCode()));
            vo.setItemCount(countMap.getOrDefault(row.getDictCode(), 0L));
            vo.setUpdatedAt(row.getUpdatedAt());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 新增字典类型（code 在 deleted=0 范围内唯一；编码创建后不可改）
     *
     * @param req 类型入参
     * @return 新类型主键
     */
    @Transactional
    public Long createType(DictTypeReq req) {
        permissionService.requirePermission("dict:create");
        assertDictCodeUnique(req.getCode(), null);
        Dict entity = new Dict();
        entity.setDictCode(req.getCode());
        entity.setName(req.getName());
        entity.setDescription(req.getDescription());
        entity.setSort(req.getSort() == null ? 0 : req.getSort());
        entity.setEnabled(Boolean.FALSE.equals(req.getEnabled()) ? 0 : 1);
        entity.setIsSystem(0);
        dictMapper.insert(entity);
        return entity.getId();
    }

    /**
     * 编辑字典类型（忽略 code，编码不可改；可改启用态）
     *
     * @param id  类型主键
     * @param req 类型入参
     */
    @Transactional
    public void updateType(Long id, DictTypeReq req) {
        permissionService.requirePermission("dict:update");
        Dict entity = requireTypeExists(id);
        entity.setName(req.getName());
        entity.setDescription(req.getDescription());
        entity.setSort(req.getSort() == null ? 0 : req.getSort());
        if (req.getEnabled() != null) {
            entity.setEnabled(req.getEnabled() ? 1 : 0);
        }
        dictMapper.updateById(entity);
        dictCache.evict(entity.getDictCode());
    }

    /**
     * 字典类型启停切换
     *
     * @param id      类型主键
     * @param enabled true 启用 / false 停用
     */
    @Transactional
    public void toggleTypeStatus(Long id, Boolean enabled) {
        permissionService.requirePermission("dict:update");
        Dict entity = requireTypeExists(id);
        entity.setEnabled(Boolean.TRUE.equals(enabled) ? 1 : 0);
        dictMapper.updateById(entity);
        dictCache.evict(entity.getDictCode());
    }

    /**
     * 删除字典类型：系统预设类型硬拦截；存在选项时拦截。
     *
     * @param id 类型主键
     */
    @Transactional
    public void deleteType(Long id) {
        permissionService.requirePermission("dict:delete");
        Dict entity = requireTypeExists(id);
        if (entity.getIsSystem() != null && entity.getIsSystem() == 1) {
            throw new BizException(ResultCode.DICT_TYPE_SYSTEM_PROTECTED);
        }
        long itemCount = dictItemMapper.selectCount(
                new LambdaQueryWrapper<DictItem>().eq(DictItem::getDictCode, entity.getDictCode()));
        if (itemCount > 0) {
            throw new BizException(ResultCode.DICT_TYPE_HAS_ITEMS);
        }
        dictMapper.deleteById(id);
        dictCache.evict(entity.getDictCode());
    }

    // ============================ 字典项 ============================

    /**
     * 字典项下拉选项（登录即可，无权限码）：全站下拉唯一数据源，命中两级缓存，0 次 DB 查询。
     *
     * @param typeCode        字典类型编码
     * @param includeDisabled true 含停用项（缓存内已按「启用优先」排序，停用项自然置底）
     * @return 下拉选项列表
     */
    public List<DictOptionVO> options(String typeCode, Boolean includeDisabled) {
        if (typeCode == null || typeCode.isBlank()) {
            throw new BizException(ResultCode.VALID_ERROR, "字典类型编码不能为空");
        }
        boolean all = Boolean.TRUE.equals(includeDisabled);
        return dictCache.items(typeCode).stream()
                .filter(row -> all || (row.getEnabled() != null && row.getEnabled() == 1))
                .map(this::toOptionVO)
                .collect(Collectors.toList());
    }

    /**
     * 字典项管理列表（含引用计数；仅 ISSUE_SOURCE 有计数意义）
     *
     * @param typeCode 字典类型编码
     * @param keyword  关键字，匹配名称或编码
     * @param enabled  启用态过滤
     * @return 字典项视图列表
     */
    public List<DictItemVO> listItems(String typeCode, String keyword, Integer enabled) {
        permissionService.requirePermission("dict:list");
        if (typeCode == null || typeCode.isBlank()) {
            throw new BizException(ResultCode.VALID_ERROR, "字典类型编码不能为空");
        }
        LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictItem::getDictCode, typeCode);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(q -> q.like(DictItem::getName, kw).or().like(DictItem::getItemCode, kw));
        }
        if (enabled != null) {
            wrapper.eq(DictItem::getEnabled, enabled);
        }
        wrapper.orderByAsc(DictItem::getSort).orderByAsc(DictItem::getId);
        List<DictItem> rows = dictItemMapper.selectList(wrapper);

        // 引用计数：一条 GROUP BY 出全量，内存回填，严禁按行 COUNT（N+1）
        Map<String, Long> refMap = new HashMap<>();
        if (Constants.DICT_TYPE_ISSUE_SOURCE.equals(typeCode)) {
            refMap = countIssueBySourceCode();
        }
        Map<String, Long> finalRefMap = refMap;
        return rows.stream().map(row -> {
            DictItemVO vo = new DictItemVO();
            vo.setId(row.getId());
            vo.setTypeCode(row.getDictCode());
            vo.setCode(row.getItemCode());
            vo.setName(row.getName());
            vo.setDescription(row.getDescription());
            vo.setSort(row.getSort());
            vo.setEnabled(row.getEnabled() != null && row.getEnabled() == 1);
            vo.setIsSystem(row.getIsSystem() != null && row.getIsSystem() == 1);
            vo.setExtra(row.getExtra());
            vo.setRefCount(finalRefMap.getOrDefault(row.getItemCode(), 0L));
            vo.setUpdatedAt(row.getUpdatedAt());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 新增字典项（镜像类型禁止新增；编码同字典内唯一）
     *
     * @param req 字典项入参
     * @return 新字典项主键
     */
    @Transactional
    public Long createItem(DictItemReq req) {
        permissionService.requirePermission("dict:create");
        if (DictTypeCodeEnum.isMirrorType(req.getTypeCode())) {
            throw new BizException(ResultCode.DICT_TYPE_MIRROR_READONLY);
        }
        requireDictCodeExists(req.getTypeCode());
        assertItemCodeUnique(req.getTypeCode(), req.getCode(), null);
        DictItem entity = new DictItem();
        entity.setDictCode(req.getTypeCode());
        entity.setItemCode(req.getCode());
        entity.setName(req.getName());
        entity.setDescription(req.getDescription());
        entity.setSort(req.getSort() == null ? 0 : req.getSort());
        entity.setEnabled(Boolean.FALSE.equals(req.getEnabled()) ? 0 : 1);
        entity.setIsSystem(0);
        entity.setExtra(req.getExtra());
        dictItemMapper.insert(entity);
        dictCache.evict(entity.getDictCode());
        return entity.getId();
    }

    /**
     * 编辑字典项（code 一律以库中值为准，预设项 code 改动静默忽略；可改启用态、扩展值）
     *
     * @param id  字典项主键
     * @param req 字典项入参
     */
    @Transactional
    public void updateItem(Long id, DictItemReq req) {
        permissionService.requirePermission("dict:update");
        DictItem entity = requireItemExists(id);
        entity.setName(req.getName());
        entity.setDescription(req.getDescription());
        entity.setSort(req.getSort() == null ? 0 : req.getSort());
        if (req.getEnabled() != null) {
            entity.setEnabled(req.getEnabled() ? 1 : 0);
        }
        entity.setExtra(req.getExtra());
        dictItemMapper.updateById(entity);
        dictCache.evict(entity.getDictCode());
    }

    /**
     * 字典项启停切换
     *
     * @param id      字典项主键
     * @param enabled true 启用 / false 停用
     */
    @Transactional
    public void toggleItemStatus(Long id, Boolean enabled) {
        permissionService.requirePermission("dict:update");
        DictItem entity = requireItemExists(id);
        entity.setEnabled(Boolean.TRUE.equals(enabled) ? 1 : 0);
        dictItemMapper.updateById(entity);
        dictCache.evict(entity.getDictCode());
    }

    /**
     * 删除字典项：系统预设项硬拦截（仅可停用）；ISSUE_SOURCE 下被问题引用时硬拦截。
     *
     * @param id 字典项主键
     */
    @Transactional
    public void deleteItem(Long id) {
        permissionService.requirePermission("dict:delete");
        DictItem entity = requireItemExists(id);
        if (entity.getIsSystem() != null && entity.getIsSystem() == 1) {
            throw new BizException(ResultCode.DICT_ITEM_SYSTEM_PROTECTED);
        }
        if (Constants.DICT_TYPE_ISSUE_SOURCE.equals(entity.getDictCode())) {
            long refCount = countIssueBySourceCode().getOrDefault(entity.getItemCode(), 0L);
            if (refCount > 0) {
                throw new BizException(ResultCode.DICT_ITEM_HAS_USAGE,
                        "该选项下存在 " + refCount + " 条问题，无法删除，可改为停用");
            }
        }
        dictItemMapper.deleteById(id);
        dictCache.evict(entity.getDictCode());
    }

    // ============================ 供业务侧调用（无权限校验，全部走缓存） ============================

    /**
     * 批量取字典项名称映射（itemCode -&gt; name），供问题列表/详情回填来源名称。
     * <p>数据源为 {@link DictCache}，<b>0 次额外 DB 查询</b>，杜绝列表 N+1。</p>
     *
     * @param typeCode  字典类型编码
     * @param itemCodes 需要回填的选项编码集合，可为空
     * @return 编码 → 名称映射，未命中的编码不出现在结果中
     */
    public Map<String, String> itemNameMap(String typeCode, Collection<String> itemCodes) {
        Map<String, String> result = new HashMap<>();
        if (typeCode == null || typeCode.isBlank() || itemCodes == null || itemCodes.isEmpty()) {
            return result;
        }
        Set<String> wanted = itemCodes.stream()
                .filter(Objects::nonNull).filter(s -> !s.isBlank()).collect(Collectors.toCollection(HashSet::new));
        if (wanted.isEmpty()) {
            return result;
        }
        for (DictItem row : dictCache.items(typeCode)) {
            if (wanted.contains(row.getItemCode())) {
                result.put(row.getItemCode(), row.getName());
            }
        }
        return result;
    }

    /**
     * 校验来源编码合法且处于启用状态（新建 / 编辑时选中的来源必须可用）。
     * <p>历史数据回显不走本方法，故停用来源的旧问题仍能正常展示。</p>
     *
     * @param sourceCode 来源编码（dict_item.item_code，类型 ISSUE_SOURCE）
     * @throws BizException 编码不存在或已停用
     */
    public void requireEnabledSource(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return;
        }
        DictItem hit = null;
        for (DictItem row : dictCache.items(Constants.DICT_TYPE_ISSUE_SOURCE)) {
            if (sourceCode.equals(row.getItemCode())) {
                hit = row;
                break;
            }
        }
        if (hit == null) {
            throw new BizException(ResultCode.DICT_ITEM_NOT_FOUND);
        }
        if (hit.getEnabled() == null || hit.getEnabled() != 1) {
            throw new BizException(ResultCode.DICT_ITEM_DISABLED);
        }
    }

    /**
     * 默认来源编码：优先 MANUAL（人工录入），MANUAL 不可用时退回 SYSTEM。
     * <p>全程走缓存，不查库（ARCH T3 要点 2）。</p>
     *
     * @return 默认来源 item_code
     */
    public String defaultSourceCode() {
        for (DictItem row : dictCache.items(Constants.DICT_TYPE_ISSUE_SOURCE)) {
            if (Constants.DICT_ITEM_SOURCE_MANUAL.equals(row.getItemCode())
                    && row.getEnabled() != null && row.getEnabled() == 1) {
                return Constants.DICT_ITEM_SOURCE_MANUAL;
            }
        }
        return Constants.DICT_ITEM_SOURCE_SYSTEM;
    }

    // ============================ 私有方法 ============================

    private DictOptionVO toOptionVO(DictItem row) {
        DictOptionVO vo = new DictOptionVO();
        vo.setId(row.getId());
        vo.setName(row.getName());
        vo.setCode(row.getItemCode());
        vo.setEnabled(row.getEnabled() != null && row.getEnabled() == 1);
        vo.setExtra(row.getExtra());
        return vo;
    }

    private Dict requireTypeExists(Long id) {
        Dict entity = dictMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DICT_TYPE_NOT_FOUND);
        }
        return entity;
    }

    private void requireDictCodeExists(String dictCode) {
        Long count = dictMapper.selectCount(new LambdaQueryWrapper<Dict>().eq(Dict::getDictCode, dictCode));
        if (count == null || count == 0) {
            throw new BizException(ResultCode.DICT_TYPE_NOT_FOUND, "字典类型编码不存在");
        }
    }

    private DictItem requireItemExists(Long id) {
        DictItem entity = dictItemMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DICT_ITEM_NOT_FOUND);
        }
        return entity;
    }

    private void assertDictCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<Dict> wrapper = new LambdaQueryWrapper<Dict>().eq(Dict::getDictCode, code);
        if (excludeId != null) {
            wrapper.ne(Dict::getId, excludeId);
        }
        if (dictMapper.selectCount(wrapper) > 0) {
            throw new BizException(ResultCode.DICT_TYPE_CODE_DUPLICATE);
        }
    }

    private void assertItemCodeUnique(String dictCode, String itemCode, Long excludeId) {
        LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getDictCode, dictCode).eq(DictItem::getItemCode, itemCode);
        if (excludeId != null) {
            wrapper.ne(DictItem::getId, excludeId);
        }
        if (dictItemMapper.selectCount(wrapper) > 0) {
            throw new BizException(ResultCode.DICT_ITEM_CODE_DUPLICATE);
        }
    }

    /** 批量统计每个字典类型下的选项数（一次 GROUP BY，禁止 N+1） */
    private Map<String, Long> countItemsByDictCode(List<String> dictCodes) {
        Map<String, Long> result = new HashMap<>();
        if (dictCodes == null || dictCodes.isEmpty()) {
            return result;
        }
        List<String> distinct = dictCodes.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return result;
        }
        QueryWrapper<DictItem> wrapper = new QueryWrapper<>();
        wrapper.select("dict_code AS dictCode", "COUNT(*) AS cnt")
                .in("dict_code", distinct)
                .groupBy("dict_code");
        List<Map<String, Object>> rows = dictItemMapper.selectMaps(wrapper);
        for (Map<String, Object> row : rows) {
            Object dc = row.get("dictCode");
            Object cnt = row.get("cnt");
            if (dc != null && cnt != null) {
                result.put(String.valueOf(dc), Long.valueOf(cnt.toString()));
            }
        }
        return result;
    }

    /** 统计每个来源（item_code）被多少条未删除问题引用（一次 GROUP BY） */
    private Map<String, Long> countIssueBySourceCode() {
        Map<String, Long> result = new HashMap<>();
        List<Map<String, Object>> rows = dictItemMapper.countIssueBySourceCode();
        for (Map<String, Object> row : rows) {
            Object sc = row.get("sourceCode");
            Object cnt = row.get("cnt");
            if (sc != null && cnt != null) {
                result.put(String.valueOf(sc), Long.valueOf(cnt.toString()));
            }
        }
        return result;
    }
}
