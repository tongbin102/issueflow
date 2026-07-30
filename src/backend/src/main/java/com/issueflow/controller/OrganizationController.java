package com.issueflow.controller;

import com.issueflow.common.Result;
import com.issueflow.dto.req.OrganizationReq;
import com.issueflow.dto.resp.OrganizationVO;
import com.issueflow.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织控制器：列表（支持名称/状态筛选）+ CRUD（写操作仅 ADMIN）
 */
@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    /**
     * 组织列表（ADMIN），可按名称模糊 / 状态筛选
     */
    @GetMapping
    public Result<List<OrganizationVO>> list(@RequestParam(required = false) String name,
                                             @RequestParam(required = false) Integer status) {
        return Result.success(organizationService.listAll(name, status));
    }

    /**
     * 新建组织（ADMIN）
     */
    @PostMapping
    public Result<OrganizationVO> create(@Valid @RequestBody OrganizationReq req) {
        return Result.success(organizationService.create(req));
    }

    /**
     * 编辑组织（ADMIN）
     */
    @PutMapping("/{id}")
    public Result<OrganizationVO> update(@PathVariable Long id, @Valid @RequestBody OrganizationReq req) {
        return Result.success(organizationService.update(id, req));
    }

    /**
     * 删除组织（ADMIN，有子节点禁止）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        organizationService.delete(id);
        return Result.success();
    }
}
