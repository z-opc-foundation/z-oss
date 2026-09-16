package com.zifang.z.oss.core.hook;

/**
 * OSS 租户初始化钩子.
 *
 * <p>z-oss-core 不依赖任何业务模块 (如 z-ctc), 通过此钩子让调用方
 * 注入租户创建时的 OSS 初始化逻辑 (如创建默认存储桶).
 *
 * <p>使用方式:
 * <pre>{@code
 * @Component
 * public class MyOssTenantHook implements OssTenantHook {
 *     @Override
 *     public void onTenantCreated(String tenantCode, Long ownerUserId) {
 *         // 创建默认存储桶
 *         bucketService.createBucket(tenantCode + "-default", ownerUserId);
 *     }
 * }
 * }</pre>
 *
 * <p>多个实现可通过 {@link #order()} 控制执行顺序, 数值越小越先执行.
 */
public interface OssTenantHook {

    /**
     * 模块名称, 用于日志标识.
     */
    default String moduleName() {
        return "z-oss";
    }

    /**
     * 执行顺序, 数值越小越先执行.
     */
    default int order() {
        return 100;
    }

    /**
     * 租户创建时回调.
     *
     * @param tenantCode  租户编码
     * @param ownerUserId 租户创建者用户 ID (可能为 null, 表示管理员手工创建)
     */
    void onTenantCreated(String tenantCode, Long ownerUserId);
}
