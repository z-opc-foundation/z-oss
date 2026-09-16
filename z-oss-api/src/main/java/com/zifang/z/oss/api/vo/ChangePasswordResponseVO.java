package com.zifang.z.oss.api.vo;

/**
 * 修改密码响应视图对象
 */
public class ChangePasswordResponseVO {

    private boolean success;
    private String message;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}