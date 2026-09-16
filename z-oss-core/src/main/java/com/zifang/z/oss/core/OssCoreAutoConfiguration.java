package com.zifang.z.oss.core;

import com.zifang.z.oss.core.file.FileStorageService;
import com.zifang.z.oss.core.file.impl.FileStorageServiceImpl;
import com.zifang.z.oss.core.provider.OssProvider;
import com.zifang.z.oss.core.provider.OssProviderManager;
import com.zifang.z.oss.core.provider.impl.AliyunOssProperties;
import com.zifang.z.oss.core.provider.impl.AliyunOssProvider;
import com.zifang.z.oss.core.provider.impl.LocalOssProvider;
import com.zifang.z.oss.core.storage.FileStorageEngine;
import com.zifang.z.oss.core.storage.FileStorageProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import java.util.ArrayList;
import java.util.List;

/**
 * Z-OSS Core 自动配置（不包含 @MapperScan —— 由 web 模块的 OssModuleDataSource 负责，
 * 避免与 main-starter 多 SqlSessionFactory 场景冲突）
 * <p>
 * 同时通过 {@link ComponentScan} 注册以下子包内的 Spring 组件:
 * <ul>
 *   <li>{@code com.zifang.z.oss.core.domain.service.impl} — {@code @Service} 标注的 Oss*ServiceImpl</li>
 *   <li>{@code com.zifang.z.oss.core.init} — {@code @Component} 标注的 OssTenantInitializer</li>
 *   <li>{@code com.zifang.z.oss.core.provider.impl} — {@code @Component} 标注的 Provider 实现</li>
 * </ul>
 * 通过 {@code excludeFilters} 显式排除 {@code com.zifang.z.oss.core.domain.mapper.*} Mapper 接口,
 * 避免本组件扫描抢在 {@code OssModuleDataSource}{@code @MapperScan} 之前以默认 SqlSessionFactory 注册同名 mapper bean.
 */
@Configuration
@ComponentScan(
        basePackages = {
                "com.zifang.z.oss.core.domain.service.impl",
                "com.zifang.z.oss.core.init",
                "com.zifang.z.oss.core.provider.impl"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.zifang\\.z\\.oss\\.core\\.domain\\.mapper\\..*"
        )
)
@EnableConfigurationProperties({FileStorageProperties.class, AliyunOssProperties.class})
public class OssCoreAutoConfiguration {

    private static final Logger log = LogManager.getLogger(OssCoreAutoConfiguration.class);

    @Value("${oss.provider:local}")
    private String activeProviderType;

    // ==================== 底层存储引擎 ====================

    @Bean
    @ConditionalOnMissingBean
    public FileStorageEngine fileStorageEngine(FileStorageProperties props) {
        FileStorageEngine engine = new FileStorageEngine();
        engine.setRootPath(props.getRoot());
        log.info("FileStorageEngine 初始化完成: root={}", props.getRoot());
        return engine;
    }

    // ==================== Local Provider（默认） ====================

    @Bean
    @ConditionalOnMissingBean(name = "localOssProvider")
    @ConditionalOnProperty(name = "oss.provider", havingValue = "local", matchIfMissing = true)
    public OssProvider localOssProvider(FileStorageEngine engine) {
        log.info("注册 OssProvider: local");
        return new LocalOssProvider(engine);
    }

    // ==================== Aliyun Provider ====================

    @Bean
    @ConditionalOnMissingBean(name = "aliyunOssProvider")
    @ConditionalOnProperty(name = "oss.provider", havingValue = "aliyun")
    public OssProvider aliyunOssProvider(AliyunOssProperties props) {
        log.info("注册 OssProvider: aliyun (endpoint={}, prefix={})",
                props.getEndpoint(), props.getBucketPrefix());
        return new AliyunOssProvider(props);
    }

    // ==================== Provider 门面 ====================

    @Bean
    @ConditionalOnMissingBean
    public OssProviderManager ossProviderManager(List<OssProvider> providers) {
        log.info("OssProviderManager 激活类型: {}，已注册: {}",
                activeProviderType,
                providers == null ? "[]" : providerTypes(providers));
        return new OssProviderManager(
                providers == null ? new ArrayList<>() : providers,
                activeProviderType);
    }

    // ==================== FileStorageService ====================

    @Bean(name = "fileStorageService")
    @ConditionalOnMissingBean(name = "fileStorageService")
    public FileStorageService fileStorageService(OssProviderManager manager,
                                                  FileStorageProperties props) {
        log.info("FileStorageService 初始化: bucket={}, publicBaseUrl={}",
                props.getDefaultBucket(), props.getPublicBaseUrl());
        return new FileStorageServiceImpl(manager, props);
    }

    private String providerTypes(List<OssProvider> providers) {
        List<String> types = new ArrayList<>();
        for (OssProvider p : providers) {
            types.add(p.getType());
        }
        return types.toString();
    }
}
