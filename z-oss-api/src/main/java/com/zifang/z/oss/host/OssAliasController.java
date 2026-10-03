package com.zifang.z.oss.host;

import com.zifang.util.core.meta.Result;
import com.zifang.z.oss.api.dto.OssBucketDetailResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 前端别名控制器 (2026-10-03 自 z-opc main-starter 的 E2EAliasController OSS 段平移):
 * 前端 makeApi('oss') 调的是 {@code /api/oss/*}, 真实管理面在
 * {@code /api/oss-admin/**} (OssAdminController, 走 service bean) 与 {@code /api/v1/**} (AK/SK).
 * <p>
 * 已知局限 (原样保留, 与 OssAdminController javadoc 的判语一致):
 * list 直查 {@code z_oss_bucket} 且查库异常吞掉返空表 —— "查不到"与"没有桶"不可区分;
 * detail 返回的是构造壳 ({@code objects=[]}), 非真实对象清单. 真面在 /api/oss-admin.
 * <p>
 * 开关 z.oss.host.enabled (默认关): 寄生 all-in-one 模式由宿主打开;
 * standalone z-oss 容器不开 —— standalone 的前端走 /api/v1/**, 不需要这条别名.
 * 控制器与装配双双挂同一开关, 保证两种装载路径 (宿主 spring.factories / 本应用组件扫描)
 * 都不会在开关关闭时注册出缺 dataSourceCtc 的半成品.
 */
@RestController
@ConditionalOnProperty(prefix = "z.oss.host", name = "enabled", havingValue = "true", matchIfMissing = false)
public class OssAliasController {

    @Resource(name = "dataSourceCtc")
    private DataSource dataSource;

    /**
     * 列出 buckets (alias for /api/v1/bucket).
     * <p>{@code GET /api/oss/list}</p>
     */
    @GetMapping("/api/oss/list")
    public Result<List<Map<String, Object>>> ossListBuckets() {
        return Result.success(queryOssBuckets());
    }

    /**
     * 单个 bucket 详情 (构造壳).
     * <p>{@code GET /api/oss/{bucketName}}</p>
     */
    @GetMapping("/api/oss/{bucketName}")
    public Result<OssBucketDetailResponse> ossBucketDetail(@PathVariable String bucketName) {
        OssBucketDetailResponse stub = new OssBucketDetailResponse(bucketName);
        return Result.success(stub);
    }

    private List<Map<String, Object>> queryOssBuckets() {
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            DataSource ds = lookupDataSource();
            if (ds == null) return rows;
            try (java.sql.Connection conn = ds.getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, bucket_name, region, acl, status FROM z_oss_bucket LIMIT 100")) {
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("id", rs.getLong("id"));
                        row.put("bucketName", rs.getString("bucket_name"));
                        row.put("region", rs.getString("region"));
                        row.put("acl", rs.getString("acl"));
                        row.put("status", rs.getInt("status"));
                        rows.add(row);
                    }
                }
            }
        } catch (Exception e) {
            // ignore — fallback to empty
        }
        return rows;
    }

    /**
     * 从 Spring 容器里找一个 DataSource. 优先 ctc, 其次 default.
     * <p>避免硬编码 datasource bean 名, 跨模块更稳.</p>
     */
    private DataSource lookupDataSource() {
        if (dataSource != null) return dataSource;
        try {
            org.springframework.context.ApplicationContext ctx =
                    org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext();
            if (ctx == null) return null;
            try {
                return ctx.getBean("dataSourceCtc", DataSource.class);
            } catch (Exception ignore) {
            }
            try {
                return ctx.getBean("dataSource", DataSource.class);
            } catch (Exception ignore) {
            }
            try {
                return ctx.getBean("dataSourceDefault", DataSource.class);
            } catch (Exception ignore) {
            }
            String[] names = ctx.getBeanNamesForType(DataSource.class);
            if (names != null && names.length > 0) {
                return ctx.getBean(names[0], DataSource.class);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
