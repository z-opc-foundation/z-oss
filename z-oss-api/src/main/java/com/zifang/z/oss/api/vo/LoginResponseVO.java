package com.zifang.z.oss.api.vo;

/**
 * 登录响应视图对象
 */
public class LoginResponseVO {

    private String token;
    private Long userId;
    private String username;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}