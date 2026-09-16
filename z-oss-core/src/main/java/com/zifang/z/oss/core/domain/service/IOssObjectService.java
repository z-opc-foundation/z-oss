package com.zifang.z.oss.core.domain.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zifang.z.oss.core.domain.entity.OssObject;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 对象服务接口
 */
public interface IOssObjectService extends IService<OssObject> {

    /**
     * 上传对象
     */
    OssObject uploadObject(String bucketName, String objectKey, InputStream inputStream,
                           long size, String contentType, Long userId);

    /**
     * 下载对象
     */
    InputStream downloadObject(String bucketName, String objectKey, Long userId);

    /**
     * 删除对象
     */
    void deleteObject(String bucketName, String objectKey, Long userId);

    /**
     * 获取对象元数据
     */
    OssObject getObject(String bucketName, String objectKey, Long userId);

    /**
     * 列举对象
     */
    List<OssObject> listObjects(String bucketName, String prefix, Long userId);

    /**
     * 创建文件夹
     */
    OssObject createFolder(String bucketName, String folderKey, Long userId);

    /**
     * 复制对象
     */
    OssObject copyObject(String sourceBucketName, String sourceObjectKey,
                         String destBucketName, String destObjectKey, Long userId);

    /**
     * 批量删除对象
     */
    void batchDeleteObjects(String bucketName, List<String> objectKeys, Long userId);

    /**
     * 生成预签名URL
     */
    String generatePresignedUrl(String bucketName, String objectKey, int expires, Long userId);

    /**
     * 获取桶统计信息
     */
    Map<String, Object> getBucketStats(String bucketName, Long userId);
}