package com.zifang.z.oss.core.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizModel;

import java.time.LocalDateTime;

/**
 * OSS用户实体
 */
@BizModel(name = "OSS用户实体", module = "z-oss", description = "OSS用户实体")
@TableName("oss_user")
public class OssUser {

    @TableId(type = IdType.AUTO)
    @BizField(name = "主键", type = BizField.FieldType.INTEGER)
    private Long id;

    @BizField(name = "用户名", type = BizField.FieldType.STRING)
    private String username;

    @BizField(name = "Access Key", type = BizField.FieldType.STRING)
    private String accessKey;

    @BizField(name = "Secret Key（加密存储）", type = BizField.FieldType.STRING)
    private String secretKey;

    @BizField(name = "状态 0禁用 1启用", type = BizField.FieldType.INTEGER)
    private Integer status;

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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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

