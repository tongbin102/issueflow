package com.issueflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.issueflow.common.BizException;
import com.issueflow.common.Constants;
import com.issueflow.common.ResultCode;
import com.issueflow.dto.req.SysConfigReq;
import com.issueflow.entity.SysConfig;
import com.issueflow.mapper.SysConfigMapper;
import com.issueflow.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 系统配置服务：主题/布局/菜单/流程开关读写
 */
@Service
@RequiredArgsConstructor
public class SysConfigService {

    private final SysConfigMapper sysConfigMapper;
    private final PermissionService permissionService;

    /**
     * 读取配置值（不存在返回 null）
     */
    public String getConfig(String key) {
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        return config == null ? null : config.getConfigValue();
    }

    /**
     * 写入配置值（存在则更新，不存在则插入）—— upsert
     */
    public void setConfig(String key, String value) {
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config == null) {
            config = new SysConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            sysConfigMapper.insert(config);
        } else {
            config.setConfigValue(value);
            sysConfigMapper.updateById(config);
        }
    }

    /**
     * 读取布尔型开关（解析失败视为 false）
     */
    public boolean isEnabled(String key) {
        return Boolean.parseBoolean(getConfig(key));
    }

    /**
     * 流程开关配置（回退 / 重开）
     */
    public Map<String, Boolean> getFlowConfig() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        map.put("rejectEnabled", isEnabled(Constants.CFG_FLOW_REJECT_ENABLED));
        map.put("reopenEnabled", isEnabled(Constants.CFG_FLOW_REOPEN_ENABLED));
        return map;
    }

    /**
     * 写入流程开关
     */
    public void setFlowConfig(Boolean rejectEnabled, Boolean reopenEnabled) {
        permissionService.requirePermission("flow:config");
        setConfig(Constants.CFG_FLOW_REJECT_ENABLED,
                String.valueOf(rejectEnabled != null && rejectEnabled));
        setConfig(Constants.CFG_FLOW_REOPEN_ENABLED,
                String.valueOf(reopenEnabled != null && reopenEnabled));
    }

    /**
     * 公开配置（任意登录用户可读取）
     */
    public Map<String, Object> getPublicConfig() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("themeColor", defaultIfNull(getConfig(Constants.CFG_THEME_COLOR), "#409EFF"));
        map.put("layout", defaultIfNull(getConfig(Constants.CFG_LAYOUT), "default"));
        map.put("menuConfig", defaultIfNull(getConfig(Constants.CFG_MENU_CONFIG), "{}"));
        map.put("flow", getFlowConfig());
        return map;
    }

    /**
     * 按 configKey 写入配置（管理员）
     */
    public void putConfig(SysConfigReq req) {
        permissionService.requirePermission("settings:update");
        if (req.getConfigKey() == null || req.getConfigKey().isBlank()) {
            throw new BizException(ResultCode.VALID_ERROR, "配置键不能为空");
        }
        setConfig(req.getConfigKey(), req.getConfigValue());
    }

    private String defaultIfNull(String value, String defaultValue) {
        return Objects.requireNonNullElse(value, defaultValue);
    }
}
