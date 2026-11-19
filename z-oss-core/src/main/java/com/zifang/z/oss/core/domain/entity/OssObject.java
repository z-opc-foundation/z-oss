package com.zifang.z.oss.core.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizModel;

import java.time.LocalDateTime;

/**
 * 对象元数据实体
 */
@BizModel(name = "对象元数据实体", module = "z-oss", description = "对象元数据实体")
@TableName("oss_object")
public class OssObject {

    @TableId(type = IdType.AUTO)
    @BizField(name = "主键", type = BizField.FieldType.INTEGER)
    private Long id;

    @BizField(name = "桶ID", type = BizField.FieldType.INTEGER)
    private Long bucketId;

    @BizField(name = "桶名称", type = BizField.FieldType.STRING)
    private String bucketName;

    @BizField(name = "对象键", type = BizField.FieldType.STRING)
    private String objectKey;

    @BizField(name = "对象名称", type = BizField.FieldType.STRING)
    private String objectName;

    @BizField(name = "内容类型", type = BizField.FieldType.STRING)
    private String contentType;

    @BizField(name = "文件大小", type = BizField.FieldType.INTEGER)
    private Long contentLength;

    @BizField(name = "ETag", type = BizField.FieldType.STRING)
    private String etag;

    @BizField(name = "存储路径", type = BizField.FieldType.STRING)
    private String storagePath;

    @BizField(name = "所属用户ID", type = BizField.FieldType.INTEGER)
    private Long userId;

    @BizField(name = "版本ID", type = BizField.FieldType.STRING)
    private String versionId;

    @BizField(name = "是否文件夹", type = BizField.FieldType.INTEGER)
    private Integer isFolder;

    @BizField(name = "自定义元数据", type = BizField.FieldType.STRING)
    private String metadata;

    @TableField(fill = FieldFill.INSERT)
    @BizField(name = "创建时间", type = BizField.FieldType.DATETIME)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @BizField(name = "更新时间", type = BizField.FieldType.DATETIME)
    private LocalDateTime updateTime;

    @TableLogic @BizField(name = "逻辑删除", type = BizField.FieldType.INTEGER)
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBucketId() {
        return bucketId;
    }

    public void setBucketId(Long bucketId) {
        this.bucketId = bucketId;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getContentLength() {
        return contentLength;
    }

    public void setContentLength(Long contentLength) {
        this.contentLength = contentLength;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getVersionId() {
        return versionId;
    }

    public void setVersionId(String versionId) {
        this.versionId = versionId;
    }

    public Integer getIsFolder() {
        return isFolder;
    }

    public void setIsFolder(Integer isFolder) {
        this.isFolder = isFolder;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}

