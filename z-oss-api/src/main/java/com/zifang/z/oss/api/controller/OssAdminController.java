package com.zifang.z.oss.api.controller;

import com.zifang.ctc.sso.config.SsoContext;
import com.zifang.ctc.sso.model.UserInfo;
import com.zifang.util.core.meta.Result;
import com.zifang.z.oss.core.domain.entity.OssBucket;
import com.zifang.z.oss.core.domain.entity.OssObject;
import com.zifang.z.oss.core.domain.entity.OssUser;
import com.zifang.z.oss.core.domain.service.IOssBucketService;
import com.zifang.z.oss.core.domain.service.IOssObjectService;
import com.zifang.z.oss.core.domain.service.IOssUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对象存储（z-oss）管理面 —— z-opc 侧的只读 controller，挂在 {@code /api/oss-admin/**}。
 *
 * <p><b>为什么不走 z-oss 自己的 {@code /api/v1/**}</b>（实测 2026-09-25，已登录会话内）：
 * {@code AuthenticationInterceptor} 要求 {@code X-Zoss-Access-Key / X-Zoss-Secret-Key} 两个头，
 * 带不带平台会话都是 401（{@code anon=401 auth=401}），而 AK/SK 存在 {@code z_oss_user} 表里、
 * 浏览器侧没有任何安全通道能拿到它。TASK-20260924-003 卡上写的"凭证下发方式待定"就是这一格。
 * 本控制器选的是另一条路：<b>不发凭证，而是在同一个 JVM 内直接调 z-oss 的 service bean</b>
 * （{@code IOssBucketService} / {@code IOssObjectService} 都在容器里 —— 它们的 controller
 * 能被注册就说明这两个 bean 起得来），鉴权换成平台自己的 SSO。
 *
 * <p><b>为什么这条路是真的</b>：{@code /api/**} 整族由 {@code SsoInterceptor} 覆盖
 * （实测 {@code /api/oss/list}、{@code /api/config/groupList}、{@code /api/acl/page} 不带 token 一律 302），
 * 所以本控制器天然要求已登录；再用 {@link SsoContext#getCurrentUser()} 的 username 去查
 * {@code z_oss_user}，只把该用户名下的桶给出去 —— 越权访问别人的桶在 service 层就会被
 * {@code validateBucket} 抛 {@code BucketException}（经 {@code GlobalExceptionAdvice} 按 HTTP 语义返回）。
 *
 * <p><b>明确不复用的两条现成路（都是假的）</b>：
 * <ul>
 *   <li>{@code E2EAliasController#ossListBuckets}（{@code /api/oss/list}）直查 {@code z_oss_bucket} 表，
 *       且 {@code catch (Exception e) { // ignore }} 后返回<b>空列表</b> ⇒ "查不到"和"没有桶"在页面上不可区分；</li>
 *   <li>{@code E2EAliasController#ossBucketDetail}（{@code /api/oss/{bucketName}}）返回
 *       {@code new OssBucketDetailResponse(name)} —— {@code objects=[] totalObjects=0 totalSize=0}
 *       是<b>构造出来的常量</b>，实测 HTTP 200 带真信封，最容易被后来人误读成"接口通了、确实是空"。</li>
 * </ul>
 *
 * <p><b>只读</b>：本文件只有 {@code @GetMapping}，没有任何写映射 —— z-oss 的建桶/删桶/上传/批量删
 * 留在 {@code /api/v1/**} 那条需要 AK/SK 的路上，不在管理台里开第二条不设防的写路径
 * （TASK-20260925-018 刚记下"裸路径含写接口完全不设防"这条 P0，不要再制造同类）。
 *
 * <p><b>绝不外泄</b>：{@code OssUser} 带 {@code accessKey/secretKey/password}，
 * 所以 {@code /whoami} 只回 id 与 username，任何端点都不返回 {@code OssUser} 实体。
 */
@RestController
@RequestMapping("/api/oss-admin")
public class OssAdminController {

    @Resource
    private IOssBucketService bucketService;
    @Resource
    private IOssObjectService objectService;
    @Resource
    private IOssUserService userService;

    /**
     * 平台登录身份 → z-oss 账号的映射结果。
     * <p>未开户时 {@code provisioned=false} 且 {@code success=false}：这是"这个账号在 z-oss 里没有身份"，
     * 不能降级成空列表冒充"有账号、桶为空"。
     */
    @GetMapping("/whoami")
    public Result<Map<String, Object>> whoami() {
        UserInfo user = SsoContext.getCurrentUser();
        if (user == null || user.getUsername() == null) {
            return Result.<Map<String, Object>>fail("未取到平台登录身份（SsoContext 为空），/api/oss-admin/** 需要已登录会话").code(401);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("platform_username", user.getUsername());
        out.put("platform_tenant", user.getTenantCode());
        OssUser ossUser = userService.lambdaQuery()
                .eq(OssUser::getUsername, user.getUsername())
                .last("LIMIT 1")
                .one();
        out.put("provisioned", ossUser != null);
        if (ossUser != null) {
            out.put("zoss_user_id", ossUser.getId());
            out.put("zoss_status", ossUser.getStatus());
        }
        return Result.success(out);
    }

    /**
     * 当前身份名下的桶列表（等价于 z-oss {@code GET /api/v1/bucket} 的 list 分支，但不需要 AK/SK）。
     */
    @GetMapping("/buckets")
    public Result<List<OssBucket>> buckets() {
        return Result.success(bucketService.listUserBuckets(currentOssUserId()));
    }

    /**
     * 单个桶的详情 + 两份统计，<b>分别来自两个 service，不能只取一份</b>（实测源码）：
     * {@code IOssBucketService#getBucketStats} 回的是桶元数据（region/acl/createTime/
     * {@code physicalExists} = 存储提供方那边这个桶真的存在吗 / activeProvider），
     * 而对象数与总大小在 {@code IOssObjectService#getBucketStats} 里 ——
     * TASK-20260924-003 修的那个 500 就是把两者当成了一份（{@code ObjectController} 早先拿
     * bucketService 的返回值去读 {@code objectCount}，读到 null 直接 NPE）。
     * 这里两路都取，页面上也分两栏显示。
     */
    @GetMapping("/bucket/detail")
    public Result<Map<String, Object>> bucketDetail(@RequestParam String name) {
        Long userId = currentOssUserId();
        OssBucket bucket = bucketService.validateBucket(name, userId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bucket_name", bucket.getName());
        out.put("region", bucket.getRegion());
        out.put("acl", bucket.getAcl());
        out.put("policy", bucket.getPolicy());
        out.put("create_time", bucket.getCreateTime());
        out.put("bucket_stats", bucketService.getBucketStats(name, userId));
        out.put("object_stats", objectService.getBucketStats(name, userId));
        return Result.success(out);
    }

    /**
     * 对象列表。{@code prefix} 原样交给 service（z-oss 语义即"目录前缀"），本层不做拼接也不兜空。
     */
    @GetMapping("/objects")
    public Result<List<OssObject>> objects(@RequestParam String name,
                                          @RequestParam(required = false) String prefix) {
        Long userId = currentOssUserId();
        bucketService.validateBucket(name, userId);
        return Result.success(objectService.listObjects(name, prefix, userId));
    }

    private Long currentOssUserId() {
        UserInfo user = SsoContext.getCurrentUser();
        if (user == null || user.getUsername() == null) {
            throw new com.zifang.z.oss.common.exception.OssException(401,
                    "未取到平台登录身份（SsoContext 为空）");
        }
        OssUser ossUser = userService.lambdaQuery()
                .eq(OssUser::getUsername, user.getUsername())
                .last("LIMIT 1")
                .one();
        if (ossUser == null) {
            throw new com.zifang.z.oss.common.exception.OssException(409,
                    "平台账号 " + user.getUsername() + " 在 z-oss 未开户（z_oss_user 无此 username），"
                            + "请先在 z-oss 侧注册该账号再进管理台 —— 这里不返回空列表冒充\"没有桶\"");
        }
        return ossUser.getId();
    }
}
