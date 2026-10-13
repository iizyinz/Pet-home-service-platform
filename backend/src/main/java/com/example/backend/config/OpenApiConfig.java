package com.example.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi 配置（Knife4j 亦可，二者均兼容 Spring Boot 3；禁用 Springfox）
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME = "Bearer JWT";

    @Bean
    public OpenAPI petServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("宠物上门服务小程序 API")
                        .description("""
                                用户 / 服务人员 / 管理员三端协同的宠物上门服务平台后端接口。
                                - 统一响应体：{ code, message, data }，code=0 成功
                                - 鉴权：除白名单外需请求头 Authorization: Bearer <JWT>
                                - 管理端接口 /api/admin/** 仅 ADMIN 角色可访问
                                - 时间格式：yyyy-MM-dd HH:mm:ss（GMT+8）；金额单位：元
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("Pet Home Service").email("dev@example.com"))
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
