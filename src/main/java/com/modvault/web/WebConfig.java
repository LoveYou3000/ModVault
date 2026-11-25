package com.modvault.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final DataPlaneAuthInterceptor dataPlaneAuth;
    private final AdminAuthInterceptor adminAuth;

    public WebConfig(DataPlaneAuthInterceptor dataPlaneAuth, AdminAuthInterceptor adminAuth) {
        this.dataPlaneAuth = dataPlaneAuth;
        this.adminAuth = adminAuth;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(dataPlaneAuth).addPathPatterns("/v1/messages/**");
        registry.addInterceptor(adminAuth).addPathPatterns("/admin/**");
    }
}
