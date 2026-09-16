package com.zifang.z.oss.core.domain.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zifang.z.oss.core.domain.entity.OssBucket;

import java.util.List;
import java.util.Map;

/**
 * 桶服务接口
 */
public interface IOssBucketService extends IService<OssBucket> {

    /**
     * 创建桶
     */
    OssBucket createBucket(String name, Long userId);

    /**
     * 删除桶
     */
    void deleteBucket(String name, Long userId);

    /**
     * 获取用户的桶列表
     */
    List<OssBucket> listUserBuckets(Long userId);

    /**
     * 验证桶归属
     */
    OssBucket validateBucket(String name, Long userId);

    /**
     * 更新桶
     */
    OssBucket updateBucket(String name, Long userId, String acl, String region, String policy);

    /**
     * 获取桶统计信息
     */
    Map<String, Object> getBucketStats(String name, Long userId);
}