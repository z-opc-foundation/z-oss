package com.zifang.z.oss.core.storage;

import java.io.InputStream;

/**
 * 存储引擎接口
 */
public interface StorageEngine {

    /**
     * 存储对象
     *
     * @param bucket      桶名
     * @param objectKey   对象键
     * @param inputStream 输入流
     * @param size        大小
     * @return ETag
     */
    String store(String bucket, String objectKey, InputStream inputStream, long size);

    /**
     * 读取对象
     */
    InputStream read(String bucket, String objectKey);

    /**
     * 删除对象
     */
    void delete(String bucket, String objectKey);

    /**
     * 判断对象是否存在
     */
    boolean exists(String bucket, String objectKey);

    /**
     * 获取对象大小
     */
    long getSize(String bucket, String objectKey);
}