package com.issueflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.Result;
import com.issueflow.dto.resp.PermissionVO;
import com.issueflow.entity.Permission;
import com.issueflow.mapper.PermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 权限目录控制器（/api/permissions）：返回权限码目录，供角色授权页渲染。
 * 只读目录，登录即可访问（授权操作权限由调用方 RoleController 校验 role:assign）。
 */
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionMapper permissionMapper;

    /**
     * 权限目录（按 type、sort、id 排序）
     */
    @GetMapping
    public Result<List<PermissionVO>> list() {
        List<Permission> all = permissionMapper.selectList(new LambdaQueryWrapper<Permission>()
                .orderByAsc(Permission::getType)
                .orderByAsc(Permission::getSort)
                .orderByAsc(Permission::getId));
        return Result.success(all.stream().map(this::toVO).collect(Collectors.toList()));
    }

    private PermissionVO toVO(Permission p) {
        PermissionVO vo = new PermissionVO();
        vo.setId(p.getId());
        vo.setCode(p.getCode());
        vo.setName(p.getName());
        vo.setModule(p.getModule());
        vo.setAction(p.getAction());
        vo.setType(p.getType());
        vo.setSort(p.getSort());
        return vo;
    }
}
