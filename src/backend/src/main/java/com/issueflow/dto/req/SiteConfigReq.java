package com.issueflow.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 网站设置保存请求（site.* 七键一次提交）
 */
@Data
public class SiteConfigReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 站点名称 site.name */
    @NotBlank(message = "站点名称不能为空")
    @Size(max = 50, message = "站点名称不能超过 50 字")
    private String name;

    /** 站点简称 site.short_name */
    @NotBlank(message = "站点简称不能为空")
    @Size(max = 8, message = "站点简称不能超过 8 字")
    private String shortName;

    /** 副标题 site.subtitle */
    @Size(max = 100, message = "副标题不能超过 100 字")
    private String subtitle;

    /** 前台默认主题 site.default_theme ∈ {light,dark,blue,green} */
    @NotBlank(message = "默认主题不能为空")
    private String defaultTheme;

    /** 默认语言 site.default_locale ∈ {zh-CN,en-US} */
    @NotBlank(message = "默认语言不能为空")
    private String defaultLocale;

    /** 版权信息 site.copyright */
    @Size(max = 100, message = "版权信息不能超过 100 字")
    private String copyright;

    /** ICP 备案号 site.icp */
    @Size(max = 50, message = "备案号不能超过 50 字")
    private String icp;
}
