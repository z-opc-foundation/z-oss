package com.zifang.z.oss.web.config;

import com.zifang.z.oss.api.ZOssApplication;
import com.zifang.z.oss.core.OssCoreAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 *
 * <h3>【TASK-20261023-056】本类此前<b>从未</b>被自动装配（一个静默的洞）</h3>
 * <p>
 * 本类的 javadoc 一直写着「通过 {@code META-INF/spring.factories} 被 Spring Boot 自动加载」，
 * 但 {@code z-oss-api} 的 {@code META-INF/spring.factories} 里<b>只登记了</b>
 * {@code OssHostAutoConfiguration}（而且它自己还带
 * {@code @ConditionalOnProperty(prefix="z.oss.host", name="enabled", matchIfMissing=false)} ⇒ 默认关）
 * ⇒ <b>本类实际上从来没有被任何宿主加载过</b>。
 * <p>
 * 后果（在 z-opc 主壳上实测，2026-10-23）：
 * <ul>
 *   <li>{@code BucketController}（{@code /api/v1/bucket**}）、
 *       {@code ObjectController}（{@code /api/v1/**}）、
 *       {@code OssAdminController}（{@code /api/oss-admin/**}）、
 *       {@code UserController}（{@code /api/v1/user/**}）—— <b>一个都没注册</b>；</li>
 *   <li>线上 actuator 映射里 z-oss 只有两个别名面：
 *       {@code GET /api/oss/list}、{@code GET /api/oss/{bucketName}}
 *       （{@code OssAliasController}，走 {@code OssHostAutoConfiguration}）；</li>
 *   <li>生产实测 {@code GET /api/v1/bucket} ⇒ <b>HTTP 404 + JSON</b>（进了应用、没有 handler）
 *       ⇒ 「对象存储」组的 3 个菜单项（ops-056/057/058）点进去必然是断头路。</li>
 * </ul>
 * <p>
 * <b>本类的修复</b>：把它登记进 {@code spring.factories}，但<b>默认关</b> ——
 * {@code @ConditionalOnProperty(prefix = "z.oss.web", name = "enabled", havingValue = "true")}
 * （与同包 {@code OssHostAutoConfiguration} 的 {@code z.oss.host.enabled} 同一套开关风格）。
 * <p>
 * <b>为什么默认关而不是直接打开</b>：本类会连带 {@code @Import(OssCoreAutoConfiguration)}
 * 与 {@code OssModuleDataSource}（{@code @MapperScan} + 独立的 {@code dataSourceOss} /
 * {@code sqlSessionFactoryOss}）⇒ 宿主必须先有 {@code z.base.db.oss.*} 数据源配置与
 * {@code z_oss_*} 表，<b>否则启动就炸</b>（多 SqlSessionFactory / mapper 抢注册）。
 * ⇒ 开关默认关 ⇒ <b>今天的行为一个字节都不变</b>，把「装配点」从静默的洞变成显式的一步。
 * <p>
 * <b>宿主要接上这一面，需要三件事</b>（缺一不可）：
 * <ol>
 *   <li>依赖里有 {@code z-oss-api}（z-opc 主壳已有：{@code BOOT-INF/lib/z-oss-api-1.0.4.jar}）；</li>
 *   <li>配置 {@code z.oss.web.enabled=true}；</li>
 *   <li>配置 {@code z.base.db.oss.*} 数据源，且 {@code z_oss_*} 表已在位。</li>
 * </ol>
 * ⇒ 这三件事做完，{@code /api/oss-admin/**}（z-opc 侧只读管理面：whoami / buckets /
 * bucket/detail / objects）才会注册；<b>在那之前，主壳的「对象存储」菜单仍是断头路</b>。
 */
@Configuration
@ConditionalOnProperty(prefix = "z.oss.web", name = "enabled", havingValue = "true")
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
