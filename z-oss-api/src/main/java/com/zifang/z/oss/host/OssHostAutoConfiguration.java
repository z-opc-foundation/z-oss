package com.zifang.z.oss.host;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 宿主侧胶水装配 (2026-10-03 自 z-opc main-starter 平移):
 * OssAliasController (/api/oss/* 前端别名面).
 *
 * <p>跟随 z.oss.host.enabled 开关 (默认关): 寄生 all-in-one 模式由宿主打开,
 * standalone z-oss 容器不开 (它自己的 ZOssApplication 组件扫描够用, 别名面是宿主前端的).
 */
@Configuration
@ConditionalOnProperty(prefix = "z.oss.host", name = "enabled", havingValue = "true", matchIfMissing = false)
@ComponentScan(basePackages = "com.zifang.z.oss.host")
public class OssHostAutoConfiguration {
}
