package com.casebrief.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ExtensionDevelopmentCorsConfiguration implements WebMvcConfigurer {

    @Value("${casebrief.extension.dev-origin:}")
    private String extensionOrigin;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (extensionOrigin == null || !extensionOrigin.matches("^chrome-extension://[a-p]{32}$")) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(extensionOrigin)
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("Content-Type")
                .allowCredentials(false)
                .maxAge(300);
    }
}
