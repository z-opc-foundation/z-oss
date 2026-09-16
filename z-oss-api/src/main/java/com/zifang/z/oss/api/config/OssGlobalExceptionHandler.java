package com.zifang.z.oss.api.config;

import com.zifang.z.oss.common.exception.OssException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * z-oss 全局异常处理器
 */
@RestControllerAdvice(basePackages = "com.zifang.z.oss")
public class OssGlobalExceptionHandler {

    @ExceptionHandler(OssException.class)
    public ResponseEntity<ErrorResponse> handleOssException(OssException e) {
        ErrorResponse response = new ErrorResponse(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.valueOf(e.getCode())).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        ErrorResponse response = new ErrorResponse(500, "Internal server error: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    public static class ErrorResponse {
        private int code;
        private String message;

        public ErrorResponse() {
        }

        public ErrorResponse(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}