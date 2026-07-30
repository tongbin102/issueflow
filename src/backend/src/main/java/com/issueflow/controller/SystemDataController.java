package com.issueflow.controller;

import com.issueflow.common.Result;
import com.issueflow.dto.req.DataResetReq;
import com.issueflow.service.SystemDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统数据控制器（R7）：数据初始化（仅 ADMIN + system:reset）
 */
@RestController
@RequestMapping("/api/system/data")
@RequiredArgsConstructor
public class SystemDataController {

    private final SystemDataService systemDataService;

    /**
     * 数据初始化：清空业务数据，保留权限/菜单/配置/流程定义与 admin 账号
     *
     * @return 各表清理条数
     */
    @PostMapping("/reset")
    public Result<Map<String, Integer>> reset(@Valid @RequestBody DataResetReq req) {
        return Result.success(systemDataService.resetData(req.getConfirmText()));
    }
}
