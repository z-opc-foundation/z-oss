package com.zifang.z.oss.common.exception;

/**
 * 桶相关异常
 */
public class BucketException extends OssException {

    public BucketException(String message) {
        super(400, message);
    }

    public BucketException(int code, String message) {
        super(code, message);
    }
}