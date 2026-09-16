package com.zifang.z.oss.core.hook;

/**
 * OSS 模块初始化钩子.
 *
 * <p>在 Spring 应用启动完成后回调, 用于执行 OSS 相关的初始化逻辑
 * (如创建默认桶、初始化存储配置等).
 *
 * <p>典型使用场景:
 * <ul>
 *   <li>应用启动时检查并创建默认存储桶</li>
 *   <li>初始化 OSS provider 配置</li>
 *   <li>执行数据迁移脚本</li>
 * </ul>
 */
public interface OssInitializer {

    /**
     * 初始化顺序, 数值越小越先执行.
     */
    default int order() {
        return 100;
    }

    /**
     * 执行初始化.
     *
     * @throws Exception 初始化失败时抛出
     */
    void initialize() throws Exception;
}
