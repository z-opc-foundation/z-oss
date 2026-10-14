package com.zifang.z.oss.api.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AuthenticationInterceptor authenticationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 只拦截 z-oss 自己的 /api/v1/** 路径，避免与 main-starter 中其他模块的 /api/** 路由冲突
        registry.addInterceptor(authenticationInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/user/register",
                        "/api/v1/user/login",
                        // 预签名下载的凭证是 URL 里的签名，不是 AK/SK 头。
                        // 这里若继续拦，就变成"发了预签名 URL 还得再补一对 AK/SK"，
                        // 预签名通道形同虚设（典型受害方是 <img>/<video> 这类没法带头的浏览器）。
                        "/api/v1/object/download"
                );
    }
}