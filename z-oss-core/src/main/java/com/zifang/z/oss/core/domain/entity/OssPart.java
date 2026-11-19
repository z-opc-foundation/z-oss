package com.zifang.z.oss.core.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizModel;

import java.time.LocalDateTime;

/**
 * 分片实体
 */
@BizModel(name = "分片实体", module = "z-oss", description = "分片实体")
@TableName("oss_part")
public class OssPart {

    @TableId(type = IdType.AUTO)
    @BizField(name = "主键", type = BizField.FieldType.INTEGER)
    private Long id;

    @BizField(name = "分片会话ID", type = BizField.FieldType.INTEGER)
    private Long multipartId;

    @BizField(name = "分片序号，从1开始", type = BizField.FieldType.INTEGER)
    private Integer partNumber;

    @BizField(name = "分片校验和", type = BizField.FieldType.STRING)
    private String etag;

    @BizField(name = "分片大小", type = BizField.FieldType.INTEGER)
    private Long size;

    @TableField(fill = FieldFill.INSERT)
    @BizField(name = "创建时间", type = BizField.FieldType.DATETIME)
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMultipartId() {
        return multipartId;
    }

    public void setMultipartId(Long multipartId) {
        this.multipartId = multipartId;
    }

    public Integer getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(Integer partNumber) {
        this.partNumber = partNumber;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}

