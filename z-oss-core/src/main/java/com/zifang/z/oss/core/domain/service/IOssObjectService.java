package com.zifang.z.oss.core.domain.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zifang.z.oss.core.domain.entity.OssObject;
import com.zifang.z.oss.core.provider.OssPartInfo;

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
     * 打开对象的读取流（不做属主校验）。预签名下载通道用 ——
     * 那条链路的权限由签名本身覆盖，没有登录态可校验。
     *
     * @param offset 起始偏移，小于等于 0 表示从头
     * @param length 读取长度，小于 0 表示读到结尾
     */
    InputStream openObjectStream(String bucketName, String objectKey, long offset, long length);

    /**
     * 按 key 查对象，不按属主过滤。预签名下载通道用。
     *
     * @return 不存在时 null
     */
    OssObject getObjectByKey(String bucketName, String objectKey);

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

    // ==================== 分片上传 ====================

    /**
     * 开启分片上传，返回 uploadId。
     */
    String initiateMultipart(String bucketName, String objectKey, String contentType, Long userId);

    /**
     * 上传一个分片。
     *
     * @param partNumber 从 1 开始
     */
    OssPartInfo uploadPart(String bucketName, String objectKey, String uploadId, int partNumber,
                           InputStream inputStream, long size, String contentType, Long userId);

    /**
     * 归并分片成最终对象，并落库。
     *
     * @param parts 可乱序传入，服务层按 partNumber 排序
     */
    OssObject completeMultipart(String bucketName, String objectKey, String uploadId,
                                List<OssPartInfo> parts, Long userId);

    /**
     * 放弃分片上传并释放已占用空间。
     */
    void abortMultipart(String bucketName, String objectKey, String uploadId, Long userId);

    /**
     * 获取桶统计信息
     */
    Map<String, Object> getBucketStats(String bucketName, Long userId);
}