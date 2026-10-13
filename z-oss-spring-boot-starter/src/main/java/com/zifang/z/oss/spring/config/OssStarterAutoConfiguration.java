package com.zifang.z.oss.spring.config;

import com.zifang.z.oss.core.provider.OssProvider;
import com.zifang.z.oss.core.provider.OssProviderManager;
import com.zifang.z.oss.core.provider.impl.LocalOssProperties;
import com.zifang.z.oss.core.provider.impl.LocalOssProvider;
import com.zifang.z.oss.core.storage.FileStorageEngine;
import com.zifang.z.oss.core.storage.FileStorageProperties;
import com.zifang.z.oss.spring.provider.S3OssProperties;
import com.zifang.z.oss.spring.provider.S3OssProvider;
import com.zifang.z.oss.spring.template.OssTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * z-oss starter 自动装配 —— 消费方只需依赖本件，不必碰 z-oss-core。
 *
 * <p>与 {@code OssCoreAutoConfiguration} 的关系：core 那份是<b>普通 @Configuration</b>
 * （被 z-oss-api 用 {@code @Import} 引入，走用户配置阶段），本份是
 * <b>AutoConfiguration</b>，Spring 保证它在用户配置之后评估。于是本件上每一条
 * {@code @ConditionalOnMissingBean} 看到的是已经装好的 core 结果并自动退让 ——
 * 同一个进程里 core 与 starter 同时在类路径上不会产生重名 bean 冲突，
 * 只用 starter 也照样能起全套。
 *
 * <p>各平台 bean 各自带 {@code @ConditionalOnProperty}：配了哪个平台就只实例化哪个，
 * {@code oss.provider} 选谁由 {@link OssProviderManager} 决定。
 * 整体开关 {@code oss.enabled=false} 可关掉本件。
 */
@Configuration
@ConditionalOnProperty(name = "oss.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(OssProvider.class)
@EnableConfigurationProperties({FileStorageProperties.class, LocalOssProperties.class, S3OssProperties.class})
public class OssStarterAutoConfiguration {

    /**
     * 激活的平台类型。与 core 的 {@code OssCoreAutoConfiguration} 读同一个键，
     * 不另起 {@code oss.provider.*} 一套，避免两份配置各说各话。
     */
    @org.springframework.beans.factory.annotation.Value("${oss.provider:local}")
    private String activeProvider;

    private static final Logger log = LogManager.getLogger(OssStarterAutoConfiguration.class);

    // ==================== 底层存储引擎 ====================

    /**
     * core 在类路径上且已被 {@code @Import} 时，这条由 core 先注册，本条退让；
     * 只用 starter 时由本条兜底，消费方不需要额外依赖 core 的配置类。
     */
    @Bean
    @ConditionalOnMissingBean
    public FileStorageEngine fileStorageEngine(FileStorageProperties props) {
        FileStorageEngine engine = new FileStorageEngine();
        engine.setRootPath(props.getRoot());
        log.info("FileStorageEngine 初始化完成: root={}", props.getRoot());
        return engine;
    }

    // ==================== 各平台 Provider ====================

    /**
     * z-oss 自持后端（对标 MinIO standalone）。预签名 URL 的签发密钥来自
     * {@code oss.local.access-key} —— core 不知道这个键，因为它要的是一个 HTTP
     * 下载端点才用得上，属于 Web 层的配置。
     */
    @Bean
    @ConditionalOnMissingBean(name = "localOssProvider")
    @ConditionalOnProperty(name = "oss.provider", havingValue = "local", matchIfMissing = true)
    public OssProvider localOssProvider(FileStorageEngine engine, LocalOssProperties props) {
        String accessKey = props.getAccessKey();
        if (accessKey == null || accessKey.isEmpty()) {
            log.warn("oss.local.access-key 未配置, 预签名 URL 将使用内置默认密钥 —— 仅限本地开发");
        }
        log.info("注册 OssProvider: local");
        return new LocalOssProvider(engine, accessKey, props.getPublicBaseUrl());
    }

    /**
     * S3 协议后端：同一份配置同时覆盖 AWS S3 与自建 MinIO。
     * <p>{@code @ConditionalOnClass} 是必需的 —— {@code io.minio} 在本件里是
     * {@code optional}，只用 local 的消费方类路径上没有它，此时若仍让本配置生效
     * 就会在类加载时 NoClassDefFoundError，而不是安静地不装配。
     */
    @Configuration
    @ConditionalOnClass(name = "io.minio.MinioClient")
    @ConditionalOnProperty(name = "oss.provider", havingValue = "s3")
    static class S3ProviderConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "s3OssProvider")
        OssProvider s3OssProvider(S3OssProperties props) {
            log.info("注册 OssProvider: s3 (endpoint={}, region={}, bucketPrefix={})",
                    props.getEndpoint(), props.getRegion(), props.getBucketPrefix());
            return new S3OssProvider(props);
        }
    }

    // ==================== Provider 汇总 ====================

    @Bean
    @ConditionalOnMissingBean
    public OssProviderManager ossProviderManager(ObjectProvider<OssProvider> providers) {
        List<OssProvider> list = new ArrayList<>();
        providers.orderedStream().forEach(list::add);
        String active = activeProvider;
        log.info("OssProviderManager 激活类型: {}，已注册: {}", active, types(list));
        return new OssProviderManager(list, active);
    }

    // ==================== 业务门面 ====================

    @Bean
    @ConditionalOnMissingBean
    public OssTemplate ossTemplate(OssProviderManager manager) {
        log.info("OssTemplate 就绪, 激活平台={}, 可用平台={}",
                manager.getActiveType(), manager.listProviderTypes());
        return new OssTemplate(manager);
    }

    private static String types(List<OssProvider> providers) {
        List<String> t = new ArrayList<>();
        for (OssProvider p : providers) {
            t.add(p.getType());
        }
        return t.toString();
    }
}
