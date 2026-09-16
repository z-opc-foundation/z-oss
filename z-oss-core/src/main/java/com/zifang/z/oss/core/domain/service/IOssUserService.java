package com.zifang.z.oss.core.domain.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zifang.z.oss.core.domain.entity.OssUser;

/**
 * 用户服务接口
 */
public interface IOssUserService extends IService<OssUser> {

    /**
     * 根据AccessKey获取用户
     */
    OssUser getByAccessKey(String accessKey);

    /**
     * 验证AK/SK
     */
    boolean verifyCredentials(String accessKey, String secretKey);

    /**
     * 创建用户
     */
    OssUser createUser(String username, String secretKey);

    /**
     * 用户注册
     */
    OssUser register(String username, String password);

    /**
     * 用户登录
     */
    OssUser login(String username, String password);

    /**
     * 更新用户信息
     */
    OssUser updateUser(String accessKey, String username);

    /**
     * 重置AK/SK
     */
    OssUser resetAccessKey(String accessKey);

    /**
     * 修改密码
     */
    void changePassword(String accessKey, String oldPassword, String newPassword);
}