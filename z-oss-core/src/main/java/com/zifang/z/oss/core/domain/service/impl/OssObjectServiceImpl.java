package com.zifang.z.oss.core.domain.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zifang.z.oss.common.exception.ObjectException;
import com.zifang.z.oss.core.domain.entity.OssBucket;
import com.zifang.z.oss.core.domain.entity.OssObject;
import com.zifang.z.oss.core.domain.mapper.OssObjectMapper;
import com.zifang.z.oss.core.domain.service.IOssBucketService;
import com.zifang.z.oss.core.domain.service.IOssObjectService;
import com.zifang.z.oss.core.provider.OssObjectSummary;
import com.zifang.z.oss.core.provider.OssProvider;
import com.zifang.z.oss.core.provider.OssProviderManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对象服务实现（基于 OssProvider）
 */
@Service
public class OssObjectServiceImpl extends ServiceImpl<OssObjectMapper, OssObject> implements IOssObjectService {

    private static final Logger log = LogManager.getLogger(OssObjectServiceImpl.class);

    private final IOssBucketService bucketService;
    private final OssProviderManager providerManager;

    public OssObjectServiceImpl(IOssBucketService bucketService, OssProviderManager providerManager) {
        this.bucketService = bucketService;
        this.providerManager = providerManager;
    }

    @Override
    public OssObject uploadObject(String bucketName, String objectKey, InputStream inputStream,
                                  long size, String contentType, Long userId) {
        OssBucket bucket = bucketService.validateBucket(bucketName, userId);
        OssProvider provider = providerManager.provider();

        OssObjectSummary summary;
        try {
            summary = provider.putObject(bucketName, objectKey, inputStream, size, contentType);
        } catch (IOException e) {
            log.error("Provider 上传失败 [provider={}]: {}", provider.getType(), e.getMessage());
            throw new ObjectException("上传失败: " + e.getMessage());
        }

        OssObject object = new OssObject();
        object.setBucketId(bucket.getId());
        object.setBucketName(bucketName);
        object.setObjectKey(objectKey);
        object.setObjectName(getObjectName(objectKey));
        object.setContentType(contentType);
        object.setContentLength(summary.getSize() != null ? summary.getSize() : size);
        object.setEtag(summary.getEtag());
        object.setStoragePath(bucketName + "/" + objectKey);
        object.setUserId(userId);
        object.setIsFolder(objectKey.endsWith("/") ? 1 : 0);
        this.save(object);

        return object;
    }

    @Override
    public InputStream downloadObject(String bucketName, String objectKey, Long userId) {
        bucketService.validateBucket(bucketName, userId);
        OssObject object = getObject(bucketName, objectKey, userId);
        if (object == null) {
            throw new ObjectException("Object not found: " + objectKey);
        }
        try {
            return providerManager.provider().getObject(bucketName, objectKey);
        } catch (IOException e) {
            throw new ObjectException("下载失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteObject(String bucketName, String objectKey, Long userId) {
        bucketService.validateBucket(bucketName, userId);
        OssObject object = getObject(bucketName, objectKey, userId);
        if (object != null) {
            try {
                providerManager.provider().deleteObject(bucketName, objectKey);
            } catch (IOException e) {
                log.error("Provider 删除对象失败: {}", e.getMessage());
                throw new ObjectException("删除失败: " + e.getMessage());
            }
            this.removeById(object.getId());
        }
    }

    @Override
    public OssObject getObject(String bucketName, String objectKey, Long userId) {
        return this.getOne(new LambdaQueryWrapper<OssObject>()
                .eq(OssObject::getBucketName, bucketName)
                .eq(OssObject::getObjectKey, objectKey)
                .eq(OssObject::getUserId, userId));
    }

    @Override
    public List<OssObject> listObjects(String bucketName, String prefix, Long userId) {
        bucketService.validateBucket(bucketName, userId);

        LambdaQueryWrapper<OssObject> wrapper = new LambdaQueryWrapper<OssObject>()
                .eq(OssObject::getBucketName, bucketName)
                .eq(OssObject::getUserId, userId);

        if (prefix != null && !prefix.isEmpty()) {
            wrapper.likeRight(OssObject::getObjectKey, prefix);
        }

        wrapper.orderByAsc(OssObject::getObjectKey);
        return this.list(wrapper);
    }

    @Override
    public OssObject createFolder(String bucketName, String folderKey, Long userId) {
        OssBucket bucket = bucketService.validateBucket(bucketName, userId);

        if (!folderKey.endsWith("/")) {
            folderKey = folderKey + "/";
        }

        OssObject existObject = getObject(bucketName, folderKey, userId);
        if (existObject != null) {
            return existObject;
        }

        // 物理层建文件夹
        try {
            // 文件夹通常是 0 字节占位对象，contentType 留空避免触发特殊处理
            providerManager.provider().putObject(bucketName, folderKey,
                    new java.io.ByteArrayInputStream(new byte[0]), 0, "application/x-directory");
        } catch (IOException e) {
            log.warn("Provider 建文件夹失败: {}", e.getMessage());
        }

        OssObject object = new OssObject();
        object.setBucketId(bucket.getId());
        object.setBucketName(bucketName);
        object.setObjectKey(folderKey);
        object.setObjectName(folderKey);
        object.setContentType("application/directory");
        object.setContentLength(0L);
        object.setEtag("");
        object.setStoragePath(bucketName + "/" + folderKey);
        object.setUserId(userId);
        object.setIsFolder(1);
        this.save(object);

        return object;
    }

    @Override
    public OssObject copyObject(String sourceBucketName, String sourceObjectKey,
                                String destBucketName, String destObjectKey, Long userId) {
        bucketService.validateBucket(sourceBucketName, userId);
        bucketService.validateBucket(destBucketName, userId);

        OssObject sourceObject = getObject(sourceBucketName, sourceObjectKey, userId);
        if (sourceObject == null) {
            throw new ObjectException("Source object not found: " + sourceObjectKey);
        }

        OssProvider provider = providerManager.provider();
        OssObjectSummary summary;
        try (InputStream in = provider.getObject(sourceBucketName, sourceObjectKey)) {
            summary = provider.putObject(destBucketName, destObjectKey, in,
                    sourceObject.getContentLength(), sourceObject.getContentType());
        } catch (IOException e) {
            throw new ObjectException("复制失败: " + e.getMessage());
        }

        OssBucket destBucket = bucketService.validateBucket(destBucketName, userId);
        OssObject destObject = new OssObject();
        destObject.setBucketId(destBucket.getId());
        destObject.setBucketName(destBucketName);
        destObject.setObjectKey(destObjectKey);
        destObject.setObjectName(getObjectName(destObjectKey));
        destObject.setContentType(sourceObject.getContentType());
        destObject.setContentLength(summary.getSize() != null ? summary.getSize() : sourceObject.getContentLength());
        destObject.setEtag(summary.getEtag());
        destObject.setStoragePath(destBucketName + "/" + destObjectKey);
        destObject.setUserId(userId);
        destObject.setIsFolder(0);
        this.save(destObject);

        return destObject;
    }

    @Override
    public void batchDeleteObjects(String bucketName, List<String> objectKeys, Long userId) {
        bucketService.validateBucket(bucketName, userId);
        for (String objectKey : objectKeys) {
            deleteObject(bucketName, objectKey, userId);
        }
    }

    @Override
    public String generatePresignedUrl(String bucketName, String objectKey, int expires, Long userId) {
        bucketService.validateBucket(bucketName, userId);
        OssObject object = getObject(bucketName, objectKey, userId);
        if (object == null) {
            throw new ObjectException("Object not found: " + objectKey);
        }
        return providerManager.provider().generatePresignedUrl(bucketName, objectKey, expires);
    }

    @Override
    public Map<String, Object> getBucketStats(String bucketName, Long userId) {
        bucketService.validateBucket(bucketName, userId);

        long objectCount = this.count(new LambdaQueryWrapper<OssObject>()
                .eq(OssObject::getBucketName, bucketName)
                .eq(OssObject::getUserId, userId)
                .eq(OssObject::getIsFolder, Integer.valueOf(0)));

        List<OssObject> objects = this.list(new LambdaQueryWrapper<OssObject>()
                .eq(OssObject::getBucketName, bucketName)
                .eq(OssObject::getUserId, userId)
                .eq(OssObject::getIsFolder, (Integer) 0));

        long totalSize = 0;
        for (OssObject obj : objects) {
            if (obj.getContentLength() != null) {
                totalSize += obj.getContentLength();
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("bucketName", bucketName);
        stats.put("objectCount", objectCount);
        stats.put("totalSize", totalSize);
        stats.put("activeProvider", providerManager.getActiveType());
        return stats;
    }

    private String getObjectName(String objectKey) {
        int lastSlash = objectKey.lastIndexOf('/');
        return lastSlash >= 0 ? objectKey.substring(lastSlash + 1) : objectKey;
    }
}
