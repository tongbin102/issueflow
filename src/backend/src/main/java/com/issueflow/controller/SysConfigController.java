package com.issueflow.controller;

import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.Result;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.SysConfigReq;
import com.issueflow.service.SysConfigService;
import com.issueflow.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统配置控制器：主题/布局/菜单/流程配置读写
 */
@RestController
@RequestMapping("/api/sys")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService sysConfigService;

    /**
     * 读取公开配置（主题/布局/菜单/流程，任意登录用户）
     */
    @GetMapping("/config")
    public Result<Map<String, Object>> get() {
        return Result.success(sysConfigService.getPublicConfig());
    }

    /**
     * 按 configKey 写入配置（ADMIN）
     */
    @PutMapping("/config")
    public Result<Void> put(@Valid @RequestBody SysConfigReq req) {
        if (!Constants.ROLE_ADMIN.equals(SecurityUtils.getCurrentRoleCode())) {
            throw new BizException(ResultCode.PERMISSION_DENIED);
        }
        sysConfigService.putConfig(req);
        return Result.success();
    }
}
