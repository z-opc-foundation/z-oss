package com.zifang.z.oss.spring.provider;

import com.zifang.z.oss.core.provider.OssObjectSummary;
import com.zifang.z.oss.spring.template.OssTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 打真 MinIO 的集成测试，覆盖 S3OssProvider 的真实网络路径。
 *
 * <p>需要先起一个 MinIO：
 * <pre>
 * docker run -d --name zoss-minio -p 9000:9000 -p 9001:9001 \
 *   -e MINIO_ROOT_USER=zossadmin -e MINIO_ROOT_PASSWORD=zossadmin123456 bitnami/minio:latest
 * </pre>
 * 然后 {@code ZOSS_MINIO_ENDPOINT=http://localhost:9000 ZOSS_MINIO_AK=zossadmin
 * ZOSS_MINIO_SK=zossadmin123456 mvn verify}。
 * 未给这组环境变量时整类跳过，不让 CI 因缺外部依赖变红。</p>
 */
@EnabledIfEnvironmentVariable(named = "ZOSS_MINIO_ENDPOINT", matches = ".+")
class S3OssProviderMinioIT {

    private static final String BUCKET = "zoss-it";

    private OssTemplate oss;

    @BeforeEach
    void setUp() throws Exception {
        S3OssProperties props = new S3OssProperties();
        props.setEndpoint(env("ZOSS_MINIO_ENDPOINT"));
        props.setAccessKey(env("ZOSS_MINIO_AK"));
        props.setSecretKey(env("ZOSS_MINIO_SK"));
        props.setRegion("us-east-1");
        props.setPathStyleAccess(true);
        oss = new OssTemplate(new com.zifang.z.oss.core.provider.OssProviderManager(
                Arrays.asList(new S3OssProvider(props)), "s3"));

        if (!oss.bucketExists(BUCKET)) {
            oss.createBucket(BUCKET, "us-east-1", "private");
        }
        for (OssObjectSummary o : oss.list(BUCKET)) {
            oss.deleteObject(BUCKET, o.getKey());
        }
    }

    @Test
    void putGetRoundTripsThroughRealS3() throws Exception {
        byte[] payload = payload(64 * 1024);
        OssObjectSummary meta = oss.putObject(BUCKET, "hello.bin",
                new ByteArrayInputStream(payload), payload.length, "application/octet-stream");

        assertThat(meta.getSize()).isEqualTo(payload.length);
        assertThat(meta.getEtag()).isNotBlank();
        assertThat(oss.exists(BUCKET, "hello.bin")).isTrue();

        assertThat(readAll(oss.getObject(BUCKET, "hello.bin"))).isEqualTo(payload);

        OssObjectSummary stat = oss.stat(BUCKET, "hello.bin");
        assertThat(stat.getSize()).isEqualTo(payload.length);
        assertThat(stat.getContentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void rangeReadReturnsExactlyTheRequestedWindow() throws Exception {
        byte[] payload = payload(4096);
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }
        oss.putObject(BUCKET, "range.bin", new ByteArrayInputStream(payload), payload.length, null);

        byte[] window = readAll(oss.getObject(BUCKET, "range.bin", 100, 500));
        assertThat(window).hasSize(500);
        assertThat(window).isEqualTo(Arrays.copyOfRange(payload, 100, 600));

        byte[] tail = readAll(oss.getObject(BUCKET, "range.bin", payload.length - 16, 16));
        assertThat(tail).isEqualTo(Arrays.copyOfRange(payload, payload.length - 16, payload.length));
    }

    @Test
    void presignedUrlIsRetrievableByAnAnonymousClient() throws Exception {
        byte[] payload = "signed-payload".getBytes(StandardCharsets.UTF_8);
        oss.putObject(BUCKET, "presigned.txt", new ByteArrayInputStream(payload), payload.length, "text/plain");

        String url = oss.presignedUrl(BUCKET, "presigned.txt", 300);
        assertThat(url).contains("X-Amz-Signature").contains("X-Amz-Expires");

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        assertThat(readAll(conn.getInputStream())).isEqualTo(payload);
        conn.disconnect();
    }

    @Test
    void copyListAndBatchDeleteBehaveLikeTheLocalBackend() throws Exception {
        oss.putObject(BUCKET, "a/1.txt", in("one"), 3, "text/plain");
        oss.putObject(BUCKET, "b/2.txt", in("two"), 3, "text/plain");

        List<OssObjectSummary> listed = oss.list(BUCKET, "a/", 10);
        assertThat(listed).extracting(OssObjectSummary::getKey).containsExactly("a/1.txt");

        oss.copy(BUCKET, "a/1.txt", BUCKET, "a/1-copy.txt");
        assertThat(oss.exists(BUCKET, "a/1-copy.txt")).isTrue();
        assertThat(readAll(oss.getObject(BUCKET, "a/1-copy.txt"))).isEqualTo("one".getBytes(StandardCharsets.UTF_8));

        // 前缀 b/ 不能被 a/ 的匹配顺带命中，否则删除范围就不可控了
        assertThat(oss.list(BUCKET, "b/", 10)).extracting(OssObjectSummary::getKey).containsExactly("b/2.txt");

        assertThat(oss.deleteObjects(BUCKET, Arrays.asList("a/1.txt", "b/2.txt", "not-there.txt")))
                .containsExactlyInAnyOrder("a/1.txt", "b/2.txt");
        assertThat(oss.exists(BUCKET, "a/1.txt")).isFalse();
        assertThat(oss.exists(BUCKET, "not-there.txt")).isFalse();
        assertThat(oss.exists(BUCKET, "a/1-copy.txt")).isTrue();
    }

    @Test
    void multipartIsRejectedLoudlyOnS3Backend() {
        assertThatThrownBy(() -> oss.initiateMultipart(BUCKET, "big.bin", "application/octet-stream"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("分片");
    }

    private static String env(String key) {
        String v = System.getenv(key);
        if (v == null || v.isEmpty()) {
            throw new IllegalStateException(key + " 未设置");
        }
        return v;
    }

    private static byte[] payload(int size) {
        byte[] b = new byte[size];
        for (int i = 0; i < size; i++) {
            b[i] = (byte) (i * 31 + 7);
        }
        return b;
    }

    private static InputStream in(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }
}