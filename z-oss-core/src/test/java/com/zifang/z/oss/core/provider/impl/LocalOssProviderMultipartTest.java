package com.zifang.z.oss.core.provider.impl;

import com.zifang.z.oss.core.provider.OssObjectSummary;
import com.zifang.z.oss.core.provider.OssPartInfo;
import com.zifang.z.oss.core.provider.OssProvider;
import com.zifang.z.oss.core.storage.FileStorageEngine;
import com.zifang.z.oss.core.util.ZOssSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * z-oss 自持后端（local provider）的能力验证。
 *
 * <p>这组测试钉的是"z-oss 自己就是那个存储服务端"这条定位该有的能力 ——
 * 分片上传、范围读、签名 URL 收发一致，而不是转发给某个云厂商的薄壳。</p>
 */
class LocalOssProviderMultipartTest {

    private static final String SIGN_KEY = "unit-test-sign-key";

    private OssProvider oss;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("root");
        Files.createDirectories(root.resolve("bucket"));
        FileStorageEngine engine = new FileStorageEngine();
        engine.setRootPath(root.toString());
        oss = new LocalOssProvider(engine, SIGN_KEY, "https://oss.example.com");
    }

    private static InputStream in(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    // ==================== 基本往返 ====================

    @Test
    void putThenGetRoundTrip() throws IOException {
        oss.putObject("bucket", "a/b.txt", in("hello oss"), 9, "text/plain");

        assertTrue(oss.doesObjectExist("bucket", "a/b.txt"));
        try (InputStream got = oss.getObject("bucket", "a/b.txt")) {
            assertEquals("hello oss", read(got));
        }
    }

    // ==================== 范围读 ====================

    @Test
    void rangeReadReturnsRequestedWindow() throws IOException {
        oss.putObject("bucket", "r.txt", in("0123456789"), 10, "text/plain");

        try (InputStream got = oss.getObject("bucket", "r.txt", 3, 4)) {
            assertEquals("3456", read(got));
        }
        // length 负数 = 读到结尾
        try (InputStream got = oss.getObject("bucket", "r.txt", 7, -1)) {
            assertEquals("789", read(got));
        }
        // offset 超出长度不应抛异常，返回空
        try (InputStream got = oss.getObject("bucket", "r.txt", 100, 5)) {
            assertEquals("", read(got));
        }
    }

    // ==================== 分片上传 ====================

    @Test
    void multipartUploadAssemblesPartsInOrder() throws IOException {
        String uploadId = oss.initiateMultipartUpload("bucket", "big.bin", "application/octet-stream");
        assertNotNull(uploadId);

        List<OssPartInfo> parts = new ArrayList<>();
        // 故意乱序上传，验证归并是按 partNumber 排序而不是按到达顺序
        parts.add(oss.uploadPart("bucket", "big.bin", uploadId, 3, in("CCC"), 3, null));
        parts.add(oss.uploadPart("bucket", "big.bin", uploadId, 1, in("AAA"), 3, null));
        parts.add(oss.uploadPart("bucket", "big.bin", uploadId, 2, in("BBB"), 3, null));

        OssObjectSummary summary = oss.completeMultipartUpload("bucket", "big.bin", uploadId, parts);

        assertEquals("big.bin", summary.getKey());
        assertEquals(9L, summary.getSize().longValue());
        assertNotNull(summary.getEtag(), "归并后应给出整对象校验和");
        try (InputStream got = oss.getObject("bucket", "big.bin")) {
            assertEquals("AAABBBCCC", read(got));
        }
    }

    @Test
    void abortMultipartLeavesNoObject() {
        String uploadId = oss.initiateMultipartUpload("bucket", "dropped.bin", null);
        oss.abortMultipartUpload("bucket", "dropped.bin", uploadId);

        assertFalse(oss.doesObjectExist("bucket", "dropped.bin"),
                "放弃的分片不得留下半截对象");
    }

    @Test
    void partNumberMustStartAtOne() {
        String uploadId = oss.initiateMultipartUpload("bucket", "x.bin", null);
        try {
            oss.uploadPart("bucket", "x.bin", uploadId, 0, in("a"), 1, null);
            org.junit.jupiter.api.Assertions.fail("partNumber=0 必须被拒");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("partNumber"));
        } catch (IOException e) {
            org.junit.jupiter.api.Assertions.fail("不应是 IO 异常: " + e);
        }
    }

    @Test
    void multipartSessionDirRejectsEscapingUploadId(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("root2");
        Files.createDirectories(root.resolve("bucket"));
        FileStorageEngine engine = new FileStorageEngine();
        engine.setRootPath(root.toString());

        // uploadId 同样来自 HTTP，若不校验就能把分片写穿存储根
        try {
            engine.getMultipartPartSize("../../escape", 1);
            org.junit.jupiter.api.Assertions.fail("越界 uploadId 必须被拒");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("uploadId"));
        }
    }

    // ==================== 预签名 URL 收发一致 ====================

    @Test
    void presignedUrlCarriesVerifiableSignature() {
        String url = oss.generatePresignedUrl("bucket", "a b/c+d.txt", 300);
        assertTrue(url.startsWith("https://oss.example.com/api/v1/object/download"), url);
        assertTrue(url.contains("accessKeyId=" + SIGN_KEY), "URL 里应带 accessKeyId: " + url);
        // key 的 '/' 不能被编码掉，否则深层 key 的 URL 不可用
        assertTrue(url.contains("a%20b/c%2Bd.txt") || url.contains("a%20b/c+d.txt"),
                "key 的路径分隔符应保留: " + url);
    }

    @Test
    void signatureRoundTripsThroughVerify() {
        long expiresAt = System.currentTimeMillis() / 1000L + 300;
        String sig = ZOssSignature.sign(SIGN_KEY, "bucket", "a/b.txt", expiresAt);

        assertTrue(ZOssSignature.verify(SIGN_KEY, "bucket", "a/b.txt", expiresAt, sig));
    }

    @Test
    void signatureRejectsWrongKeyBucketAndKey() {
        long expiresAt = System.currentTimeMillis() / 1000L + 300;
        String sig = ZOssSignature.sign(SIGN_KEY, "bucket", "a/b.txt", expiresAt);

        assertFalse(ZOssSignature.verify("other-key", "bucket", "a/b.txt", expiresAt, sig),
                "换密钥必须验不过");
        assertFalse(ZOssSignature.verify(SIGN_KEY, "other-bucket", "a/b.txt", expiresAt, sig),
                "换桶必须验不过 —— 否则签名可被搬到别的桶上用");
        assertFalse(ZOssSignature.verify(SIGN_KEY, "bucket", "a/other.txt", expiresAt, sig),
                "换 key 必须验不过");
    }

    @Test
    void signatureRejectsExpiredAndMalformed() {
        long past = System.currentTimeMillis() / 1000L - 60;
        String sig = ZOssSignature.sign(SIGN_KEY, "bucket", "a/b.txt", past);
        assertFalse(ZOssSignature.verify(SIGN_KEY, "bucket", "a/b.txt", past, sig), "过期必须验不过");

        long now = System.currentTimeMillis() / 1000L + 300;
        String fresh = ZOssSignature.sign(SIGN_KEY, "bucket", "a/b.txt", now);
        assertFalse(ZOssSignature.verify(SIGN_KEY, "bucket", "a/b.txt", now, null), "缺签名必须验不过");
        assertFalse(ZOssSignature.verify(SIGN_KEY, "bucket", "a/b.txt", now, ""), "空签名必须验不过");
        assertFalse(ZOssSignature.verify(SIGN_KEY, "bucket", "a/b.txt", now, fresh.substring(1)),
                "被改一位的签名必须验不过");
    }

    // ==================== 批量 / 复制 / 桶 ====================

    @Test
    void batchDeleteReportsOnlyWhatWasDeleted() throws IOException {
        oss.putObject("bucket", "d1", in("1"), 1, null);
        oss.putObject("bucket", "d2", in("2"), 1, null);

        List<String> deleted = oss.batchDeleteObjects("bucket", Arrays.asList("d1", "never-existed"));

        assertEquals(java.util.Collections.singletonList("d1"), deleted);
        assertFalse(oss.doesObjectExist("bucket", "d1"));
        assertTrue(oss.doesObjectExist("bucket", "d2"));
    }

    @Test
    void copyObjectPreservesContent() throws IOException {
        oss.putObject("bucket", "src.txt", in("copy me"), 7, "text/plain");

        oss.copyObject("bucket", "src.txt", "bucket", "dst.txt");

        try (InputStream got = oss.getObject("bucket", "dst.txt")) {
            assertEquals("copy me", read(got));
        }
    }

    @Test
    void deleteBucketRefusesNonEmptyBucket() throws IOException {
        oss.putObject("bucket", "keep.txt", in("x"), 1, null);
        try {
            oss.deleteBucket("bucket");
            org.junit.jupiter.api.Assertions.fail("非空桶不得被删");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("not empty"), expected.getMessage());
        }
    }
}
