package com.zifang.z.oss.api.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
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

    /**
     * SPA fallback: 前端 React Router 的路径全部 forward 到 index.html。
     *
     * 缺这条时的症状很有迷惑性: 从根路径 "/" 进来一切正常(客户端 history 路由不发请求),
     * 但刷新或直链 /dashboard 会拿到 404 JSON, 白屏且控制台无报错。
     * vite dev/preview 自带 history fallback, 所以开发期永远看不见, 只在生产现形。
     *
     * 路由表抄自 _frontend 的菜单配置(z-oss-component 的 menuItems 全部挂在 /z-oss/* 下),
     * 改动那边要同步改这里。
     *
     * 注意不能写成 /** —— 那会把 /api/** 的 404 也 forward 过去, 把"接口不存在"
     * 伪造成"返回了一坨 HTML", 客户端 JSON.parse 直接炸。必须逐条列前端路由前缀。
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/z-oss/login").setViewName("forward:/index.html");
        registry.addViewController("/z-oss/**").setViewName("forward:/index.html");
        registry.addViewController("/dashboard").setViewName("forward:/index.html");
        registry.addViewController("/buckets").setViewName("forward:/index.html");
        registry.addViewController("/objects/**").setViewName("forward:/index.html");
        registry.addViewController("/users").setViewName("forward:/index.html");
    }
}