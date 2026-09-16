package com.zifang.z.oss.core.file.impl;

import com.zifang.z.oss.core.file.FileInfo;
import com.zifang.z.oss.core.file.FileStorageService;
import com.zifang.z.oss.core.provider.OssObjectSummary;
import com.zifang.z.oss.core.provider.OssProviderManager;
import com.zifang.z.oss.core.storage.FileStorageProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;

/**
 * FileStorageService 实现 — 委托给 {@link OssProviderManager} 完成真实存储。
 *
 * <p>本地模式: 文件写入 {@code oss.storage.file.root}/{bucket}/{path}/{filename}，
 * getAllUrl 拼接 public-base-url 前缀。
 * <p>云模式 (aliyun): 文件上传到 OSS bucket，getAllUrl 返回预签名 URL。
 */
public class FileStorageServiceImpl implements FileStorageService {

    private static final Logger log = LogManager.getLogger(FileStorageServiceImpl.class);

    private final OssProviderManager manager;
    private final String defaultBucket;
    private final String publicBaseUrl;

    public FileStorageServiceImpl(OssProviderManager manager, FileStorageProperties props) {
        this.manager = manager;
        this.defaultBucket = props.getDefaultBucket();
        this.publicBaseUrl = props.getPublicBaseUrl();
    }

    @Override
    public FileInfo of(byte[] bytes) {
        FileInfo info = new FileInfo();
        info.setBytes(bytes);
        return wrapUpload(info);
    }

    @Override
    public String getAllUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        // 完整 URL 直接返回
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        // 拼接 public-base-url 前缀
        String prefix = publicBaseUrl;
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        String rel = url.startsWith("/") ? url.substring(1) : url;
        return prefix + rel;
    }

    /**
     * 包装 FileInfo，重写 upload() 方法注入真实上传逻辑。
     */
    private FileInfo wrapUpload(final FileInfo delegate) {
        return new FileInfo() {
            @Override
            public FileInfo upload() {
                byte[] data = delegate.getBytes();
                String path = delegate.getPath();
                String saveFilename = delegate.getSaveFilename();

                if (data == null || data.length == 0) {
                    log.warn("[FileStorage] upload skipped: bytes is empty");
                    delegate.setUrl("");
                    return delegate;
                }

                // 构造 object key: {path}/{saveFilename}
                String key = buildObjectKey(path, saveFilename);

                try {
                    ByteArrayInputStream bais = new ByteArrayInputStream(data);
                    OssObjectSummary summary = manager.provider().putObject(
                            defaultBucket, key, bais, data.length, null);
                    // 本地模式下 key 即为相对路径，云模式下也用 key 作为标识
                    delegate.setUrl(summary.getKey());
                    log.info("[FileStorage] upload success: bucket={}, key={}, size={}",
                            defaultBucket, key, data.length);
                } catch (Exception e) {
                    log.error("[FileStorage] upload failed: bucket={}, key={}", defaultBucket, key, e);
                    delegate.setUrl("");
                }
                return delegate;
            }

            @Override
            public byte[] getBytes() { return delegate.getBytes(); }
            @Override
            public String getPath() { return delegate.getPath(); }
            @Override
            public String getSaveFilename() { return delegate.getSaveFilename(); }
            @Override
            public String getUrl() { return delegate.getUrl(); }
            @Override
            public void setUrl(String url) { delegate.setUrl(url); }
            @Override
            public FileInfo setPath(String path) { delegate.setPath(path); return this; }
            @Override
            public FileInfo setSaveFilename(String saveFilename) { delegate.setSaveFilename(saveFilename); return this; }
            @Override
            public FileInfo setBytes(byte[] bytes) { delegate.setBytes(bytes); return this; }
        };
    }

    private String buildObjectKey(String path, String saveFilename) {
        StringBuilder sb = new StringBuilder();
        if (path != null && !path.isEmpty()) {
            String p = path.endsWith("/") ? path : path + "/";
            sb.append(p);
        }
        if (saveFilename != null && !saveFilename.isEmpty()) {
            sb.append(saveFilename);
        }
        return sb.toString();
    }
}
