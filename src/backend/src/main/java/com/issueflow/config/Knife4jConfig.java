package com.issueflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / OpenAPI3 文档配置
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI issueFlowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("issueFlow API")
                        .description("缺陷记录与验证管理平台 接口文档")
                        .version("v1.0")
                        .contact(new Contact().name("issueFlow Team")));
    }
}
