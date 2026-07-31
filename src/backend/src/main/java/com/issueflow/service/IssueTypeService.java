package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.IssueTypeReq;
import com.issueflow.dto.resp.IssueTypeOptionVO;
import com.issueflow.dto.resp.IssueTypeVO;
import com.issueflow.entity.Issue;
import com.issueflow.entity.IssueType;
import com.issueflow.mapper.IssueMapper;
import com.issueflow.mapper.IssueTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 问题类型业务服务：CRUD + 启停 + 引用计数删除阻断 + 批量名称回填
 */
@Service
@RequiredArgsConstructor
public class IssueTypeService {

    private final IssueTypeMapper issueTypeMapper;
    private final IssueMapper issueMapper;
    private final PermissionService permissionService;

    /**
     * 管理列表（含停用项，sort 升序，带 issueCount 引用计数）
     */
    public List<IssueTypeVO> list(String keyword, Integer enabled) {
        permissionService.requirePermission("issue:type:list");
        LambdaQueryWrapper<IssueType> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(q -> q.like(IssueType::getName, kw).or().like(IssueType::getCode, kw));
        }
        if (enabled != null) {
            wrapper.eq(IssueType::getEnabled, enabled);
        }
        wrapper.orderByAsc(IssueType::getSort).orderByAsc(IssueType::getId);
        List<IssueType> rows = issueTypeMapper.selectList(wrapper);

        // 批量引用计数（GROUP BY 一次查询，禁止 N+1）
        Map<Long, Long> countMap = countIssuesByType(
                rows.stream().map(IssueType::getId).collect(Collectors.toList()));
        return rows.stream().map(row -> {
            IssueTypeVO vo = new IssueTypeVO();
            vo.setId(row.getId());
            vo.setName(row.getName());
            vo.setCode(row.getCode());
            vo.setDescription(row.getDescription());
            vo.setSort(row.getSort());
            vo.setEnabled(row.getEnabled() != null && row.getEnabled() == 1);
            vo.setIssueCount(countMap.getOrDefault(row.getId(), 0L));
            vo.setUpdatedAt(row.getUpdatedAt());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 下拉选项。
     * <p>includeDisabled=false（默认，新建/编辑表单）只返 enabled=1；
     * true（筛选场景，Q6）返全量，启用项按 sort 原序、停用项统一置底（R14），
     * 「(已停用)」后缀由前端按 enabled 拼接。</p>
     */
    public List<IssueTypeOptionVO> options(boolean includeDisabled) {
        LambdaQueryWrapper<IssueType> wrapper = new LambdaQueryWrapper<>();
        if (!includeDisabled) {
            wrapper.eq(IssueType::getEnabled, 1);
        }
        // enabled 降序保证停用项置底，其后按 sort 升序
        wrapper.orderByDesc(IssueType::getEnabled)
                .orderByAsc(IssueType::getSort)
                .orderByAsc(IssueType::getId);
        return issueTypeMapper.selectList(wrapper).stream().map(row -> {
            IssueTypeOptionVO vo = new IssueTypeOptionVO();
            vo.setId(row.getId());
            vo.setName(row.getName());
            vo.setCode(row.getCode());
            vo.setEnabled(row.getEnabled() != null && row.getEnabled() == 1);
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 新增类型（code 在 deleted=0 范围内唯一）
     */
    @Transactional
    public Long create(IssueTypeReq req) {
        permissionService.requirePermission("issue:type:create");
        assertCodeUnique(req.getCode(), null);
        IssueType entity = new IssueType();
        entity.setName(req.getName());
        entity.setCode(req.getCode());
        entity.setDescription(req.getDescription());
        entity.setSort(req.getSort() == null ? 0 : req.getSort());
        entity.setEnabled(Boolean.FALSE.equals(req.getEnabled()) ? 0 : 1);
        issueTypeMapper.insert(entity);
        return entity.getId();
    }

    /**
     * 编辑类型
     */
    @Transactional
    public void update(Long id, IssueTypeReq req) {
        permissionService.requirePermission("issue:type:update");
        IssueType entity = requireExists(id);
        assertCodeUnique(req.getCode(), id);
        entity.setName(req.getName());
        entity.setCode(req.getCode());
        entity.setDescription(req.getDescription());
        entity.setSort(req.getSort() == null ? 0 : req.getSort());
        if (req.getEnabled() != null) {
            entity.setEnabled(req.getEnabled() ? 1 : 0);
        }
        issueTypeMapper.updateById(entity);
    }

    /**
     * 启停切换（Q6：停用不影响存量问题展示，仅新建表单不可选）
     */
    @Transactional
    public void toggleStatus(Long id, Boolean enabled) {
        permissionService.requirePermission("issue:type:update");
        IssueType entity = requireExists(id);
        entity.setEnabled(Boolean.TRUE.equals(enabled) ? 1 : 0);
        issueTypeMapper.updateById(entity);
    }

    /**
     * 删除类型：被引用（未删除问题 count>0）时抛业务异常阻断，提示改为停用；无引用则逻辑删除。
     */
    @Transactional
    public void delete(Long id) {
        permissionService.requirePermission("issue:type:delete");
        requireExists(id);
        long refCount = countIssueRef(id);
        if (refCount > 0) {
            throw new BizException(ResultCode.ISSUE_TYPE_HAS_USAGE,
                    "该类型下存在 " + refCount + " 个问题，无法删除，可改为停用");
        }
        issueTypeMapper.deleteById(id);
    }

    /**
     * 批量名称映射（供 IssueService 回填 typeName/typeCode，禁止 N+1）
     */
    public Map<Long, IssueType> nameMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return new HashMap<>();
        }
        return issueTypeMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(IssueType::getId, Function.identity(), (a, b) -> a));
    }

    /**
     * 校验类型存在且启用（新建问题 / 变更类型时调用）
     */
    public IssueType requireEnabled(Long typeId) {
        if (typeId == null) {
            throw new BizException(ResultCode.ISSUE_TYPE_NOT_FOUND, "请选择问题类型");
        }
        IssueType entity = issueTypeMapper.selectById(typeId);
        if (entity == null) {
            throw new BizException(ResultCode.ISSUE_TYPE_NOT_FOUND);
        }
        if (entity.getEnabled() == null || entity.getEnabled() != 1) {
            throw new BizException(ResultCode.ISSUE_TYPE_DISABLED);
        }
        return entity;
    }

    /** 存在性校验（deleted=0） */
    private IssueType requireExists(Long id) {
        IssueType entity = issueTypeMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ResultCode.ISSUE_TYPE_NOT_FOUND);
        }
        return entity;
    }

    /** code 唯一校验（仅查 deleted=0，excludeId 用于编辑自身豁免） */
    private void assertCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<IssueType> wrapper = new LambdaQueryWrapper<IssueType>()
                .eq(IssueType::getCode, code);
        if (excludeId != null) {
            wrapper.ne(IssueType::getId, excludeId);
        }
        if (issueTypeMapper.selectCount(wrapper) > 0) {
            throw new BizException(ResultCode.ISSUE_TYPE_CODE_DUPLICATE);
        }
    }

    /** 单类型引用计数（未删除问题） */
    private long countIssueRef(Long typeId) {
        Long count = issueMapper.selectCount(
                new LambdaQueryWrapper<Issue>().eq(Issue::getTypeId, typeId));
        return count == null ? 0L : count;
    }

    /** 批量引用计数：一次 GROUP BY 查询 */
    private Map<Long, Long> countIssuesByType(List<Long> typeIds) {
        Map<Long, Long> result = new HashMap<>();
        if (typeIds == null || typeIds.isEmpty()) {
            return result;
        }
        QueryWrapper<Issue> wrapper = new QueryWrapper<>();
        wrapper.select("type_id AS typeId", "COUNT(*) AS cnt")
                .in("type_id", typeIds)
                .groupBy("type_id");
        List<Map<String, Object>> rows = issueMapper.selectMaps(wrapper);
        for (Map<String, Object> row : rows) {
            Object typeId = row.get("typeId");
            Object cnt = row.get("cnt");
            if (typeId != null && cnt != null) {
                result.put(Long.valueOf(typeId.toString()), Long.valueOf(cnt.toString()));
            }
        }
        return result;
    }
}
