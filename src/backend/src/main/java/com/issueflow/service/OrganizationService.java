package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.OrganizationReq;
import com.issueflow.dto.resp.OrganizationVO;
import com.issueflow.entity.Organization;
import com.issueflow.mapper.OrganizationMapper;
import com.issueflow.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 组织服务：扁平列表 + CRUD（删除前校验无子节点）
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationMapper organizationMapper;

    /**
     * 全部组织（按 sort,id 升序），前端组装树
     */
    public List<OrganizationVO> listAll() {
        List<Organization> all = organizationMapper.selectList(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getDeleted, 0)
                .orderByAsc(Organization::getSort)
                .orderByAsc(Organization::getId));
        return all.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 新建组织
     */
    public OrganizationVO create(OrganizationReq req) {
        requireAdmin();
        if (req.getParentId() == null) {
            req.setParentId(0L);
        }
        if (req.getSort() == null) {
            req.setSort(0);
        }
        Organization org = new Organization();
        org.setName(req.getName());
        org.setParentId(req.getParentId());
        org.setSort(req.getSort());
        organizationMapper.insert(org);
        return toVO(org);
    }

    /**
     * 编辑组织（仅更新非空字段）
     */
    public OrganizationVO update(Long id, OrganizationReq req) {
        requireAdmin();
        Organization exist = organizationMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "组织不存在");
        }
        if (req.getName() != null) {
            exist.setName(req.getName());
        }
        if (req.getParentId() != null) {
            if (Objects.equals(req.getParentId(), id)) {
                throw new BizException(ResultCode.VALID_ERROR, "父级不能为自身");
            }
            exist.setParentId(req.getParentId());
        }
        if (req.getSort() != null) {
            exist.setSort(req.getSort());
        }
        organizationMapper.updateById(exist);
        return toVO(exist);
    }

    /**
     * 逻辑删除组织（有子节点则禁止）
     */
    public void delete(Long id) {
        requireAdmin();
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

    private OrganizationVO toVO(Organization o) {
        OrganizationVO vo = new OrganizationVO();
        vo.setId(o.getId());
        vo.setName(o.getName());
        vo.setParentId(o.getParentId());
        vo.setSort(o.getSort());
        vo.setCreatedAt(o.getCreatedAt());
        vo.setUpdatedAt(o.getUpdatedAt());
        return vo;
    }

    private void requireAdmin() {
        if (!Constants.ROLE_ADMIN.equals(SecurityUtils.getCurrentRoleCode())) {
            throw new BizException(ResultCode.PERMISSION_DENIED);
        }
    }
}
