package com.zifang.z.oss.core.hook;

import com.zifang.z.oss.common.exception.BucketException;
import com.zifang.z.oss.core.domain.entity.OssBucket;
import com.zifang.z.oss.core.domain.service.IOssBucketService;
import com.zifang.z.oss.core.hook.OssTenantHook;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * z-opc OSS 租户初始化钩子实现.
 *
 * <p>新建租户时, 为 owner 用户建一个默认存储桶 {@code <tenantCode>-default}:
 * <ul>
 *   <li>命名: {@code <tenantCode>-default} (符合 OSS 桶命名规则: 小写/数字/-)</li>
 *   <li>权限: private</li>
 *   <li>归属: owner user (ctx.createdBy)</li>
 * </ul>
 *
 * <p>order=300: 在 z-ctc (10) / z-config (200) 之后, 业务模块排最后.
 * 幂等: 同名 bucket 跳过.
 */
@Component
public class ZOssTenantHookImpl implements OssTenantHook {

    private static final Logger log = LogManager.getLogger(ZOssTenantHookImpl.class);

    @Autowired
    private IOssBucketService bucketService;

    @Override
    public String moduleName() {
        return "z-oss";
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    public void onTenantCreated(String tenantCode, Long ownerUserId) {
        // 无 owner (管理员手工创建) 时跳过: 不能确定桶归谁
        if (ownerUserId == null) {
            log.warn("[z-oss-init] 租户 {} 没有 owner user (ownerUserId=null), 跳过默认桶创建. " +
                    "可手动调用 /api/v1/bucket 创建.", tenantCode);
            return;  // 不算错, 直接返回
        }

        String bucketName = sanitizeBucketName(tenantCode) + "-default";
        // 幂等: 查现有 (注意 validateBucket 在桶不存在时 throw BucketException, 需 catch)
        try {
            OssBucket existing = bucketService.validateBucket(bucketName, ownerUserId);
            if (existing != null) {
                log.debug("[z-oss-init] 桶 {} 已存在, 跳过", bucketName);
                return;
            }
        } catch (BucketException notFound) {
            // 桶不存在, 正常情况, 继续创建
            log.debug("[z-oss-init] 桶 {} 不存在, 开始创建", bucketName);
        }
        // 失败抛异常让调用方准确报告
        OssBucket created = bucketService.createBucket(bucketName, ownerUserId);
        log.info("[z-oss-init] 租户 {} 创建默认桶 id={} name={}",
                tenantCode, created == null ? "?" : created.getId(), bucketName);
    }

    /**
     * OSS 桶名规则: 3-63 位, 小写字母/数字/-. 把 tenantCode 清洗一下.
     */
    private String sanitizeBucketName(String s) {
        if (s == null) return "tenant";
        String lower = s.toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (char c : lower.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-') {
                sb.append(c);
            } else if (c == '_' || c == '.') {
                sb.append('-');
            }
        }
        String result = sb.toString();
        if (result.length() < 3) result = (result + "tenant").substring(0, Math.min(63, result.length() + 6));
        if (result.length() > 50) result = result.substring(0, 50);
        return result;
    }
}
