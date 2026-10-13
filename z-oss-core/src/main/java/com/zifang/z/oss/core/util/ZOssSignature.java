package com.zifang.z.oss.core.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 预签名 URL 的签名与校验。
 * <p>
 * z-oss 自持后端（local）没有云厂商的签名服务可借，就自己签，形态对齐 S3 预签名：
 * URL 里带 accessKeyId、过期时间戳和签名，校验时用同一把密钥重算并比对，
 * 同时强制检查过期。签发与校验必须是同一个类 —— 两边各写一份算法迟早会对不上。
 */
public final class ZOssSignature {

    private static final String ALGORITHM = "HmacSHA256";

    private ZOssSignature() {
    }

    /**
     * 对 bucket/key/过期时间戳签名
     */
    public static String sign(String accessKey, String bucket, String key, long expiresAt) {
        return sign(accessKey, payload(bucket, key, expiresAt));
    }

    /**
     * 校验预签名参数
     *
     * @return 过期返回 false；签名不匹配返回 false
     */
    public static boolean verify(String accessKey, String bucket, String key,
                                 long expiresAt, String signature) {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        if (System.currentTimeMillis() / 1000L > expiresAt) {
            return false;
        }
        return constantTimeEquals(sign(accessKey, bucket, key, expiresAt), signature);
    }

    private static String sign(String accessKey, String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(accessKey.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 不可用", e);
        }
    }

    private static String payload(String bucket, String key, long expiresAt) {
        return bucket + "\n" + key + "\n" + expiresAt;
    }

    /**
     * 恒定时间比较，避免按字节比对被计时侧信道逐位猜出签名
     */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}