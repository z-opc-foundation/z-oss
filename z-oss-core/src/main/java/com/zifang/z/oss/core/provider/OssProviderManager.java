package com.zifang.z.oss.core.provider;

import java.util.*;

/**
 * OssProvider 门面
 * <p>
 * 注册所有 {@link OssProvider} 实现，按 {@code oss.provider} 配置项暴露当前激活的 Provider。
 * 对业务层只暴露一个 {@link OssProvider} bean。
 */
public class OssProviderManager {

    private final Map<String, OssProvider> providers = new LinkedHashMap<>();
    private final String activeType;

    public OssProviderManager(List<OssProvider> all, String activeType) {
        if (all != null) {
            for (OssProvider p : all) {
                providers.put(p.getType(), p);
            }
        }
        this.activeType = activeType;
    }

    /**
     * 获取当前激活的 Provider（由 {@code oss.provider} 决定）
     */
    public OssProvider provider() {
        OssProvider p = providers.get(activeType);
        if (p == null) {
            throw new IllegalStateException(
                    "No OssProvider found for type='" + activeType + "'. " +
                            "Registered types: " + providers.keySet());
        }
        return p;
    }

    /**
     * 获取指定类型的 Provider
     */
    public OssProvider provider(String type) {
        OssProvider p = providers.get(type);
        if (p == null) {
            throw new IllegalStateException("No OssProvider found for type='" + type + "'");
        }
        return p;
    }

    /**
     * 已注册的 Provider 类型列表
     */
    public List<String> listProviderTypes() {
        return new ArrayList<>(providers.keySet());
    }

    /**
     * 当前激活的 Provider 类型
     */
    public String getActiveType() {
        return activeType;
    }

    public Map<String, OssProvider> getProviders() {
        return Collections.unmodifiableMap(providers);
    }
}
