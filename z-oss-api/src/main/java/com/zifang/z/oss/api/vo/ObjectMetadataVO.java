package com.zifang.z.oss.api.vo;

import java.time.LocalDateTime;

/**
 * 对象元数据视图对象 (用于 headObject)
 */
public class ObjectMetadataVO {

    private String key;
    private Long size;
    private String contentType;
    private LocalDateTime lastModified;
    private String etag;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }
}