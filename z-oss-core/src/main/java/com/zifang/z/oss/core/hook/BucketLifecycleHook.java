package com.zifang.z.oss.core.hook;

import com.zifang.z.oss.core.domain.entity.OssBucket;

/**
 * OSS 存储桶生命周期钩子.
 *
 * <p>在桶创建/删除前后回调, 用于业务层注入额外逻辑 (如权限初始化、日志审计).
 *
 * <p>z-oss-core 不依赖任何业务模块, 通过此钩子让调用方扩展桶生命周期行为.
 */
public interface BucketLifecycleHook {

    /**
     * 桶创建前回调.
     *
     * @param bucketName 桶名称
     * @param userId     创建者用户 ID
     */
    default void beforeCreate(String bucketName, Long userId) {
        // 默认空实现
    }

    /**
     * 桶创建后回调.
     *
     * @param bucket 新创建的桶实体
     */
    default void afterCreate(OssBucket bucket) {
        // 默认空实现
    }

    /**
     * 桶删除前回调.
     *
     * @param bucket 待删除的桶实体
     */
    default void beforeDelete(OssBucket bucket) {
        // 默认空实现
    }

    /**
     * 桶删除后回调.
     *
     * @param bucketName 已删除的桶名称
     */
    default void afterDelete(String bucketName) {
        // 默认空实现
    }
}
