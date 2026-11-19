package com.zifang.z.oss.core.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizModel;

import java.time.LocalDateTime;

/**
 * 分片上传实体
 */
@BizModel(name = "分片上传实体", module = "z-oss", description = "分片上传实体")
@TableName("oss_multipart")
public class OssMultipart {

    @TableId(type = IdType.AUTO)
    @BizField(name = "主键", type = BizField.FieldType.INTEGER)
    private Long id;

    @BizField(name = "分片上传会话ID", type = BizField.FieldType.STRING)
    private String uploadId;

    @BizField(name = "桶ID", type = BizField.FieldType.INTEGER)
    private Long bucketId;

    @BizField(name = "归并后的对象键", type = BizField.FieldType.STRING)
    private String objectKey;

    @BizField(name = "发起者ID", type = BizField.FieldType.INTEGER)
    private Long userId;

    @BizField(name = "分片大小（字节）", type = BizField.FieldType.INTEGER)
    private Long partSize;

    @BizField(name = "总分片数", type = BizField.FieldType.INTEGER)
    private Integer totalParts;

    @BizField(name = "已上传分片数", type = BizField.FieldType.INTEGER)
    private Integer uploadedParts;

    @BizField(name = "状态 0进行中 1已完成 2已放弃", type = BizField.FieldType.INTEGER)
    private Integer status;

    @BizField(name = "过期时间", type = BizField.FieldType.DATETIME)
    private LocalDateTime expireTime;

    @TableField(fill = FieldFill.INSERT)
    @BizField(name = "创建时间", type = BizField.FieldType.DATETIME)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @BizField(name = "更新时间", type = BizField.FieldType.DATETIME)
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public Long getBucketId() {
        return bucketId;
    }

    public void setBucketId(Long bucketId) {
        this.bucketId = bucketId;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getPartSize() {
        return partSize;
    }

    public void setPartSize(Long partSize) {
        this.partSize = partSize;
    }

    public Integer getTotalParts() {
        return totalParts;
    }

    public void setTotalParts(Integer totalParts) {
        this.totalParts = totalParts;
    }

    public Integer getUploadedParts() {
        return uploadedParts;
    }

    public void setUploadedParts(Integer uploadedParts) {
        this.uploadedParts = uploadedParts;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
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
}

