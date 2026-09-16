package com.zifang.z.oss.api.config;

import com.zifang.z.oss.common.exception.OssException;
import com.zifang.z.oss.core.domain.entity.OssUser;
import com.zifang.z.oss.core.domain.service.IOssUserService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 认证拦截器 (FEATURE055 · D06 已整改).
 * <p>
 * <b>要求</b>: 调用方必须通过 {@code X-Zoss-Access-Key} + {@code X-Zoss-Secret-Key}
 * 两个请求头传入 z-oss 自身凭证. 其他方式 (URL query 传密钥 / 任意 Bearer 归户 admin)
 * 一律拒绝, 关闭 P0 安全旁路.
 */
@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    public static final String HEADER_ACCESS_KEY = "X-Zoss-Access-Key";
    public static final String HEADER_SECRET_KEY = "X-Zoss-Secret-Key";
    public static final String ATTR_USER = "oss_user";
    private static final Logger log = LogManager.getLogger(AuthenticationInterceptor.class);
    @Autowired
    private IOssUserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // D06: 仅接受 Header 凭证; query 传密钥会进访问日志, 已关闭该通道.
        String accessKey = request.getHeader(HEADER_ACCESS_KEY);
        String secretKey = request.getHeader(HEADER_SECRET_KEY);

        if (accessKey == null || secretKey == null) {
            log.warn("z-oss 鉴权拒绝: 缺少 AK/SK Header (path={}, ua={})",
                    request.getRequestURI(), request.getHeader("User-Agent"));
            throw new OssException(401, "Missing z-oss credentials (X-Zoss-Access-Key / X-Zoss-Secret-Key)");
        }

        OssUser user = userService.getByAccessKey(accessKey);
        if (user == null) {
            throw new OssException(401, "Invalid access key");
        }

        if (!user.getSecretKey().equals(secretKey)) {
            throw new OssException(401, "Invalid secret key");
        }

        if (user.getStatus() != 1) {
            throw new OssException(403, "User is disabled");
        }

        // 将用户信息存入请求属性
        request.setAttribute(ATTR_USER, user);
        return true;
    }
}