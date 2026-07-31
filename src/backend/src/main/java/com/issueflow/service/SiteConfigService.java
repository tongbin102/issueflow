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
 * 网站设置服务：读写 sys_config 的 site.* 七键。
 * <p>GET 公开（登录页要用），缺键时用默认值补齐，保证永远返回 7 键；
 * PUT 需 site:config:update 权限，主题/语言做枚举校验。</p>
 */
@Service
@RequiredArgsConstructor
public class SiteConfigService {

    private final SysConfigMapper sysConfigMapper;
    private final SysConfigService sysConfigService;
    private final PermissionService permissionService;

    /**
     * 读取全部 site.* 键（DB 优先，缺键补默认值，永远返回 7 键）
     */
    public Map<String, String> getSiteConfig() {
        Map<String, String> result = defaults();
        List<SysConfig> rows = sysConfigMapper.selectList(
                new LambdaQueryWrapper<SysConfig>().likeRight(SysConfig::getConfigKey, "site."));
        for (SysConfig row : rows) {
            if (row.getConfigValue() != null && result.containsKey(row.getConfigKey())) {
                result.put(row.getConfigKey(), row.getConfigValue());
            }
        }
        return result;
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
