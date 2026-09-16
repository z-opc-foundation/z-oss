package com.zifang.z.oss.web.config;

import com.zifang.z.oss.api.ZOssApplication;
import com.zifang.z.oss.core.OssCoreAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

/**
 * z-oss Web 模块自动配置入口
 * <p>
 * 通过 {@code META-INF/spring.factories} 被 Spring Boot 自动加载，扫描 web 端 controller / config / dto / vo。
 * main-starter 不需要在 ComponentScan 中列出 z-oss 包路径。
 * <p>
 * 注意 1: 不要在本类上加 {@code @MapperScan} —— mapper 注册由
 * {@link com.zifang.z.oss.api.config.OssModuleDataSource} 唯一负责;
 * 重复声明会导致 ClassPathMapperScanner 对同名 mapper bean 二次扫描并触发 WARN:
 * "Skipping MapperFactoryBean with name 'ossBucketMapper' ... already defined"。
 * <p>
 * 注意 2: 不要把 {@code @ComponentScan(basePackages = "com.zifang.z.oss")} 写宽而吞没
 * {@code com.zifang.z.oss.core.*} 子包 —— 该子包下的 {@code Mapper} 接口带
 * {@code @org.apache.ibatis.annotations.Mapper} 注解，会被 Spring 当作 {@code @Component} 直接注册,
 * 且走默认 {@code SqlSessionFactory} 注入 (而非 oss 专属的 sqlSessionFactoryOss),
 * 触发 "expected single matching bean but found 16" 启动失败.
 * 仅扫描 api + web 两个子包才能保证 mapper 只走 OssModuleDataSource 的 @MapperScan 通道.
 * <p>
 * 注意 3: {@code com.zifang.z.oss.api} 根包下还存在一个用于独立启动的 demo 类
 * {@link ZOssApplication}, 它自身携带 {@code @MapperScan} + {@code @ComponentScan(basePackages = "com.zifang.z.oss")}.
 * 若被本类扫到并注册为 bean, 它的 @MapperScan 会先于 OssModuleDataSource 抢先注册同名 mapper
 * (绑定默认 SqlSessionFactory), 触发 ClassPathMapperScanner "Bean already defined with the same name" 警告,
 * 进而导致 mapper 在注入 SqlSessionFactory 时撞上多 bean 失败.
 * 用 {@code excludeFilters} 显式排除该 demo 类, 保证 main-starter 模式下只走 OssModuleDataSource 这一条注册通道.
 */
@Configuration
@Import(OssCoreAutoConfiguration.class)
@ComponentScan(
        basePackages = {
                "com.zifang.z.oss.api",
                "com.zifang.z.oss.web",
                // 显式扫描 OssUserServiceImpl 等 @Service 实现 — 之前只 @Import OssCoreAutoConfiguration
                // 但 OssCoreAutoConfiguration 嵌套的 @ComponentScan 在 z-oss-core 模块里没被 Spring 触发
                // 导致 IOssUserService bean 找不到. 直接扫这里最稳.
                "com.zifang.z.oss.core.domain.service.impl"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = ZOssApplication.class
        )
)
public class OssWebAutoConfiguration {
}
