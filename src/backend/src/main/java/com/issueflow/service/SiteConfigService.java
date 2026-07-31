package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.SiteConfigReq;
import com.issueflow.entity.SysConfig;
import com.issueflow.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网站设置服务：读写 sys_config 的 site.* 配置键。
 * <p>公开 GET（登录页要用）缺键时用默认值补齐，保证永远返回 7 个展示键；
 * 管理端 GET 额外下发 site.default_password（共 8 键）；
 * PUT 需 site:config:update 权限，主题/语言做枚举校验。</p>
 */
@Service
@RequiredArgsConstructor
public class SiteConfigService {

    private final SysConfigMapper sysConfigMapper;
    private final SysConfigService sysConfigService;
    private final PermissionService permissionService;

    /**
     * 公开读取 site.* 展示键（DB 优先，缺键补默认值，永远返回 7 键）。
     * <p>Phase8 W1 #2：白名单由 {@link #defaults()} 决定，敏感键 site.default_password
     * 不在其中，因此本方法（对应公开端点 GET /api/site/config）永不下发默认密码。</p>
     */
    public Map<String, String> getSiteConfig() {
        return fillFromDb(defaults());
    }

    /**
     * 管理端读取全部 site.* 键（7 个展示键 + site.default_password，共 8 键）。
     * <p>Phase8 W1 #2：供「系统设置」页回填表单，需 site:config:update 权限。</p>
     */
    public Map<String, String> getAdminSiteConfig() {
        permissionService.requirePermission("site:config:update");
        Map<String, String> template = defaults();
        template.put(Constants.CFG_SITE_DEFAULT_PASSWORD, Constants.DEFAULT_USER_PASSWORD);
        return fillFromDb(template);
    }

    /** 用 DB 中的 site.* 行覆盖模板值（仅覆盖模板已声明的键，未声明键一律忽略） */
    private Map<String, String> fillFromDb(Map<String, String> template) {
        List<SysConfig> rows = sysConfigMapper.selectList(
                new LambdaQueryWrapper<SysConfig>().likeRight(SysConfig::getConfigKey, "site."));
        for (SysConfig row : rows) {
            if (row.getConfigValue() != null && template.containsKey(row.getConfigKey())) {
                template.put(row.getConfigKey(), row.getConfigValue());
            }
        }
        return template;
    }

    /**
     * 读取「新增用户默认密码」（DB 缺键 / 空值时回落 {@link Constants#DEFAULT_USER_PASSWORD}）。
     * <p>供后续用户新增流程复用，不经过权限校验。</p>
     */
    public String getDefaultUserPassword() {
        String value = sysConfigService.getConfig(Constants.CFG_SITE_DEFAULT_PASSWORD);
        return (value == null || value.isBlank()) ? Constants.DEFAULT_USER_PASSWORD : value;
    }

    /**
     * 批量保存七键（upsert 语义复用 SysConfigService.setConfig）
     */
    @Transactional
    public void saveSiteConfig(SiteConfigReq req) {
        permissionService.requirePermission("site:config:update");
        validateEnums(req);
        sysConfigService.setConfig(Constants.CFG_SITE_NAME, req.getName());
        sysConfigService.setConfig(Constants.CFG_SITE_SHORT_NAME, req.getShortName());
        sysConfigService.setConfig(Constants.CFG_SITE_SUBTITLE, nullToEmpty(req.getSubtitle()));
        sysConfigService.setConfig(Constants.CFG_SITE_DEFAULT_THEME, req.getDefaultTheme());
        sysConfigService.setConfig(Constants.CFG_SITE_DEFAULT_LOCALE, req.getDefaultLocale());
        sysConfigService.setConfig(Constants.CFG_SITE_COPYRIGHT, nullToEmpty(req.getCopyright()));
        sysConfigService.setConfig(Constants.CFG_SITE_ICP, nullToEmpty(req.getIcp()));
        // Phase8 W1 #2：新增用户默认密码（长度由 SiteConfigReq 的 @Size(6,32) 保证）
        sysConfigService.setConfig(Constants.CFG_SITE_DEFAULT_PASSWORD, req.getDefaultPassword());
    }

    /** 前端硬编码默认值的后端镜像（两端保持一致） */
    private Map<String, String> defaults() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(Constants.CFG_SITE_NAME, "issueFlow");
        map.put(Constants.CFG_SITE_SHORT_NAME, "IF");
        map.put(Constants.CFG_SITE_SUBTITLE, "问题跟踪与流程管理平台");
        map.put(Constants.CFG_SITE_DEFAULT_THEME, "light");
        map.put(Constants.CFG_SITE_DEFAULT_LOCALE, "zh-CN");
        map.put(Constants.CFG_SITE_COPYRIGHT, "(c) 2026 issueFlow");
        map.put(Constants.CFG_SITE_ICP, "");
        return map;
    }

    /** 主题/语言枚举校验 */
    private void validateEnums(SiteConfigReq req) {
        if (!Constants.SITE_THEMES.contains(req.getDefaultTheme())) {
            throw new BizException(ResultCode.VALID_ERROR, "默认主题仅支持 light/dark/blue/green");
        }
        if (!Constants.SITE_LOCALES.contains(req.getDefaultLocale())) {
            throw new BizException(ResultCode.VALID_ERROR, "默认语言仅支持 zh-CN/en-US");
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
