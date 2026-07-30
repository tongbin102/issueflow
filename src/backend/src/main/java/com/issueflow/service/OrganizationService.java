package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.OrganizationReq;
import com.issueflow.dto.resp.OrganizationVO;
import com.issueflow.entity.Organization;
import com.issueflow.mapper.OrganizationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 组织服务：扁平列表（支持名称/状态筛选）+ CRUD。
 * <p>Phase 5：code 唯一校验、父级防环（含子孙）、回带 leaderName。</p>
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationMapper organizationMapper;
    private final PermissionService permissionService;
    private final UserService userService;

    /**
     * 全部组织（按 sort,id 升序），支持按名称模糊 / 状态筛选，前端组装树。
     * <p>命中筛选时返回命中节点及其全部祖先，保证树形表格可展示完整路径。</p>
     *
     * @param name   组织名称模糊关键字（可空）
     * @param status 状态 1/0（可空）
     */
    public List<OrganizationVO> listAll(String name, Integer status) {
        permissionService.requirePermission("organization:list");
        List<Organization> all = organizationMapper.selectList(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getDeleted, 0)
                .orderByAsc(Organization::getSort)
                .orderByAsc(Organization::getId));

        boolean hasFilter = (name != null && !name.isBlank()) || status != null;
        List<Organization> result = all;
        if (hasFilter) {
            Map<Long, Organization> byId = all.stream()
                    .collect(Collectors.toMap(Organization::getId, o -> o, (a, b) -> a));
            Set<Long> keep = new HashSet<>();
            for (Organization org : all) {
                boolean matched = true;
                if (name != null && !name.isBlank()
                        && (org.getName() == null || !org.getName().contains(name.trim()))) {
                    matched = false;
                }
                if (matched && status != null && !Objects.equals(org.getStatus(), status)) {
                    matched = false;
                }
                if (matched) {
                    // 命中节点 + 全部祖先（保证树路径完整）
                    Organization cursor = org;
                    while (cursor != null && keep.add(cursor.getId())) {
                        Long pid = cursor.getParentId();
                        cursor = (pid == null || pid == 0L) ? null : byId.get(pid);
                    }
                }
            }
            result = all.stream().filter(o -> keep.contains(o.getId())).collect(Collectors.toList());
        }

        Map<Long, String> nameMap = userService.userNameMap();
        return result.stream().map(o -> toVO(o, nameMap)).collect(Collectors.toList());
    }

    /**
     * 新建组织（code 唯一校验）
     */
    public OrganizationVO create(OrganizationReq req) {
        permissionService.requirePermission("organization:create");
        assertCodeUnique(req.getCode(), null);
        Organization org = new Organization();
        org.setName(req.getName());
        org.setCode(req.getCode() == null ? null : req.getCode().trim());
        org.setLeaderId(req.getLeaderId());
        org.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        org.setDescription(req.getDescription());
        org.setParentId(req.getParentId() == null ? 0L : req.getParentId());
        org.setSort(req.getSort() == null ? 0 : req.getSort());
        organizationMapper.insert(org);
        return toVO(org, userService.userNameMap());
    }

    /**
     * 编辑组织（code 唯一校验 + 父级防环，含子孙）
     */
    public OrganizationVO update(Long id, OrganizationReq req) {
        permissionService.requirePermission("organization:update");
        Organization exist = organizationMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "组织不存在");
        }
        if (req.getCode() != null && !req.getCode().isBlank()) {
            assertCodeUnique(req.getCode(), id);
            exist.setCode(req.getCode().trim());
        }
        if (req.getName() != null) {
            exist.setName(req.getName());
        }
        if (req.getParentId() != null) {
            assertNotSelfOrDescendant(id, req.getParentId());
            exist.setParentId(req.getParentId());
        }
        exist.setLeaderId(req.getLeaderId());
        if (req.getStatus() != null) {
            exist.setStatus(req.getStatus());
        }
        exist.setDescription(req.getDescription());
        if (req.getSort() != null) {
            exist.setSort(req.getSort());
        }
        organizationMapper.updateById(exist);
        return toVO(exist, userService.userNameMap());
    }

    /**
     * 逻辑删除组织（有子节点则禁止）
     */
    public void delete(Long id) {
        permissionService.requirePermission("organization:delete");
        Organization exist = organizationMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "组织不存在");
        }
        long childCount = organizationMapper.selectCount(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getParentId, id)
                .eq(Organization::getDeleted, 0));
        if (childCount > 0) {
            throw new BizException(ResultCode.NODE_HAS_CHILDREN);
        }
        organizationMapper.deleteById(id);
    }

    /**
     * 校验组织编码唯一（excludeId 用于编辑时排除自身）
     */
    private void assertCodeUnique(String code, Long excludeId) {
        if (code == null || code.isBlank()) {
            throw new BizException(ResultCode.VALID_ERROR, "组织编码不能为空");
        }
        LambdaQueryWrapper<Organization> wrapper = new LambdaQueryWrapper<Organization>()
                .eq(Organization::getCode, code.trim())
                .eq(Organization::getDeleted, 0);
        if (excludeId != null) {
            wrapper.ne(Organization::getId, excludeId);
        }
        if (organizationMapper.selectCount(wrapper) > 0) {
            throw new BizException(ResultCode.ORG_CODE_DUPLICATE);
        }
    }

    /**
     * 父级防环：新父级不能是自身，也不能是自身的子孙节点
     */
    private void assertNotSelfOrDescendant(Long id, Long newParentId) {
        if (newParentId == null || newParentId == 0L) {
            return;
        }
        if (Objects.equals(newParentId, id)) {
            throw new BizException(ResultCode.ORG_PARENT_CYCLE);
        }
        List<Organization> all = organizationMapper.selectList(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getDeleted, 0));
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (Organization org : all) {
            childrenMap.computeIfAbsent(org.getParentId() == null ? 0L : org.getParentId(),
                    k -> new ArrayList<>()).add(org.getId());
        }
        // BFS 收集 id 的全部子孙
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(id);
        Set<Long> descendants = new HashSet<>();
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            for (Long child : childrenMap.getOrDefault(current, List.of())) {
                if (descendants.add(child)) {
                    queue.add(child);
                }
            }
        }
        if (descendants.contains(newParentId)) {
            throw new BizException(ResultCode.ORG_PARENT_CYCLE);
        }
    }

    private OrganizationVO toVO(Organization o, Map<Long, String> userNameMap) {
        OrganizationVO vo = new OrganizationVO();
        vo.setId(o.getId());
        vo.setName(o.getName());
        vo.setCode(o.getCode());
        vo.setLeaderId(o.getLeaderId());
        vo.setLeaderName(o.getLeaderId() == null ? null : userNameMap.get(o.getLeaderId()));
        vo.setStatus(o.getStatus());
        vo.setDescription(o.getDescription());
        vo.setParentId(o.getParentId());
        vo.setSort(o.getSort());
        vo.setCreatedAt(o.getCreatedAt());
        vo.setUpdatedAt(o.getUpdatedAt());
        return vo;
    }
}
