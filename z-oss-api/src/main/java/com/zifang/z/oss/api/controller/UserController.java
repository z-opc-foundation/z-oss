package com.zifang.z.oss.api.controller;

import com.zifang.util.core.meta.Result;
import com.zifang.util.core.meta.ResultCode;
import com.zifang.z.oss.api.dto.UserLoginRequest;
import com.zifang.z.oss.api.dto.UserRegisterRequest;
import com.zifang.z.oss.api.vo.ChangePasswordResponseVO;
import com.zifang.z.oss.api.vo.LoginResponseVO;
import com.zifang.z.oss.api.vo.UserVO;
import com.zifang.z.oss.core.domain.entity.OssUser;
import com.zifang.z.oss.core.domain.service.IOssUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户管理 API 控制器,提供用户注册、登录、信息维护、密钥重置与密码修改等接口.
 * <p>
 * API 基础路径: /api/v1/user
 * 所属模块: z-oss-api
 * 鉴权: 注册/登录无需鉴权;其余接口通过请求头 X-Zoss-Access-Key 识别当前用户
 *
 * <p>主要端点:
 * <ul>
 *   <li>POST /api/v1/user/register — 用户注册</li>
 *   <li>POST /api/v1/user/login — 用户登录,返回 accessKey:secretKey 作为 token</li>
 *   <li>GET /api/v1/user/info — 查询当前用户信息</li>
 *   <li>PUT /api/v1/user/info — 更新用户名</li>
 *   <li>POST /api/v1/user/reset-key — 重置 accessKey/secretKey</li>
 *   <li>POST /api/v1/user/password — 修改密码</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    @Autowired
    private IOssUserService userService;

    /**
     * 用户注册,使用用户名与密码创建新账号,系统会自动生成 accessKey/secretKey.
     *
     * @param request 注册请求参数(用户名、密码)
     * @return 新注册用户的 VO
     */
    @PostMapping("/register")
    public Result<UserVO> register(@RequestBody UserRegisterRequest request) {
        OssUser user = userService.register(request.getUsername(), request.getPassword());
        return Result.<UserVO>success(convertUser(user));
    }

    /**
     * 用户登录,校验成功后以 accessKey:secretKey 形式签发 token.
     *
     * @param request 登录请求参数(用户名、密码)
     * @return 登录响应 VO(token、用户 ID、用户名)
     */
    @PostMapping("/login")
    public Result<LoginResponseVO> login(@RequestBody UserLoginRequest request) {
        OssUser user = userService.login(request.getUsername(), request.getPassword());
        LoginResponseVO vo = new LoginResponseVO();
        vo.setToken(user.getAccessKey() + ":" + user.getSecretKey());
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        return Result.success(vo);
    }

    /**
     * 根据 accessKey 查询当前用户信息,用户不存在时返回 404.
     *
     * @param accessKey 用户 accessKey(请求头 X-Zoss-Access-Key)
     * @return 用户 VO 的封装结果
     */
    @GetMapping("/info")
    public Result<UserVO> getUserInfo(@RequestHeader("X-Zoss-Access-Key") String accessKey) {
        OssUser user = userService.getByAccessKey(accessKey);
        if (user == null) {
            return Result.<UserVO>fail("资源不存在").code(ResultCode.NOT_FOUND.getCode());
        }
        return Result.<UserVO>success(convertUser(user));
    }

    /**
     * 更新当前用户的用户名.
     *
     * @param accessKey 用户 accessKey(请求头 X-Zoss-Access-Key)
     * @param request   更新请求参数(用户名)
     * @return 更新后的用户 VO
     */
    @PutMapping("/info")
    public Result<UserVO> updateUserInfo(@RequestHeader("X-Zoss-Access-Key") String accessKey,
                                         @RequestBody UserRegisterRequest request) {
        OssUser user = userService.updateUser(accessKey, request.getUsername());
        return Result.<UserVO>success(convertUser(user));
    }

    /**
     * 重置当前用户的 accessKey/secretKey.
     *
     * @param accessKey 用户 accessKey(请求头 X-Zoss-Access-Key)
     * @return 重置后的用户 VO(含新的 accessKey/secretKey)
     */
    @PostMapping("/reset-key")
    public Result<UserVO> resetKeys(@RequestHeader("X-Zoss-Access-Key") String accessKey) {
        OssUser user = userService.resetAccessKey(accessKey);
        return Result.<UserVO>success(convertUser(user));
    }

    /**
     * 修改当前用户的密码,需要提供旧密码与新密码.
     *
     * @param accessKey   用户 accessKey(请求头 X-Zoss-Access-Key)
     * @param passwordMap 包含 oldPassword 与 newPassword 的请求体
     * @return 密码修改结果响应 VO
     */
    @PostMapping("/password")
    public Result<ChangePasswordResponseVO> changePassword(@RequestHeader("X-Zoss-Access-Key") String accessKey,
                                                           @RequestBody Map<String, String> passwordMap) {
        String oldPassword = passwordMap.get("oldPassword");
        String newPassword = passwordMap.get("newPassword");
        userService.changePassword(accessKey, oldPassword, newPassword);
        ChangePasswordResponseVO vo = new ChangePasswordResponseVO();
        vo.setSuccess(true);
        vo.setMessage("密码修改成功");
        return Result.success(vo);
    }

    /**
     * 将用户实体转换为对外 VO.
     *
     * @param user 用户实体
     * @return 转换后的 VO 对象
     */
    private UserVO convertUser(OssUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setAccessKey(user.getAccessKey());
        vo.setSecretKey(user.getSecretKey());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}