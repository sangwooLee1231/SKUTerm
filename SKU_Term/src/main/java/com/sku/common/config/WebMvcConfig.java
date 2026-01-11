package com.sku.common.config;

import com.sku.common.interceptor.QueueAccessInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final QueueAccessInterceptor queueAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(queueAccessInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/",
                        "/member/**",
                        "/api/auth/**",
                        "/queue/**",
                        "/api/queue/**",
                        "/api/admin/**",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/favicon.ico",
                        "/static/**",
                        "/error",
                        "/login-required"
                );
    }
}