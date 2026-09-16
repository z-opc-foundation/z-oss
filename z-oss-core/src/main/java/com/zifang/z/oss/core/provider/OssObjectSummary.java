package com.zifang.z.oss.core.provider;

import java.util.Date;

/**
 * 对象元数据 POJO（Provider 层抽象，不耦合 DB 实体）
 */
public class OssObjectSummary {

    /**
     * 对象 key
     */
    private String key;

    /**
     * 对象大小（字节）
     */
    private Long size;

    /**
     * ETag
     */
    private String etag;

    /**
     * MIME 类型
     */
    private String contentType;

    /**
     * 是否文件夹（OSS 原生无 folder，由 Provider 自己造）
     */
    private Boolean isFolder;

    /**
     * 最后修改时间
     */
    private Date lastModified;

    public OssObjectSummary() {
    }

    public OssObjectSummary(String key, Long size, String etag, String contentType,
                            Boolean isFolder, Date lastModified) {
        this.key = key;
        this.size = size;
        this.etag = etag;
        this.contentType = contentType;
        this.isFolder = isFolder;
        this.lastModified = lastModified;
    }

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

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Boolean getIsFolder() {
        return isFolder;
    }

    public void setIsFolder(Boolean isFolder) {
        this.isFolder = isFolder;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }
}
