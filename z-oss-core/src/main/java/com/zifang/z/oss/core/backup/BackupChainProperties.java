package com.zifang.z.oss.core.backup;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 备份链配置，前缀 {@code oss.backup}。
 * <pre>
 * oss:
 *   backup:
 *     enabled: true            # 总开关；默认 false（不改变既有部署行为）
 *     bucket: z-graph-backup   # 备份归档落在哪个 bucket
 *     key-prefix: zgraph/      # 备份对象 key 前缀（滚动保留只在这个前缀下做）
 *     retention: 7             # 滚动保留份数 N；<=0 表示不删（只增不删）
 * </pre>
 *
 * <p><b>retention 的语义是"保留最新 N 份"</b>，不是"总共存 N 个文件"：
 * 归档第 N+1 份时，最老的那份会被删掉。</p>
 */
@ConfigurationProperties(prefix = "oss.backup")
public class BackupChainProperties {

    /** 总开关。默认关 —— 备份链不该在没配置时改变既有行为。 */
    private boolean enabled = false;

    /** 备份归档的 bucket 名。 */
    private String bucket = "z-graph-backup";

    /** 备份对象的 key 前缀；滚动保留只在这个前缀下计算。 */
    private String keyPrefix = "zgraph/";

    /** 滚动保留份数 N（保留最新 N 份）；<=0 表示不删。 */
    private int retention = 7;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public int getRetention() {
        return retention;
    }

    public void setRetention(int retention) {
        this.retention = retention;
    }
}
