package com.zifang.z.oss.common.enums;

/**
 * 对象状态枚举
 */
public enum ObjectStatus {

    NORMAL(1, "正常"),
    DELETED(0, "已删除");

    private final int code;
    private final String desc;

    ObjectStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}