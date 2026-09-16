package com.zifang.z.oss.common.exception;

/**
 * OSS基础异常
 */
public class OssException extends RuntimeException {

    private final int code;
    private final String message;

    public OssException(String message) {
        super(message);
        this.code = 500;
        this.message = message;
    }

    public OssException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
        this.message = message;
    }

    public OssException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public OssException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}