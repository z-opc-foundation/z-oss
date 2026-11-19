package com.zifang.z.oss.core.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizModel;

import java.time.LocalDateTime;

/**
 * 存储桶实体
 */
@BizModel(name = "存储桶实体", module = "z-oss", description = "存储桶实体")
@TableName("oss_bucket")
public class OssBucket {

    @TableId(type = IdType.AUTO)
    @BizField(name = "主键", type = BizField.FieldType.INTEGER)
    private Long id;

    @BizField(name = "桶名称", type = BizField.FieldType.STRING)
    private String name;

    @BizField(name = "所属用户ID", type = BizField.FieldType.INTEGER)
    private Long userId;

    @BizField(name = "区域", type = BizField.FieldType.STRING)
    private String region;

    @BizField(name = "桶策略（JSON）", type = BizField.FieldType.STRING)
    private String policy;

    @BizField(name = "访问控制", type = BizField.FieldType.STRING)
    private String acl;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getPolicy() {
        return policy;
    }

    public void setPolicy(String policy) {
        this.policy = policy;
    }

    public String getAcl() {
        return acl;
    }

    public void setAcl(String acl) {
        this.acl = acl;
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

