package com.issueflow.controller;

import com.issueflow.common.Result;
import com.issueflow.dto.req.SiteConfigReq;
import com.issueflow.service.SiteConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 网站设置控制器
 * <p>GET /api/site/config 在 SecurityConfig.WHITE_LIST 中公开（登录页需读站点名）；
 * PUT /api/admin/site/config 走另一路径前缀，需登录 + site:config:update 权限。</p>
 */
@RestController
@RequiredArgsConstructor
public class SiteConfigController {

    private final SiteConfigService siteConfigService;

    /**
     * 公开读取全部 site.* 配置（缺键补默认值，永远 7 键）
     */
    @GetMapping("/api/site/config")
    public Result<Map<String, String>> get() {
        return Result.success(siteConfigService.getSiteConfig());
    }

    /**
     * 管理端读取全部 site.* 配置（含敏感键 site.default_password，共 8 键）
     * <p>Phase8 W1 #2：需登录 + site:config:update 权限，供「系统设置」页回填表单。</p>
     */
    @GetMapping("/api/admin/site/config")
    public Result<Map<String, String>> getForAdmin() {
        return Result.success(siteConfigService.getAdminSiteConfig());
    }

    /**
     * 管理端保存网站设置（site:config:update）
     */
    @PutMapping("/api/admin/site/config")
    public Result<Void> put(@Valid @RequestBody SiteConfigReq req) {
        siteConfigService.saveSiteConfig(req);
        return Result.success();
    }
}
