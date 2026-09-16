package com.zifang.z.oss.core.domain.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zifang.z.oss.common.exception.OssException;
import com.zifang.z.oss.core.domain.entity.OssUser;
import com.zifang.z.oss.core.domain.mapper.OssUserMapper;
import com.zifang.z.oss.core.domain.service.IOssUserService;
import org.springframework.stereotype.Service;

import java.util.UUID;
import com.zifang.util.core.lang.RandomUtil;

/**
 * 用户服务实现
 */
@Service
public class OssUserServiceImpl extends ServiceImpl<OssUserMapper, OssUser> implements IOssUserService {

    @Override
    public OssUser getByAccessKey(String accessKey) {
        return this.getOne(new LambdaQueryWrapper<OssUser>()
                .eq(OssUser::getAccessKey, accessKey));
    }

    @Override
    public boolean verifyCredentials(String accessKey, String secretKey) {
        OssUser user = getByAccessKey(accessKey);
        if (user == null) {
            return false;
        }
        return user.getSecretKey().equals(secretKey);
    }

    @Override
    public OssUser createUser(String username, String secretKey) {
        OssUser user = new OssUser();
        user.setUsername(username);
        user.setAccessKey(generateAccessKey());
        user.setSecretKey(secretKey);
        user.setStatus(1);
        this.save(user);
        return user;
    }

    @Override
    public OssUser register(String username, String password) {
        // 检查用户名是否已存在
        OssUser existUser = this.getOne(new LambdaQueryWrapper<OssUser>()
                .eq(OssUser::getUsername, username));
        if (existUser != null) {
            throw new OssException(400, "Username already exists");
        }

        // 创建新用户
        OssUser user = new OssUser();
        user.setUsername(username);
        user.setAccessKey(generateAccessKey());
        user.setSecretKey(password); // 实际生产中应该加密存储
        user.setStatus(1);
        this.save(user);
        return user;
    }

    @Override
    public OssUser login(String username, String password) {
        // 根据用户名查询用户
        OssUser user = this.getOne(new LambdaQueryWrapper<OssUser>()
                .eq(OssUser::getUsername, username));
        if (user == null) {
            throw new OssException(401, "Invalid username or password");
        }

        // 验证密码
        if (!user.getSecretKey().equals(password)) {
            throw new OssException(401, "Invalid username or password");
        }

        // 检查用户状态
        if (user.getStatus() != 1) {
            throw new OssException(403, "User is disabled");
        }

        return user;
    }

    @Override
    public OssUser updateUser(String accessKey, String username) {
        OssUser user = getByAccessKey(accessKey);
        if (user == null) {
            throw new OssException(404, "User not found");
        }

        // 检查新用户名是否已被占用
        if (username != null && !username.equals(user.getUsername())) {
            OssUser existUser = this.getOne(new LambdaQueryWrapper<OssUser>()
                    .eq(OssUser::getUsername, username));
            if (existUser != null) {
                throw new OssException(400, "Username already exists");
            }
            user.setUsername(username);
        }

        this.updateById(user);
        return user;
    }

    @Override
    public OssUser resetAccessKey(String accessKey) {
        OssUser user = getByAccessKey(accessKey);
        if (user == null) {
            throw new OssException(404, "User not found");
        }

        // 生成新的AK/SK
        user.setAccessKey(generateAccessKey());
        user.setSecretKey(generateSecretKey());
        this.updateById(user);
        return user;
    }

    @Override
    public void changePassword(String accessKey, String oldPassword, String newPassword) {
        OssUser user = getByAccessKey(accessKey);
        if (user == null) {
            throw new OssException(404, "User not found");
        }

        // 验证旧密码
        if (!user.getSecretKey().equals(oldPassword)) {
            throw new OssException(400, "Old password is incorrect");
        }

        // 更新密码
        user.setSecretKey(newPassword);
        this.updateById(user);
    }

    private String generateAccessKey() {
        return "zoss_" + RandomUtil.uuidShort(16);
    }

    private String generateSecretKey() {
        return RandomUtil.uuidCompact();
    }
}