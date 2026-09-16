package com.zifang.z.oss.core.provider.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.*;
import com.zifang.z.oss.common.enums.BucketAcl;
import com.zifang.z.oss.common.exception.OssException;
import com.zifang.z.oss.core.provider.OssObjectSummary;
import com.zifang.z.oss.core.provider.OssProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 阿里云 OSS Provider
 * <p>
 * 依赖：{@code com.aliyun.oss:aliyun-sdk-oss:3.17.4+}
 */
public class AliyunOssProvider implements OssProvider {

    private static final Logger log = LogManager.getLogger(AliyunOssProvider.class);

    private final AliyunOssProperties props;
    private final OSS client;

    public AliyunOssProvider(AliyunOssProperties props) {
        this.props = props;
        if (props.getAccessKeyId() == null || props.getAccessKeyId().isEmpty()
                || props.getAccessKeySecret() == null || props.getAccessKeySecret().isEmpty()
                || props.getEndpoint() == null || props.getEndpoint().isEmpty()) {
            log.warn("AliyunOssProvider: endpoint / access-key-id / access-key-secret 未配置，Provider 不可用");
            this.client = null;
        } else {
            this.client = new OSSClientBuilder().build(
                    props.getEndpoint(),
                    props.getAccessKeyId(),
                    props.getAccessKeySecret(),
                    props.getSecurityToken());
            log.info("AliyunOssProvider 初始化完成: endpoint={}, prefix={}",
                    props.getEndpoint(), props.getBucketPrefix());
        }
    }

    @Override
    public String getType() {
        return "aliyun";
    }

    private OSS client() {
        if (client == null) {
            throw new OssException("Aliyun OSS 客户端未初始化，请检查 oss.aliyun.* 配置");
        }
        return client;
    }

    // ==================== 对象操作 ====================

    @Override
    public OssObjectSummary putObject(String bucket, String key, InputStream inputStream,
                                      long size, String contentType) throws IOException {
        String realBucket = props.resolveBucketName(bucket);
        com.aliyun.oss.model.ObjectMetadata meta = new com.aliyun.oss.model.ObjectMetadata();
        if (contentType != null) {
            meta.setContentType(contentType);
        }
        if (size > 0) {
            meta.setContentLength(size);
        }
        try {
            client().putObject(realBucket, key, inputStream, meta);
        } catch (Exception e) {
            throw new IOException("Aliyun putObject failed: " + e.getMessage(), e);
        }
        return statObject(bucket, key);
    }

    @Override
    public InputStream getObject(String bucket, String key) throws IOException {
        String realBucket = props.resolveBucketName(bucket);
        try {
            OSSObject obj = client().getObject(realBucket, key);
            return obj.getObjectContent();
        } catch (Exception e) {
            throw new IOException("Aliyun getObject failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteObject(String bucket, String key) throws IOException {
        String realBucket = props.resolveBucketName(bucket);
        try {
            // 文件夹需要先列再删
            if (key.endsWith("/")) {
                ObjectListing listing = client().listObjects(realBucket, key);
                for (OSSObjectSummary s : listing.getObjectSummaries()) {
                    client().deleteObject(realBucket, s.getKey());
                }
            } else {
                client().deleteObject(realBucket, key);
            }
        } catch (Exception e) {
            throw new IOException("Aliyun deleteObject failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean doesObjectExist(String bucket, String key) {
        String realBucket = props.resolveBucketName(bucket);
        try {
            return client().doesObjectExist(realBucket, key);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public OssObjectSummary statObject(String bucket, String key) {
        String realBucket = props.resolveBucketName(bucket);
        try {
            com.aliyun.oss.model.ObjectMetadata meta = client().getObjectMetadata(realBucket, key);
            return new OssObjectSummary(key, meta.getContentLength(), meta.getETag(),
                    meta.getContentType(), key.endsWith("/"), meta.getLastModified());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String generatePresignedUrl(String bucket, String key, int expireSeconds) {
        String realBucket = props.resolveBucketName(bucket);
        try {
            Date expiry = new Date(System.currentTimeMillis() + expireSeconds * 1000L);
            return client().generatePresignedUrl(realBucket, key, expiry).toString();
        } catch (Exception e) {
            throw new OssException("生成签名 URL 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<OssObjectSummary> listObjects(String bucket, String prefix, int maxKeys) {
        String realBucket = props.resolveBucketName(bucket);
        try {
            ObjectListing listing;
            if (prefix == null || prefix.isEmpty()) {
                listing = client().listObjects(realBucket);
            } else {
                listing = client().listObjects(realBucket, prefix);
            }
            List<OssObjectSummary> result = new ArrayList<>();
            int count = 0;
            for (OSSObjectSummary s : listing.getObjectSummaries()) {
                result.add(new OssObjectSummary(s.getKey(), s.getSize(), s.getETag(),
                        null, s.getKey().endsWith("/"), s.getLastModified()));
                count++;
                if (maxKeys > 0 && count >= maxKeys) {
                    break;
                }
            }
            Collections.sort(result, (a, b) -> a.getKey().compareTo(b.getKey()));
            return result;
        } catch (Exception e) {
            log.warn("Aliyun listObjects failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== 桶操作 ====================

    @Override
    public void createBucket(String bucket, String region, String acl) throws IOException {
        String realBucket = props.resolveBucketName(bucket);
        try {
            CreateBucketRequest req = new CreateBucketRequest(realBucket);
            if (region != null && !region.isEmpty()) {
                req.setLocationConstraint(region);
            }
            client().createBucket(req);
            String aclValue = (acl == null || acl.isEmpty()) ? props.getDefaultAcl() : acl;
            setBucketAcl(bucket, aclValue);
        } catch (Exception e) {
            throw new IOException("Aliyun createBucket failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteBucket(String bucket) throws IOException {
        String realBucket = props.resolveBucketName(bucket);
        try {
            // 先清空所有对象
            ObjectListing listing = client().listObjects(realBucket);
            for (OSSObjectSummary s : listing.getObjectSummaries()) {
                client().deleteObject(realBucket, s.getKey());
            }
            client().deleteBucket(realBucket);
        } catch (Exception e) {
            throw new IOException("Aliyun deleteBucket failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean doesBucketExist(String bucket) {
        String realBucket = props.resolveBucketName(bucket);
        try {
            return client().doesBucketExist(realBucket);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<String> listBuckets() {
        try {
            List<String> result = new ArrayList<>();
            for (com.aliyun.oss.model.Bucket b : client().listBuckets()) {
                String name = b.getName();
                if (props.getBucketPrefix() != null && !props.getBucketPrefix().isEmpty()
                        && name.startsWith(props.getBucketPrefix())) {
                    name = name.substring(props.getBucketPrefix().length());
                }
                result.add(name);
            }
            Collections.sort(result);
            return result;
        } catch (Exception e) {
            log.warn("Aliyun listBuckets failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void setBucketAcl(String bucket, String acl) {
        String realBucket = props.resolveBucketName(bucket);
        try {
            SetBucketAclRequest req = new SetBucketAclRequest(realBucket, toAliyunAcl(acl));
            client().setBucketAcl(req);
        } catch (Exception e) {
            throw new OssException("设置桶 ACL 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getBucketAcl(String bucket) {
        String realBucket = props.resolveBucketName(bucket);
        try {
            BucketInfo info = client().getBucketInfo(realBucket);
            CannedAccessControlList acl = info.getCannedACL();
            if (acl == null) {
                return BucketAcl.PRIVATE.getCode();
            }
            switch (acl) {
                case PublicRead:
                    return BucketAcl.PUBLIC_READ.getCode();
                case PublicReadWrite:
                    return BucketAcl.PUBLIC_READ_WRITE.getCode();
                case Private:
                default:
                    return BucketAcl.PRIVATE.getCode();
            }
        } catch (Exception e) {
            return BucketAcl.PRIVATE.getCode();
        }
    }

    private CannedAccessControlList toAliyunAcl(String acl) {
        if (acl == null || acl.isEmpty()) {
            return CannedAccessControlList.Private;
        }
        switch (acl) {
            case "public-read":
                return CannedAccessControlList.PublicRead;
            case "public-read-write":
                return CannedAccessControlList.PublicReadWrite;
            case "private":
            default:
                return CannedAccessControlList.Private;
        }
    }
}
