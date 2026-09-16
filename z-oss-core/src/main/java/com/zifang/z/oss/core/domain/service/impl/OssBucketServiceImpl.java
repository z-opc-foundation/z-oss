package com.zifang.z.oss.core.domain.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zifang.z.oss.common.enums.BucketAcl;
import com.zifang.z.oss.common.exception.BucketException;
import com.zifang.z.oss.core.domain.entity.OssBucket;
import com.zifang.z.oss.core.domain.mapper.OssBucketMapper;
import com.zifang.z.oss.core.domain.service.IOssBucketService;
import com.zifang.z.oss.core.provider.OssProvider;
import com.zifang.z.oss.core.provider.OssProviderManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 桶服务实现（基于 OssProvider）
 */
@Service
public class OssBucketServiceImpl extends ServiceImpl<OssBucketMapper, OssBucket> implements IOssBucketService {

    private static final Logger log = LogManager.getLogger(OssBucketServiceImpl.class);

    private final OssProviderManager providerManager;

    public OssBucketServiceImpl(OssProviderManager providerManager) {
        this.providerManager = providerManager;
    }

    @Override
    public OssBucket createBucket(String name, Long userId) {
        validateBucketName(name);

        // 业务侧查重
        OssBucket existBucket = this.getOne(new LambdaQueryWrapper<OssBucket>()
                .eq(OssBucket::getName, name)
                .eq(OssBucket::getUserId, userId));
        if (existBucket != null) {
            throw new BucketException("Bucket already exists: " + name);
        }

        // 物理层创建桶
        OssProvider provider = providerManager.provider();
        try {
            provider.createBucket(name, "default", BucketAcl.PRIVATE.getCode());
        } catch (IOException e) {
            log.error("Provider 创建桶失败 [provider={}]: {}", provider.getType(), e.getMessage());
            throw new BucketException("创建桶失败: " + e.getMessage());
        }

        // 写 DB
        OssBucket bucket = new OssBucket();
        bucket.setName(name);
        bucket.setUserId(userId);
        bucket.setRegion("default");
        bucket.setAcl(BucketAcl.PRIVATE.getCode());
        this.save(bucket);

        return bucket;
    }

    @Override
    public void deleteBucket(String name, Long userId) {
        OssBucket bucket = validateBucket(name, userId);

        // 物理层删除（Provider 内部会校验是否非空）
        OssProvider provider = providerManager.provider();
        try {
            provider.deleteBucket(name);
        } catch (IOException e) {
            log.error("Provider 删除桶失败 [provider={}]: {}", provider.getType(), e.getMessage());
            throw new BucketException("删除桶失败: " + e.getMessage());
        }

        this.removeById(bucket.getId());
    }

    @Override
    public List<OssBucket> listUserBuckets(Long userId) {
        return this.list(new LambdaQueryWrapper<OssBucket>()
                .eq(OssBucket::getUserId, userId));
    }

    @Override
    public OssBucket validateBucket(String name, Long userId) {
        OssBucket bucket = this.getOne(new LambdaQueryWrapper<OssBucket>()
                .eq(OssBucket::getName, name)
                .eq(OssBucket::getUserId, userId));
        if (bucket == null) {
            throw new BucketException("Bucket not found: " + name);
        }
        return bucket;
    }

    @Override
    public OssBucket updateBucket(String name, Long userId, String acl, String region, String policy) {
        OssBucket bucket = validateBucket(name, userId);

        if (acl != null) {
            // 同步 Provider 端 ACL
            try {
                providerManager.provider().setBucketAcl(name, acl);
            } catch (Exception e) {
                log.error("Provider 设置 ACL 失败: {}", e.getMessage());
                throw new BucketException("设置 ACL 失败: " + e.getMessage());
            }
            bucket.setAcl(acl);
        }
        if (region != null) {
            bucket.setRegion(region);
        }
        if (policy != null) {
            bucket.setPolicy(policy);
        }

        this.updateById(bucket);
        return bucket;
    }

    @Override
    public Map<String, Object> getBucketStats(String name, Long userId) {
        OssBucket bucket = validateBucket(name, userId);
        Map<String, Object> stats = new HashMap<>();
        stats.put("bucketName", bucket.getName());
        stats.put("region", bucket.getRegion());
        stats.put("acl", bucket.getAcl());
        stats.put("createTime", bucket.getCreateTime());
        // 物理层是否存在
        try {
            stats.put("physicalExists", providerManager.provider().doesBucketExist(name));
            stats.put("activeProvider", providerManager.getActiveType());
        } catch (Exception e) {
            stats.put("physicalExists", false);
        }
        return stats;
    }

    private void validateBucketName(String name) {
        if (name == null || !name.matches("^[a-z0-9][a-z0-9-]{2,62}[a-z0-9]$")) {
            throw new BucketException("Invalid bucket name: " + name);
        }
    }
}
