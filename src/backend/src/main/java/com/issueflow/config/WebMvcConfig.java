package com.issueflow.config;

import com.issueflow.common.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Web MVC 配置：跨域 CorsConfigurationSource + 附件静态资源映射
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.attachment-base-path:/data/attachments}")
    private String attachmentBasePath;

    /**
     * 跨域配置源（被 SecurityConfig 引用）
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 附件静态资源映射：/api/attachments/static/** -> 磁盘目录
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String basePath = attachmentBasePath;
        if (!basePath.endsWith("/")) {
            basePath = basePath + "/";
        }
        registry.addResourceHandler(Constants.ATTACHMENT_STATIC_URL_PREFIX + "**")
                .addResourceLocations("file:" + basePath);
    }
}
