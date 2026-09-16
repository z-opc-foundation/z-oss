package com.zifang.z.oss.common.enums;

/**
 * 桶访问控制类型
 */
public enum BucketAcl {

    PRIVATE("private", "私有"),
    PUBLIC_READ("public-read", "公共读"),
    PUBLIC_READ_WRITE("public-read-write", "公共读写"),
    AUTHENTICATED_READ("authenticated-read", "认证读");

    private final String code;
    private final String desc;

    BucketAcl(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}