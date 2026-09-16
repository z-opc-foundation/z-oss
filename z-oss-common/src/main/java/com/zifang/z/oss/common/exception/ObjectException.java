package com.zifang.z.oss.common.exception;

/**
 * 对象相关异常
 */
public class ObjectException extends OssException {

    public ObjectException(String message) {
        super(404, message);
    }

    public ObjectException(int code, String message) {
        super(code, message);
    }
}